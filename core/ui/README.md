# core:ui

Shared UI layer for every feature module: the composition root, access to the `AppGraph`,
formatters for times, dates and durations, pickers, permission helpers and a few common
composables. Everything here is built from `core:designsystem` components and tokens, so it works
in all 12 themes, light and dark, English and Thai.

## Root of every activity

```kotlin
setContent {
    AlarmClockAppRoot(appGraph()) { MyScreen() }
}
```

`AlarmClockAppRoot(graph, overrides = ThemeOverrides.None)` collects `graph.settings` and provides

| Local | Content |
|---|---|
| `LocalAppGraph` | the `AppGraph` (outside Compose: `context.appGraph()`) |
| `LocalAppSettings` | the current `AppSettings` |
| `LocalRegionSettings` | `settings.region` (language, 12/24 h, numerals, era, first day of week) |
| `LocalUiFormatters` | formatters for the current locale and region settings |

and applies `AlarmClockTheme` (appearance + accessibility), `ThemedSystemBars` and the theme's
window background. Nothing is composed until the first settings value arrives (no theme flash);
after `settingsTimeoutMillis` (5 s) the defaults are used. Settings survive configuration changes.

The ring screen uses `AlarmClockRingRoot(graph, alarm.appearance)`: the alarm's own theme, else
Settings › ring-screen theme, else the app theme (`ThemeOverrides.forRing`), with a 1 s wait.

ViewModels: `val vm = appViewModel { graph -> MyViewModel(graph.alarms, graph.engine) }`.

## Formatting

Never format times or dates by hand: the user chooses 12/24 h, Thai numerals (๐–๙) and the
Buddhist year.

```kotlin
val f = rememberUiFormatters()          // or UiFormatters.from(context, settings.region)
f.time.time(alarm.time)                 // "07:30" / "7:30 AM" / "7:30 ก่อนเที่ยง"
f.time.dateShort(date, today)           // "Wed, 25 Nov" / "พ. 25 พ.ย." (+ year if not this year)
f.time.dateLong(date)                   // "Wednesday, 25 November 2026" / "วันพุธที่ 25 พฤศจิกายน 2569"
f.time.dayAndTime(triggerAt, zone, now) // "Tomorrow at 7:00 AM" / "พรุ่งนี้ เวลา 07:00"
f.duration.until(millis)                // "in 7 h 24 min" / "อีก 7 ชม. 24 นาที" (rounded up)
f.duration.since(millis)                // "5 min ago" / "just now"
f.duration.countdown(millis)            // "05:00", "1:05:09"
f.duration.durationWithSeconds(ms)      // "1 min 30 s"; DurationStyle.LONG for TalkBack
f.repeat.summary(alarm.repeat, alarm.time, now) // "Weekdays", "Mon, Wed, Fri", "4 on / 2 off from 25 Nov", …
f.days.short(1) / narrow(1) / long(1)   // "Mon" / "M" / "Monday"; f.days.week = display order
```

## Time

- `rememberNow(intervalMillis)` — ticking epoch millis, aligned to the wall clock, paused when the
  screen is not visible, refreshed when the user changes time or time zone. `rememberZone()`.
- `CountdownText(targetAt, style = Relative | Duration | Digits)` — recomposes only when the shown
  value changes; TalkBack hears the long form.
- `LocalUiClock` — provide `FixedUiClock(millis, zone)` in previews and screenshot tests.

## Pickers (bottom sheets)

- `TimePickerSheet(initialTime, onConfirm, onDismiss, supportingText = { "Rings in …" })` —
  wheels or typed digits; `TimeWheelPicker` and `TimeKeyboardInput` can be used inline.
- `DurationPicker(durationMillis, onDurationChange)` — h/m/s wheels;
  `DurationKeypad(digits, onDigitsChange)` with `DurationDigits` — Samsung-style typed timer.
- `DatePickerSheet(initialDate, onConfirm, onDismiss, minDate, maxDate)` — Material 3 calendar in
  the theme, with Thai numerals, first day of week and the Buddhist year in the headline and month
  title.

Sheets call exactly one of `onConfirm` / `onDismiss`, after they have closed.

## Permissions

```kotlin
PermissionGate(listOf(Manifest.permission.CAMERA), title, message) { CameraPreview() }

val requester = rememberPermissionRequester(listOf(Manifest.permission.ACTIVITY_RECOGNITION)) { granted -> … }
PermissionPrompts(requester, rationaleTitle, rationaleMessage)   // rationale + "open settings" dialogs
AppButton(text, onClick = requester::request)
```

`NotificationPermissionCard()` shows itself only while notifications are off.

## Navigation

- Routes live in `navigation/Routes.kt`; `TopLevelRoutes.all` are the bottom-bar tabs.
- `navController.navigateToTopLevel(route)` switches tabs; `TopLevelActions(navController)` is the
  Settings gear every top-level screen shows in its `AppScaffold(actions = …)`.
- Onboarding ends by setting `settings.onboardingCompleted = true`; the app shell then makes
  `AlarmsRoute` the start destination.
- `MainIntents` builds intents that open the app on a screen (notifications, widgets, tiles,
  shortcuts): `MainIntents.openAlarm(context, id)`, `openRoute(context, MainIntents.ROUTE_TIMER)`,
  `newAlarm(context, hour, minute)`.

## Other composables

`NextAlarmBanner(next)`, `ConfirmDialog(…)` / `rememberConfirmState<T>()`, `LoadingState()`,
`ErrorState(error, onRetry)` (shows `LocalizedError` messages).

## Language

`AppLocales.setLanguage(context, AppLanguage.THAI)` switches the per-app language (activities are
recreated); `AppLocales.currentLanguage(context)` tells what the UI shows. English is the default.
