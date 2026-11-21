# Facing

A small, private Android app for a **daily photo series** – a selfie a day, two people together, a pet, a plant.
It overlays your previous photo on the camera preview so every picture lines up, and turns the whole
series into a time-lapse movie.

No account, no internet permission, no tracking: all photos stay on your device.

## Features

- Daily camera with a **ghost overlay** of your last photo, grid, front/back camera, self-timer and volume-key shutter
- **Groups**: separate series, e.g. "Me", "Together", or another person – switch any time
- **Streak**, calendar view and gallery with notes and favorites
- **Movie**: play the series, choose a time range, 2/5/10 fps, export as MP4 (with date stamp and looping music)
- **Compare** two days with a cross-fade slider
- **Import** photos from your gallery (date from EXIF), optional **copy of new photos to the gallery**
- **Backup / restore** as ZIP, optional weekly automatic backup to a folder you choose
- Home-screen widgets: photo ("1 year ago today"), streak, today status
- Daily reminder (only if you haven't taken a photo yet)
- **Themes**: light/dark following the system or fixed, Catppuccin, Nord, Gruvbox, Rosé Pine, Solarized, multiple custom themes of your own colours (create, rename, delete), optional pure black (AMOLED)
- Languages: English, Deutsch, Español, Français

## Build

```
./gradlew assembleDebug
```

Requires JDK 17 and the Android SDK (compileSdk 34). The APK ends up in `app/build/outputs/apk/debug/`.

## Code overview

Kotlin, Jetpack Compose, Room, DataStore, CameraX, WorkManager, Coil. No Google Play Services.

| Path (`app/src/main/java/app/facing/`) | What it does |
|---|---|
| `FacingApp.kt`, `MainActivity.kt` | Manual dependency wiring, entry point, volume-key shutter |
| `data/db/` | Room: `PhotoEntity`, `GroupEntity`, DAOs, migration 1→2 (groups) |
| `data/PhotoRepository.kt` | All photo queries, scoped to the active group; group management |
| `data/files/` | `PhotoStorage` (scaled JPEG + thumbnails), `GalleryExporter` (MediaStore copy) |
| `data/export/` | `MovieExporter` (MediaCodec → MP4), `AudioMixer` (music track, looping) |
| `data/backup/` | `BackupManager` (ZIP + `photos.json`), `AutoBackup` (WorkManager) |
| `data/prefs/` | Settings in DataStore |
| `reminder/`, `widget/` | Notification scheduling, home-screen widgets |
| `ui/theme/` | Colour palettes (`Palettes.kt`) and theme setup (`Theme.kt`) |
| `ui/` | Compose screens: camera, gallery, calendar, compare, movie, dialogs |

Strings live in `app/src/main/res/values*/strings.xml` (English is the default/fallback).

## License

Facing is free software, licensed under the **GNU General Public License v3.0 or later**
(`GPL-3.0-or-later`). See [LICENSE](LICENSE).
