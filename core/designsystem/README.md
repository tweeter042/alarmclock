# core:designsystem

Everything that decides how the app looks: 12 design languages (themes), their palettes, the
bundled fonts, the themed components and the clock faces. Feature modules build their UI **only**
from what is in here, so every screen automatically works in all 12 themes, light/dark, Thai and
English, large fonts, high contrast and reduce motion.

## Applying the theme

```kotlin
val settings by graph.settings.settings.collectAsStateWithLifecycle(AppSettings())
AlarmClockTheme(appearance = settings.appearance, accessibility = settings.accessibility) {
    ThemedSystemBars()   // once per activity: bar icons readable on light/dark themes
    // app content
}
```

| Parameter | Use |
|---|---|
| `appearance` | theme, palette per theme, dark mode (SYSTEM/LIGHT/DARK), body/clock fonts, font scale |
| `accessibility` | high contrast, large buttons, reduce motion, haptics |
| `forceDark` | force dark/light (previews, night clock) |
| `themeOverride` / `paletteOverride` | ring-screen theme (`appearance.ringThemeId`), gallery previews |

Dark-only themes (Terminal, Neon, AMOLED) are always dark. The Material You palette
`DYNAMIC_PALETTE_ID` ("dynamic") uses wallpaper colours. `AlarmClockTheme` also provides a matching
`MaterialTheme`, so stock Material 3 widgets fit, but prefer the `App*` components below.

## Reading tokens

```kotlin
AppTheme.colors.primary          // AppColors: palette roles + card, bar, flipCard, … + .material
AppTheme.typography.titleLarge   // M3 scale + largeTitle, barTitle, sectionLabel, button, clock
AppTheme.shapes.card             // card, button, chip, field, sheet, dialog, track, thumb, …
AppTheme.spacing.screen          // page margin; also card, gutter, listItem, xs…xxxl
AppTheme.motion.spec<Float>()    // durations/easing; Pixel is stepped; reduce motion -> snap
AppTheme.border / AppTheme.shadow / AppTheme.traits / AppTheme.clockFace / AppTheme.iconStyle
```

Never hard-code colours, shapes, elevations or font families. For a custom container use
`Modifier.themedContainer(shape, fill, border, shadow)`; for animations use `AppTheme.motion`.
Loops (pulsing, blinking) must check `AppTheme.motion.ambientEnabled`.

## Components

| Component | Notes |
|---|---|
| `AppScaffold(title, subtitle, navigationIcon, actions, largeTitle, bottomBar, floatingActionButton, snackbarHostState, animatedBackground) { padding -> }` | One UI large collapsing title (≈30 % of the screen) over `ThemedBackground`. Content must scroll; apply `padding`. |
| `AppTopBar`, `AppBackButton` | Compact bar for sub-screens / full-screen pickers. |
| `AppNavigationBar { AppNavigationItem(selected, onClick, icon, label, selectedIcon, badgeCount) }` | Handles the navigation-bar inset. |
| `AppCard(modifier, variant) { }`, `AppCard(onClick, …) { }` | Variants: `Default`, `Emphasis`, `Outlined`. Sets `LocalContentColor`. |
| `AppButton(text, onClick, variant, size, enabled, leadingIcon, trailingIcon)` | `Primary`, `Secondary`, `Tonal`, `Danger`, `Text`; sizes `Small`…`ExtraLarge` (ring screen). Large-buttons setting bumps one size. |
| `AppIconButton(icon, contentDescription, onClick, variant)`, `AppFab(icon, contentDescription, onClick, text, expanded)` | |
| `AppSwitch`, `AppCheckbox`, `AppRadioButton` | Pass `null` callbacks when the whole row toggles. |
| `AppSlider(value, onValueChange, valueRange, steps, label, valueLabel)` | Haptic tick per step. |
| `AppChip`, `AppFilterChip` | |
| `DayOfWeekSelector(selectedDays, onSelectedDaysChange, firstDayOfWeek)` | ISO days (1 = Monday); `firstDayOfWeek = 0` uses the locale. |
| `AppSegmentedButtons(options, selectedIndex, onSelect)` | |
| `AppListItem`, `LeadingIcon`, `SectionHeader`, `SettingsSwitchRow`, `SettingsValueRow`, `SettingsNavRow` | One UI rows: value in the accent colour under the title. |
| `AppTextField(value, onValueChange, label, placeholder, supportingText, errorText, …)` | Label above the field. |
| `AppDialog`, `AppConfirmDialog`, `AppAlertDialog`, `AppBottomSheet` | Pickers belong in bottom sheets. |
| `AppDivider`, `AppBadge`, `AppLinearProgress(progress or null)`, `ProgressRing(progress) { }` | |
| `EmptyState(icon, title, body, actionLabel, onAction)` | |
| `ThemedBackground(animated) { }` | Glass blobs, scanlines, paper grain, pixel grid, neon grid, … `animated = true` on the ring screen / bedside clock. |
| `AppSnackbarHost(hostState)` | Themed snackbars (AppScaffold uses it). |

Icons: `AppIcons.Alarm`, `AppIcons.Add`, … return the Filled/Rounded/Outlined/Sharp variant
matching the theme. Haptics: `val haptics = rememberAppHaptics()` (`click()`, `toggle(on)`,
`tick()`, `confirm()`, `reject()`) respects the user's haptics setting.

## Clocks

```kotlin
ClockDisplay(time = now, use24h = is24h, showSeconds = false, numerals = settings.region.numerals,
             size = ClockSize.Hero, faceOverride = null)
ClockText("05:00", size = ClockSize.Large)   // timers, stopwatch, countdowns
```

Faces: `PLAIN`, `SEVEN_SEGMENT` (ghost segments, blinking colon), `FLIP` (split-flap animation),
`ANALOG` (dial style per Analog palette), `PIXEL` (5×7 dot matrix), `TERMINAL` (blinking block
cursor), `NEON_GLOW` (layered glow + warm-up flicker), `SERIF` (editorial rules).
Font-based faces render Thai numerals (๐–๙); seven-segment and dot-matrix faces always draw Arabic
digits like the hardware they imitate. `ClockSize.Compact` is for list rows (analog shows a mini
dial + digits). Big sizes shrink to fit the width. TalkBack reads "Time 7:30 AM".

## Fonts

- 42 bundled families in `res/font/<id>_<weight>.ttf` (`tools/fonts/download_fonts.py`); licences
  in `assets/licenses/fonts/<id>.txt` for the licences screen.
- `FontFamilies.resolve(context, id, themeDefaultId)` handles catalog ids, `FontIds.SYSTEM`,
  `FontIds.THEME_DEFAULT` and `custom:<file>`. Latin-only fonts get a matching Thai partner
  (`FontFamilies.thaiPartners`) as per-glyph fallback.
- `CustomFonts.import(context, uri)` / `list` / `delete` manage user .ttf/.otf files in
  `filesDir/fonts`. Map `CustomFonts.ImportException` to `ds_font_import_*` strings.

## Appearance gallery

`ThemePreviewCard(themeId, paletteId, dark, selected, onClick)` renders a live miniature of the
theme; `PaletteSwatches(themeId, selectedPaletteId, dark, onSelect)` lists palettes.
`ThemeInfo.localizedName()`, `localizedDescription()`, `PaletteSpec.localizedName()` pick EN/TH.

## Adding or changing a palette

Palettes live in `core:model` `ThemeDefinitions.kt` (shared with Wear OS). Per-theme builders
derive outlines, ghosts and shadows. `PaletteContrastTest` enforces ≥ 4.5:1 for every text pair and
≥ 3:1 for clock digits; run `./gradlew :core:designsystem:testDebugUnitTest`.

## Screenshots

`ThemeScreenshotTest`, `ClockFaceScreenshotTest` and `GalleryScreenshotTest` write PNGs to
`build/screenshots-out/` (Roborazzi, Robolectric native graphics). They render with reduce motion
so infinite animations cannot stall the test clock; `AnimatedClockTest` covers the animated paths.
