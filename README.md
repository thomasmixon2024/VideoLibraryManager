# Video Library Manager

An Android app that scans your device for video files, stores metadata in a local Room database, and displays a clean Jetpack Compose UI. Includes a persistent **run/bug log** accessible from within the app.

[![Android CI](https://github.com/thomasmixon2024/VideoLibraryManager/actions/workflows/android.yml/badge.svg)](https://github.com/thomasmixon2024/VideoLibraryManager/actions/workflows/android.yml)

---

## Features

| Feature | Status |
|---------|--------|
| Scoped Storage (READ_MEDIA_VIDEO / READ_EXTERNAL_STORAGE) | ✅ |
| Room DB v3 with FTS5 full-text search | ✅ |
| DB migrations v1→v2→v3 | ✅ |
| Foreground scanner service with notification | ✅ |
| Quarantine tracking (`isCorrupt` flag) | ✅ |
| Jetpack Compose UI — Material3 | ✅ |
| **Persistent run/bug log** (BugLogger) | ✅ |
| In-app log viewer (Settings → View Run Log) | ✅ |
| Log file sharing via share sheet | ✅ |
| MediaStore scanner (full query) | ✅ |
| Native metadata extraction (MediaMetadataRetriever) | ✅ |
| Thumbnail generation | ✅ |
| ExoPlayer video playback | ✅ |
| Persistent settings (DataStore) | ✅ |
| Search UI (FTS connected) — prefix wildcard via `VideoRepository` | ✅ |
| Scan folder filtering (Custom Scan Paths via MediaStore Buckets) | ✅ |
| Category customization (Tag-based category updates preserved across scans) | ✅ |

---

## Tech Stack

| Layer | Library | Version |
|-------|---------|---------|
| UI | Jetpack Compose + Material3 | BOM 2024.10 |
| Navigation | Navigation Compose | 2.8.4 |
| ViewModel | Lifecycle ViewModel + StateFlow | 2.8.7 |
| Database | Room + FTS4 | 2.7.1 |
| Scanner | MediaStore API + MediaMetadataRetriever | — |
| Preferences | DataStore Preferences | 1.1.1 |
| Playback | Media3 ExoPlayer | 1.5.0 |
| Async | Kotlin Coroutines | 1.9.0 |
| Build | AGP | 8.7.0 |
| Kotlin | — | 2.0.0 |

---

## Requirements

- **Android Studio** Ladybug (2024.2) or newer
- **Android SDK** 35
- **Min SDK** Android 7.0 (API 24)
- **JDK** 17

---

## Quick Start

```bash
git clone https://github.com/your-org/VideoLibraryManager.git
cd VideoLibraryManager
./gradlew assembleDebug        # build APK
./gradlew test                 # run unit tests
./gradlew installDebug         # install on connected device/emulator
```

---

## Project Structure

```
app/src/main/java/com/example/videolibrarymanager/
├── VlmApplication.kt            # Application subclass — BugLogger init
├── MainActivity.kt              # Entry point, permission handling
├── data/
│   ├── VideoEntity.kt           # Room entity with indices
│   ├── VideoFtsEntity.kt        # FTS5 virtual table entity
│   ├── VideoDao.kt              # CRUD + FTS search queries
│   ├── VideoDatabase.kt         # DB singleton + migrations v1→v3
│   └── VideoRepository.kt       # Repository layer
├── scanner/
│   └── VideoScanner.kt          # MediaStore query
├── service/
│   └── VideoScannerService.kt   # Foreground service
├── ui/
│   ├── VideoViewModel.kt        # StateFlow ViewModel
│   ├── HomeScreen.kt            # Video list + rich cards
│   ├── SettingsScreen.kt        # Settings menu
│   ├── VideoPlayerScreen.kt     # ExoPlayer playback screen
│   ├── LogViewerScreen.kt       # In-app run/bug log viewer
│   ├── PermissionScreen.kt      # Permission request UI
│   └── MainNavigationShell.kt   # NavHost (home / search / settings / player)
└── util/
    ├── VideoMetadataHelper.kt   # Native video metadata & thumbnail extraction
    └── BugLogger.kt             # Persistent structured logger
```

---

## Run / Bug Log

Every operation is recorded to `<filesDir>/vlm_runlog.txt`. The log:

- Survives app restarts (appended across sessions)
- Auto-rotates to `vlm_runlog_prev.txt` when it exceeds 512 KB
- Is viewable in-app via **⚙️ → View Run Log**
- Can be shared as a plain-text file via the share button in the log viewer
- Is mirrored to Android Logcat (use `adb logcat` during development)

### Reading the log via ADB

```bash
# Live tail all VLM tags
adb logcat -s MainActivity VideoScannerService VideoScanner VideoViewModel VideoDatabase BugLogger

# Pull the persistent log file
adb shell run-as com.example.videolibrarymanager cat files/vlm_runlog.txt > vlm_runlog.txt
```

### Example log output

```
════════════════════════════════════════════════════════════════════════
  SESSION START  2026-06-06 14:30:01.123
════════════════════════════════════════════════════════════════════════
2026-06-06 14:30:01.124 [INFO ] [VlmApplication] VlmApplication.onCreate — process started
2026-06-06 14:30:01.125 [INFO ] [VlmApplication] Device API level: 34
2026-06-06 14:30:01.126 [INFO ] [MainActivity] onCreate — savedInstanceState=false
2026-06-06 14:30:01.130 [DEBUG] [VideoDatabase] DB onOpen — version=3
2026-06-06 14:30:01.140 [DEBUG] [MainActivity] hasStoragePermission(android.permission.READ_MEDIA_VIDEO) = true
2026-06-06 14:30:01.141 [INFO ] [MainActivity] Starting VideoScannerService (foreground)
2026-06-06 14:30:01.200 [INFO ] [VideoScannerService] onStartCommand — startId=1
2026-06-06 14:30:01.210 [INFO ] [VideoScannerService] Running MediaStore scan…
2026-06-06 14:30:01.350 [DEBUG] [VideoScanner] Cursor opened — columnCount=9
2026-06-06 14:30:01.400 [INFO ] [VideoScanner] Cursor exhausted — found=47 skipped=0
2026-06-06 14:30:01.401 [INFO ] [VideoScannerService] MediaStore returned 47 video(s)
2026-06-06 14:30:01.450 [INFO ] [VideoScannerService] Scan complete — inserted=47 errors=0 total_in_db=47 elapsed=240ms
```

---

## Database Schema (v3)

**videos** table:

| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER PK | Auto-generated |
| path | TEXT UNIQUE | Full file path |
| name | TEXT | Display name |
| category | TEXT | Folder/bucket |
| duration | INTEGER | Duration in ms |
| resolution | TEXT | e.g. "1920x1080" |
| size | INTEGER | File size in bytes |
| thumbnailPath | TEXT? | Added v2 |
| checksum | TEXT? | Added v2 |
| isCorrupt | INTEGER | Added v2 (0/1) |
| dateAdded | INTEGER | Epoch ms |
| errorMessage | TEXT? | Scan error detail |

**videos_fts** (v3): FTS5 virtual table synced via INSERT/UPDATE/DELETE triggers.

---

## License

MIT
