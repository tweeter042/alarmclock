# Alarm Clock — Architecture & Engineering Guide

This is the contract every contributor (human or agent) follows. Read it fully before changing code.
Product requirements: [`docs/SPEC.md`](SPEC.md).

## 1. Product context

- Personal app, **sideloaded** (no Play Store). Target device: **Samsung Galaxy S24 Ultra, Android 17 (API 37), One UI 9**.
  Companion **Wear OS app for Galaxy Watch5**.
- Languages: **English (default)** and **Thai**. Every user-visible string exists in both.
- Twelve themes (design languages), each with ≥4 palettes, light/dark; user-selectable fonts (Thai-capable).
- Reliability is the #1 requirement: an alarm that does not ring is a total failure.
  Must survive Samsung battery optimization / Device care, reboots (incl. Direct Boot), time-zone changes.

## 2. Toolchain (verified working in this repo)

| Item | Version / value |
|---|---|
| Gradle | 9.9.3 (wrapper) |
| AGP | 9.5.3 — **built-in Kotlin**: never apply `org.jetbrains.kotlin.android` |
| Kotlin | 2.4.21 (Compose compiler + serialization plugins same version) |
| KSP | 2.3.14 (Room only) |
| compileSdk | 37 minor 2 (`compileSdk { version = release(37) { minorApiLevel = 2 } }`) — set in root build |
| targetSdk | 36 (deliberate: Android 16 behaviour is well understood; runs on Android 17) |
| minSdk | 34 phone modules; 33 for `:core:model`, `:shared:wearprotocol`, `:wear` |
| Java | 17 bytecode, JDK 21 runtime |
| Compose BOM | 2026.11.01 (Compose 1.13, Material3 1.5, icons-extended 1.7.8) |

Shared Android config lives in the **root `build.gradle.kts`** (`subprojects { pluginManager.withPlugin(...) }`).
Module build files only add plugins, `namespace` and dependencies. Applying `alias(libs.plugins.kotlin.compose)`
enables Compose and adds the BOM + core Compose deps automatically.

Dependencies come from `gradle/libs.versions.toml`. If a module needs a new library, add it **inside that
module's marked block** in the `[libraries]` section (e.g. under `# [feature-missions]`) so parallel edits
merge cleanly. Prefer libraries already in the catalog.

### Build commands

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew :feature:alarms:compileDebugKotlin --console=plain -q   # fast compile check of one module
./gradlew :feature:alarms:testDebugUnitTest --console=plain -q    # unit/Robolectric tests of one module
./gradlew :app:assembleDebug :wear:assembleDebug --console=plain -q
./gradlew lintDebug                                                 # MissingTranslation is an error
```

- The machine has 4 CPUs and ~15 GB RAM shared by up to two builds: always pass `--max-workers=2`.
- Maven Central sometimes answers **HTTP 429** through the proxy: wait ~30 s and re-run; it is not a code error.
- The build cache is on (`org.gradle.caching=true`), so clones share compiled outputs via `~/.gradle`.

## 3. Modules

```
:app                      Application, MainActivity, NavHost, AppGraph wiring, hooks, changelog asset
:wear                     Wear OS app (Galaxy Watch5), same applicationId as :app
:core:model               Pure domain: models, settings, theme/font catalogs, service + repository
                          interfaces, AppGraph, schedule calculator. No Android UI, no I/O.
:core:data                Room (device-protected storage), DataStore settings, SecretStore, backup, changelog
:core:alarm               The alarm engine: AlarmManager scheduling, receivers, ring foreground service,
                          audio/vibration, notifications, reliability checker, guard service, watchdog
:core:designsystem        Theme system (12 themes), palettes, fonts (bundled TTF), components, clock faces
:core:ui                  Navigation routes, LocalAppGraph, shared composables (formatters, pickers,
                          permission helpers, scaffolds built on designsystem)
:shared:wearprotocol      Phone⇄watch Data Layer paths + JSON payloads
:feature:missions         Wake-up missions (math, shake, QR/barcode, photo, steps, typing, memory, S Pen
                          handwriting), asset registration, MissionsEditor/MissionRunner
:feature:alarms           Alarm list, editor, groups, sound picker, nap
:feature:ringing          RingActivity (ring UI, missions host, awake check, after-dismiss routine),
                          SunriseActivity
:feature:clock            World clock, timers, stopwatch, bedside/night clock
:feature:sleep            Bedtime reminder, wake-time calculator, sleep sounds, sleep stats, DND
:feature:integrations     Weather (Open-Meteo), holidays, calendar alarms, location alarms, TTS +
                          briefing, quotes, news
:feature:connected        SmartThings, Galaxy Watch bridge (phone side), Health Connect, music apps
:feature:widgets          Glance widgets, Quick Settings tiles, app shortcuts, AlarmClock intents,
                          automation (Routines) entry points
:feature:settings         All settings screens, onboarding, health check + Samsung setup wizard,
                          backup UI, about/changelog/what's new/licences
```

Dependency rules (acyclic, enforced by build files):

- `core:model` ← everything. `core:data` → model. `core:alarm` → model, data. `core:designsystem` → model.
- `core:ui` → model (api), designsystem (api). Every feature → `core:ui`.
- Allowed feature→feature edges: `alarms → missions, connected, integrations`; `ringing → missions`;
  `sleep → connected`.
  No other feature-to-feature dependencies. Cross-feature navigation uses routes from `core:ui`.
- `:app` depends on everything. `:wear` → model, wearprotocol.

Package root: `io.github.tweeter042.alarmclock`. Module namespace = root + module path
(e.g. `io.github.tweeter042.alarmclock.feature.alarms`).

## 4. Contracts (do not break)

### 4.1 `core:model`

- `alarm/` — `Alarm` and all nested configs, `RepeatRule`, `MissionConfig`, `ActiveRing`, `NextAlarm`,
  `PreAlarmEvent`, **`AlarmScheduleCalculator`** (the only place occurrence times are computed),
  `SnoozePolicy`, `BuiltInTones`.
- `entity/` — groups, holidays, calendar rules, location alarms, timers, stopwatch, world clocks, event log,
  routine items, mission assets, `ScheduledTrigger`.
- `settings/AppSettings` — one serializable document; add fields **with defaults** only.
- `theme/` — `ThemeId`, `PaletteColors` (ARGB longs, shared with Wear), `ThemeCatalog`. `font/FontCatalog`.
- `service/` — interfaces for weather, holidays, calendar, SmartThings, wear bridge, Health Connect, music,
  speech, briefing, quotes, news, backup, changelog, `BuildInfo`.
- `engine/` — `AlarmEngine`, `AlarmEngineHooks`, `RingUiLauncher`, `ReliabilityChecker` (+ health items).
- `repository/` — repository interfaces + engine-internal `RingStateStore`, `ScheduleLedger`.
- `AppGraph` — the manual DI graph. `util/ModelJson` — the one JSON config (ignoreUnknownKeys, discriminator "type").

Changing `core:model`: only additive, source-compatible changes (new optional fields/defaults, new interface
members **with default implementations**). Anything else must be raised in your final report instead.

### 4.2 Frozen cross-feature APIs (signatures fixed; implementations are yours)

- `feature/missions/MissionsApi.kt`: `MissionsEditor(...)`, `MissionRunner(...)`, `requiredPermissions(...)`.
- `feature/connected/ConnectedApi.kt`: `SmartThingsScenePicker`, `SmartThingsDevicePicker`, `MusicAppPicker`.
- `feature/ringing/RingScreens.kt`: `RingScreens.ringIntent/sunriseIntent` + extras.
- `feature/widgets/WidgetsApi.kt`: `WidgetsApi.refreshAll(context)`.
- Each feature's `fun NavGraphBuilder.<feature>Graph(navController: NavHostController)`.

### 4.3 Module guides (read the one you build on)

- `core/designsystem/README.md` — themes, tokens, components, clock faces, fonts, icons.
- `core/ui/README.md` — `AlarmClockAppRoot` / `AlarmClockRingRoot` (every activity's root),
  `UiFormatters` (time, durations, day names, repeat summaries in EN/TH with Thai numerals and
  Buddhist year), pickers (time/date/duration), permission helpers, `TopLevelActions`,
  `NextAlarmBanner`, `ErrorState` (shows `LocalizedError`), `appViewModel { graph -> … }`.
- `core/alarm/README.md` — how the ring screen, timers, watch bridge and app use the engine.

Opening the main activity from anywhere: `core.ui.navigation.MainIntents` —
`main(ctx)`, `openAlarm(ctx, id)`, `openRoute(ctx, MainIntents.ROUTE_*)` (`new_alarm`, `timer`,
`stopwatch`, `bedside`, `sleep`, `world_clock`, `settings`, `reliability`), `newAlarm(ctx, h, m)`.
Onboarding ends by setting `settings.onboardingCompleted = true` (the shell switches to the alarm
list). Top-level screens put `TopLevelActions(navController)` in `AppScaffold(actions = …)`; the
bottom bar belongs to the shell. Launcher entries are `<activity-alias>`es (one per
`AppIconVariant`); app shortcuts meta-data goes on every alias. Notification small icon for every
module: `@drawable/ds_ic_notification` (core:designsystem).

### 4.4 Dependency access

- In Compose: `val graph = LocalAppGraph.current`. Outside Compose: `context.appGraph()` (both from core:ui).
- ViewModels: `viewModel { MyViewModel(graph.alarms, graph.engine) }` (androidx `viewModel(initializer)`),
  constructor-inject only the members you need (keeps them testable with fakes).
- No DI framework, no global mutable singletons besides what `AppGraph` exposes.

## 5. Engineering conventions

### 5.1 UI

- Use **only `core:designsystem` components and tokens** (`AppTheme.colors`, `AppTheme.shapes`, `AppScaffold`,
  `AppCard`, `AppButton`, `ClockText`, …) so all 12 themes apply everywhere. No hard-coded colours, shapes,
  elevations or font families in feature code.
- One UI ergonomics on a 6.8" screen: large collapsing title header, primary actions in the lower half,
  bottom sheets for pickers, FAB bottom-right. Edge-to-edge with correct insets.
- Compose state: `collectAsStateWithLifecycle()`, `rememberSaveable` for form state, no blocking I/O on main.
- Accessibility: every icon-only control has `contentDescription`; touch targets ≥ 48 dp; supports font
  scale up to 200 %; TalkBack order sensible; respect `AccessibilitySettings` (large buttons, reduce motion).
- Time/date text goes through `core:ui` formatters, which honour 12/24 h, Thai numerals and Buddhist year.

### 5.2 Strings & languages

- No user-visible literal strings in Kotlin. Every string goes in the module's `res/values/strings.xml`
  (English) **and** `res/values-th/strings.xml` (natural, idiomatic Thai — not word-by-word translation).
- Resource names are prefixed per module to avoid collisions in the merged app:
  `model_`, `data_`, `engine_`, `ds_`, `ui_`, `mission_`, `alarms_`, `ring_`, `clock_`, `sleep_`,
  `integ_`, `conn_`, `widget_`, `settings_`, `app_`, `wear_`.
- Use `<plurals>` for counts (Thai uses `other` only). Use positional args (`%1$s`).
- `MissingTranslation` is a lint error.

### 5.3 Reliability rules (ring path)

- Exact alarms via `AlarmManager.setAlarmClock()` for rings, snoozes, naps and timers; pre-alarm triggers via
  `setExactAndAllowWhileIdle`. Permission `USE_EXACT_ALARM` (sideloaded; auto-granted).
- Ring audio uses `AudioAttributes.USAGE_ALARM` (bypasses silent/DND). Always have a fallback tone; if all
  audio fails, vibrate + max-volume `ToneGenerator`.
- Everything the ring path reads lives in **device-protected storage** and every ring-path component is
  `android:directBootAware="true"` (alarms work after a reboot before unlock). WorkManager, SecretStore and
  network are **not** available before unlock — never required for ringing.
- Every scheduled trigger is recorded in `ScheduleLedger`; the engine self-verifies and repairs (watchdog,
  app start, boot, time change, package replaced).
- Never let an exception in an integration (weather, SmartThings, TTS, watch) stop or delay a ring:
  wrap hooks with timeouts and catch-all logging.
- Every component that touches `DataGraph` data (activities, receivers, services, workers, tiles,
  widgets) runs in the app's default process — never set `android:process`: Room/DataStore change
  notifications are per process.
- Errors shown to users implement `core.model.util.LocalizedError` (string resource id), e.g. backup
  failures, so any UI module can display them in English or Thai.

### 5.4 Kotlin & architecture

- Coroutines + Flow everywhere; `Dispatchers.IO` for disk/network; structured concurrency (no `GlobalScope`).
- Pure logic goes into plain classes/functions with JVM unit tests. Android framework code is thin.
- Errors: return `Result`/sealed types from services; never crash the UI for a failed integration.
- Logging: `android.util.Log` with tag `AlarmClock/<Area>`; no personal data in logs.
- Security: exported components only where needed (with explicit intent filters); secrets only in
  `SecretStore`; backups exclude secrets.

### 5.5 Testing

- **JVM unit tests** for every piece of logic (schedule, snooze, missions generation/validation, parsers,
  mappers, formatters, view-model state transitions with fakes).
- **Robolectric** (`@RunWith(AndroidJUnit4::class)`, `@Config(sdk = [35])`) for framework-dependent code
  (Room DAOs, AlarmManager scheduling via `ShadowAlarmManager`, notifications, receivers).
- **Roborazzi** screenshots for key composables: `@GraphicsMode(GraphicsMode.Mode.NATIVE)` +
  `captureRoboImage("build/screenshots-out/<name>.png")`, run with
  `-Droborazzi.test.record=true` (or set in the test). Look at the PNGs you produce (they are images you
  can open) to check layout in several themes, light/dark, and with Thai text.
- A module is "done" only when `:module:testDebugUnitTest` passes and `:app:assembleDebug` compiles.

### 5.6 Git

- Small, focused commits with a clear message. Never commit `local.properties`, keystores or secrets.
- Do not put AI model names/identifiers in code, comments, resources or commit messages (the About screen
  reads the model name from untracked `local.properties`).
- End every commit message with these trailer lines exactly:

```
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01VfVBtDkmR1M8gCAmrw5C9E
```

## 6. Engine design (summary; details in core:alarm KDoc)

```
AlarmManager ──► AlarmTriggerReceiver ──► AlarmRingService (FGS, directBootAware)
   ▲                                          │  audio (USAGE_ALARM, fade-in, fallback), vibration,
   │ setAlarmClock / setExactAndAllowWhileIdle │  wake lock, full-screen notification → RingActivity,
   │                                          │  flip/power/volume/media-button actions, auto-stop,
Scheduler ◄── repositories (Flow) ◄── edits   │  TTS (DURING_RING), hooks (watch, SmartThings, widgets)
   │                                          ▼
ScheduleLedger ◄──── IntegrityWatchdog ◄─ ActiveRing state machine (RingStateStore, persisted)
BootReceiver (LOCKED_BOOT_COMPLETED, BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_SET, TIMEZONE_CHANGED)
```

- State machine: `RINGING ⇄ MISSION → (dismiss) → AWAKE_CHECK? → FINISHED`, `RINGING → SNOOZED → RINGING`.
- Multiple rings at once are queued (`RingStateStore.enqueue`), never dropped.
- WATCH_ONLY alarms: the phone ring starts silent for 60 s; unless the watch reports snooze/dismiss in that
  window the phone rings normally (the user is never left without an alarm).
- Missed alarms (device off, process killed) are detected from the ledger + event log; within
  `rebootGraceMinutes` they ring immediately after boot, otherwise a "missed alarm" notification is shown.

## 7. Samsung (One UI) specifics

- Health check items (`HealthItemId`) cover Android permissions plus Samsung settings that cannot be read
  programmatically (Never sleeping apps, Put unused apps to sleep, Recents lock). Those are confirmed by the
  user after we deep-link to the right screen (`ReliabilityChecker.fixIntents` returns ordered candidates,
  Samsung components first, Android fallbacks last).
- Install requires temporarily disabling **Auto Blocker** (Settings › Security and privacy › Auto Blocker).
- S Pen: Air actions media-key presses are handled via a `MediaSession` while ringing
  (`DismissConfig.mediaButton`); the handwriting mission uses ML Kit Digital Ink with stylus detection.
