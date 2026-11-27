# Alarm Clock

A full-featured Android alarm clock for Samsung Galaxy phones (built for the Galaxy S24 Ultra on
Android 17 / One UI 9) with a companion Galaxy Watch (Wear OS) app.

- 12 design themes (Minimal, Brutal, Retro LCD, Flip Clock, Glass, Material You, Terminal, Neon,
  Paper, Pixel, Analog, AMOLED Black), each with several colour palettes, light and dark
- English and Thai, selectable fonts
- Wake-up missions (math, shake, QR/barcode, photo, steps, typing, memory, S Pen handwriting)
- Reliability system against Samsung battery optimization / Device care
- Galaxy Watch app, SmartThings sunrise lights, Health Connect sleep stats, widgets and tiles

See [`docs/SPEC.md`](docs/SPEC.md) for the full feature list and
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for how the code is organised.

## Build

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleRelease :wear:assembleRelease
```
