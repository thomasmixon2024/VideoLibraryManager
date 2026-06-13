# Video Library Manager - Deep Repository Analysis

## Executive Summary

**Video Library Manager** is an Android Jetpack Compose application that scans device storage for video files, stores metadata in a local Room database with FTS5 full-text search, and displays a Material3 UI. The app includes a persistent **run/bug log** system accessible within the app and comprehensive error tracking.

---

## Project Metadata

| Property | Value |
|----------|-------|
| **Target Framework** | Android Jetpack Compose + Material3 |
| **Min SDK** | API 24 (Android 7.0) |
| **Target SDK** | API 35 |
| **Kotlin Version** | 2.0.0 |
| **AGP (Android Gradle Plugin)** | 8.7.0 |
| **JDK Compliance** | 17 |
| **Namespace** | `com.example.videolibrarymanager` |
| **Version** | 1.0 (versionCode=1) |
| **Database** | Room v2.6.1 with FTS5 (v3 schema) |

---

## Architecture Overview

### Layered Architecture

```
┌─────────────────────────────────────────────┐
│         UI Layer (Jetpack Compose)          │
│  MainNavigationShell → Bottom Nav           │
│  HomeScreen, SearchScreen, SettingsScreen  │
└────────────────────┬────────────────────────┘
                     │
┌────────────────────v────────────────────────┐
│     ViewModel Layer (StateFlow)              │
│  VideoViewModel ← Repository                │
│  State: videos, isLoading, error, count     │
└────────────────────┬────────────────────────┘
                     │
┌────────────────────v────────────────────────┐
│     Repository Pattern (Data Access)        │
│  VideoRepository ← VideoDao                 │
│  Methods: getAllVideos, searchVideos, etc.  │
└────────────────────┬────────────────────────┘
                     │
┌────────────────────v────────────────────────┐
│         Database Layer (Room + FTS5)        │
│  VideoDatabase (singleton)                  │
│  Entities: VideoEntity, VideoFtsEntity      │
│  Migrations: v1→v2→v3 (schema evolution)    │
└────────────────────┬────────────────────────┘
                     │
┌────────────────────v────────────────────────┐
│      SQLite Database + FTS5 Virtual Table   │
│  videos table + videos_fts (full-text)      │
└─────────────────────────────────────────────┘
```

### Service Architecture

```
┌──────────────────┐
│   MainActivity   │ ← Permission handling, UI entry point
└────────┬─────────┘
         │ starts (if permission granted)
         ▼
┌─────────────────────────────┐
│  VideoScannerService        │ ← Foreground service
│  (runs in background)       │   with notification
└────────┬────────────────────┘
         │ delegates to
         ▼
┌─────────────────────────────┐
│  VideoScanner               │ ← MediaStore query abstraction
│  (queries device storage)   │
└────────┬────────────────────┘
         │ returns
         ▼
┌─────────────────────────────┐
│  VideoEntity list           │ ← Inserted into database
└─────────────────────────────┘
```

---

## File Organization & Components

### 📁 `data/` - Data Layer

#### **VideoEntity.kt**
```kotlin
@Entity(tableName = "videos", indices = [
    Index(value = ["path"], unique = true),
    Index(value = ["category", "dateAdded"]),
    Index(value = ["isCorrupt"]),
    Index(value = ["name"])
])
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val name: String,
    val category: String = "Uncategorized",
    val duration: Long = 0,
    val resolution: String = "",
    val size: Long = 0,
    val thumbnailPath: String? = null,
    val checksum: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val isCorrupt: Boolean = false,
    val errorMessage: String? = null
)
```

**Purpose**: Room entity mapping to `videos` table  
**Indices**:
- `path` (UNIQUE) - Fast lookups, prevent duplicates
- `category + dateAdded` - Sort by folder/bucket and date
- `isCorrupt` - Filter quarantined videos
- `name` - Search by display name

---

#### **VideoFtsEntity.kt**
```kotlin
@Entity(tableName = "videos_fts")
@Fts5(contentEntity = VideoEntity::class)
data class VideoFtsEntity(
    val name: String,
    val category: String,
    val path: String
)
```

**Purpose**: FTS5 virtual table for full-text search  
**Features**:
- Content-backed FTS (synced via triggers to `videos`)
- Indexed fields: `name`, `category`, `path`
- Supports BM25 ranking algorithm

---

#### **VideoDatabase.kt**
- **Singleton pattern** with `@Volatile` double-checked locking
- **Version**: 3 (schema evolution v1→v2→v3)
- **Migrations**:
  - **v1→v2**: Added columns `isCorrupt`, `thumbnailPath`, `checksum`
  - **v2→v3**: Created FTS5 virtual table + INSERT/UPDATE/DELETE triggers
- **Callbacks**: `onCreate`, `onOpen`, `onDestructiveMigration` with BugLogger integration
- **Export Schema**: Enabled (schemas/ directory)

---

#### **VideoDao.kt**
```kotlin
@Dao
interface VideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: VideoEntity)

    @Query("SELECT * FROM videos ORDER BY dateAdded DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE isCorrupt = 1")
    fun getQuarantinedVideos(): Flow<List<VideoEntity>>

    @Query("SELECT COUNT(*) FROM videos")
    fun getVideoCount(): Flow<Int>

    @Transaction
    @Query("""
        SELECT v.* FROM videos v 
        JOIN videos_fts fts ON v.id = fts.docid 
        WHERE videos_fts MATCH :query 
        ORDER BY bm25(videos_fts) DESC 
        LIMIT 50
    """)
    fun searchVideos(query: String): Flow<List<VideoEntity>>

    @Query("DELETE FROM videos WHERE path = :path")
    suspend fun deleteByPath(path: String)
}
```

**Key Methods**:
- **getAllVideos()** - Returns Flow for reactive updates
- **searchVideos()** - FTS5 MATCH with BM25 ranking, 50 result limit
- **getQuarantinedVideos()** - Filter corrupt videos
- **insert()** - REPLACE strategy (upsert on path)

---

#### **VideoRepository.kt**
```kotlin
class VideoRepository(private val videoDao: VideoDao) {
    fun getAllVideos(): Flow<List<VideoEntity>> = videoDao.getAllVideos()
    fun getQuarantinedVideos(): Flow<List<VideoEntity>> = videoDao.getQuarantinedVideos()
    fun getVideoCount() = videoDao.getVideoCount()
    suspend fun insert(video: VideoEntity) = videoDao.insert(video)
    fun searchVideos(query: String): Flow<List<VideoEntity>> = videoDao.searchVideos(query)
    suspend fun deleteByPath(path: String) = videoDao.deleteByPath(path)
}
```

**Pattern**: Repository abstraction over DAO (manual injection, no Hilt/Dagger)

---

### 📁 `scanner/` - Media Scanning

#### **VideoScanner.kt**
```kotlin
fun scanAll(): List<VideoEntity>
```

**Functionality**:
1. Queries `MediaStore.Video.Media.EXTERNAL_CONTENT_URI`
2. Extracts projection: `_ID, DISPLAY_NAME, DATA, DURATION, SIZE, DATE_ADDED, BUCKET_DISPLAY_NAME, HEIGHT, WIDTH`
3. Sorts by `DATE_ADDED DESC`
4. Handles null paths, calculates resolution from width/height
5. Converts MediaStore timestamps (seconds) to ms: `cursor.getLong(idxDate) * 1000L`
6. Returns `List<VideoEntity>` or empty list on exception

**Error Handling**:
- Catches `SecurityException` (permission revoked)
- Logs skipped rows with null DATA
- Returns partial results if error occurs mid-scan

---

### 📁 `service/` - Background Processing

#### **VideoScannerService.kt**
- **Type**: Foreground service with notification
- **Lifecycle**: `onStartCommand` → create notification channel → launch scan coroutine
- **Return Value**: `START_NOT_STICKY` (no restart on termination)
- **Notification**: Android O+ with channel `CHANNEL_ID = "VlmScanner"`
- **Coroutine Scope**: `SupervisorJob() + Dispatchers.IO`

**Scan Flow**:
1. Query all videos via `VideoScanner.scanAll()`
2. Insert each into Room DAO
3. Track inserted count, errors
4. Calculate elapsed time
5. Update UI state (VideoViewModel observes DB changes)
6. Log comprehensive metrics

---

### 📁 `ui/` - UI Layer (Jetpack Compose)

#### **MainNavigationShell.kt**
```kotlin
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Catalog", Icons.Default.Home)
    object Search : Screen("search", "Search", Icons.Default.Search)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}
```

- Bottom navigation bar with 3 screens
- NavController manages routing
- Current route tracked in `mutableStateOf`

---

#### **VideoViewModel.kt**
```kotlin
class VideoViewModel(private val repository: VideoRepository) : ViewModel() {
    private val _videos    = MutableStateFlow<List<VideoEntity>>(emptyList())
    val videos: StateFlow<List<VideoEntity>> = _videos.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error     = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _videoCount = MutableStateFlow(0)
    val videoCount: StateFlow<Int> = _videoCount.asStateFlow()
}
```

**State Management**:
- `videos` - Observed from repository, emitted via Flow.collect
- `isLoading` - Set during loadVideos()
- `error` - Caught exceptions
- `videoCount` - DB count subscription

**Methods**:
- `loadVideos()` - Subscribes to repository flow
- `observeCount()` - Monitors video count in separate job
- `searchVideos(query)` - Delegates to repository
- `retry()` - Retriggers loadVideos()

---

#### **HomeScreen.kt**
```kotlin
@Composable
fun HomeScreen(
    viewModel: VideoViewModel,
    onNavigateToSettings: () -> Unit
)
```

**Components**:
- **TopAppBar** - Title with settings icon button
- **VideoList** - LazyColumn of VideoCard items
- **VideoCard** - Displays: name, category, resolution, duration, size, corrupt status
- **EmptyState** - "No videos found" placeholder
- **ErrorState** - Error message + Retry button
- **LoadingState** - Circular progress indicator

**State Collection**:
```kotlin
val videos     by viewModel.videos.collectAsStateWithLifecycle()
val isLoading  by viewModel.isLoading.collectAsStateWithLifecycle()
val error      by viewModel.error.collectAsStateWithLifecycle()
val videoCount by viewModel.videoCount.collectAsStateWithLifecycle()
```

---

#### **VideoSearchScreen.kt**
```kotlin
@Composable
fun VideoSearchScreen(
    viewModel: VideoViewModel,
    onVideoClick: (VideoEntity) -> Unit,
    modifier: Modifier = Modifier
)
```

**Features**:
- OutlinedTextField for query input
- Real-time search via `remember(searchQuery) { viewModel.searchVideos(searchQuery) }`
- LazyColumn with search results
- Clear button (X icon) to reset query

---

#### **SettingsScreen.kt**
```kotlin
@Composable
fun SettingsScreen(
    onClearDatabaseClick: () -> Unit,
    modifier: Modifier = Modifier
)
```

**Preferences**:
- **Auto-scan enabled** - Toggle switch
- **Skip corrupt videos** - Toggle switch
- **Scan limit slider** - Range 0-1000 (default 500)
- **Delete confirmation dialog** - Before clearing DB

---

#### **LogViewerScreen.kt**
```kotlin
@Composable
fun LogViewerScreen(onBack: () -> Unit)
```

**Features**:
- Displays `BugLogger.readLog()` in monospace font
- Vertical + horizontal scroll for long lines
- **Share button** - Sends log via FileProvider & share sheet
- **Clear button** - Erases log, writes new session header
- **Refresh FAB** - Manual reload (workaround for lack of SwipeRefresh dependency)

---

#### **PermissionScreen.kt**
```kotlin
@Composable
fun PermissionScreen(onGrantClicked: () -> Unit)
```

**UI**: Centered column with title, rationale, and "Grant Storage Permission" button

---

#### **Navigation.kt**
```kotlin
@Composable
fun AppNavigation(viewModel: VideoViewModel)
```

- NavHost with 3 routes: `home`, `settings`, `log_viewer`
- Each route composes appropriate screen
- Back navigation via `nav.popBackStack()`

---

### 📁 `util/` - Utilities

#### **BugLogger.kt**
```kotlin
object BugLogger {
    fun init(context: Context)
    fun info(tag: String, msg: String)
    fun warn(tag: String, msg: String)
    fun error(tag: String, msg: String, ex: Exception? = null)
    fun debug(tag: String, msg: String)
    fun readLog(): String
    fun logFile(): File
}
```

**Architecture**:
- **Singleton pattern** with `@Volatile` double-checked locking
- **Thread-safe** append operations with synchronized blocks
- **File location**: `<filesDir>/vlm_runlog.txt`

**Features**:
1. **Persistent logging** - Survives app restarts
2. **Auto-rotation** - Moves to `vlm_runlog_prev.txt` at 512 KB threshold
3. **Session headers** - Separator lines with session start time
4. **Timestamp formatting** - `yyyy-MM-dd HH:mm:ss.SSS` (UTC)
5. **Dual output** - File + Android Logcat via `Log.*` methods
6. **Level indicators** - `[INFO ]`, `[WARN ]`, `[ERROR]`, `[DEBUG]`

**Log Format**:
```
════════════════════════════════════════════════════════════════════════
  SESSION START  2026-06-06 14:30:01.123
════════════════════════════════════════════════════════════════════════
2026-06-06 14:30:01.124 [INFO ] [VlmApplication] VlmApplication.onCreate — process started
2026-06-06 14:30:01.140 [DEBUG] [VideoDatabase] DB onOpen — version=3
```

---

### 🧪 Test Files

#### **BugLoggerTest.kt**
```kotlin
class BugLoggerTest {
    @Before
    fun setUp() {
        resetBugLogger()
        logFile = tmpFolder.newFile("vlm_runlog.txt")
        // Inject via reflection to bypass Context
        val fileField = BugLogger::class.java.getDeclaredField("logFile")
        fileField.isAccessible = true
        fileField.set(BugLogger, logFile)
    }

    @Test
    fun `info writes INFO level entry to file`() { ... }
}
```

**Approach**: Reflection-based singleton reset (TemporaryFolder) for pure JVM tests

---

#### **VideoEntityTest.kt**
```kotlin
@Test
fun `default values are sensible`() {
    val e = VideoEntity(path = "/sdcard/test.mp4", name = "test.mp4")
    assertEquals(0L, e.id)
    assertEquals("Uncategorized", e.category)
    assertTrue(e.dateAdded > 0)
}

@Test
fun `copy produces independent entity`() {
    val original = VideoEntity(path = "/a.mp4", name = "a.mp4")
    val copy     = original.copy(name = "b.mp4", isCorrupt = true)
    assertEquals("a.mp4", original.name)
    assertEquals("b.mp4", copy.name)
}
```

**Coverage**: Data class defaults, copy semantics, field values

---

## Database Schema

### **videos** table (v3)

| Column | Type | Constraints | Purpose |
|--------|------|-------------|---------|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Unique identifier |
| path | TEXT | UNIQUE, NOT NULL | Full file path (upsert key) |
| name | TEXT | NOT NULL | Display name |
| category | TEXT | Indexed | Folder/bucket (e.g., "DCIM") |
| duration | INTEGER | | Duration in milliseconds |
| resolution | TEXT | | Format: "1920x1080" |
| size | INTEGER | | File size in bytes |
| dateAdded | INTEGER | Indexed with category | Epoch milliseconds |
| thumbnailPath | TEXT | (Added v2) | Future use |
| checksum | TEXT | (Added v2) | SHA-256 for corruption detection |
| isCorrupt | INTEGER | Indexed (Added v2) | 0/1 flag for quarantine |
| errorMessage | TEXT | | Scan error detail |

**Indices** (Performance):
```
UNIQUE INDEX videos_path ON videos(path)
INDEX videos_category_dateAdded ON videos(category, dateAdded)
INDEX videos_isCorrupt ON videos(isCorrupt)
INDEX videos_name ON videos(name)
```

---

### **videos_fts** table (v3 - FTS5 Virtual)

| Column | Type | Purpose |
|--------|------|---------|
| docid | INTEGER (implicit) | Maps to `videos.id` |
| name | TEXT | Indexed for search |
| category | TEXT | Indexed for search |
| path | TEXT | Indexed for search |

**Triggers** (Keep FTS synced):
```sql
CREATE TRIGGER videos_ai AFTER INSERT ON videos BEGIN
  INSERT INTO videos_fts(docid, name, category, path)
  VALUES (new.id, new.name, new.category, new.path);
END;

CREATE TRIGGER videos_ad AFTER DELETE ON videos BEGIN
  DELETE FROM videos_fts WHERE docid = old.id;
END;

CREATE TRIGGER videos_au AFTER UPDATE ON videos BEGIN
  UPDATE videos_fts SET name=new.name, category=new.category, path=new.path
  WHERE docid = new.id;
END;
```

---

## Permissions & Manifest

### Required Permissions
```xml
<!-- Scoped storage -->
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />  <!-- API 33+ -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" maxSdkVersion="28" />

<!-- Foreground service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

### Manifest Components

**Activities**:
- `MainActivity` - MAIN entry point, theme applied, permission handling

**Services**:
- `VideoScannerService` - Foreground dataSync service

**Providers**:
- `androidx.core.content.FileProvider` - Share run log via Intent.ACTION_SEND

**Other**:
- `android:name=".VlmApplication"` - Custom Application subclass
- `android:fullBackupContent="@xml/backup_rules"`
- `android:dataExtractionRules="@xml/data_extraction_rules"`

---

## Build Configuration

### **build.gradle.kts (root)**
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.compose.compiler) apply false
}
```

### **app/build.gradle.kts**
```kotlin
android {
    compileSdk = 35
    defaultConfig {
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
                arguments["room.incremental"] = "true"
            }
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
```

### **Dependencies** (via Version Catalog)
| Library | Version | Purpose |
|---------|---------|---------|
| androidx-core-ktx | 1.15.0 | Core KTX extensions |
| androidx-lifecycle-runtime-ktx | 2.8.7 | Lifecycle, ViewModel |
| androidx-compose-bom | 2024.10 | Compose dependencies |
| androidx-material3 | Latest | Material Design 3 |
| androidx-navigation-compose | 2.8.4 | Navigation |
| androidx-room-runtime | 2.6.1 | Room database |
| androidx-room-compiler | 2.6.1 | Room code generation |
| kotlinx-coroutines-android | 1.9.0 | Async/coroutines |
| ffmpeg-kit-full | 6.0-2 | FFmpeg integration (TODO) |

---

## Key Design Patterns

### 1. **Singleton Pattern**
- **VideoDatabase** - Double-checked locking with `@Volatile`
- **BugLogger** - Synchronized blocks for thread safety

### 2. **Repository Pattern**
- **VideoRepository** - Wraps VideoDao, no dependency injection framework
- Pass DAO manually: `VideoRepository(videoDao)`

### 3. **ViewModel + StateFlow**
- **VideoViewModel** - Reactive state management
- Collects repository flows, exposes as StateFlow
- `collectAsStateWithLifecycle()` in Composables

### 4. **Sealed Classes**
- **Screen** - Navigation routes with typed data
- Exhaustive when expressions

### 5. **Room Migrations**
- Versioned schema: v1 → v2 → v3
- Backwards-compatible schema evolution
- FTS5 triggers for search index sync

### 6. **Flow-based Reactivity**
- All queries return `Flow<T>` for live updates
- UI subscribes via `collectAsStateWithLifecycle()`
- Automatic recomposition on data changes

---

## Known Issues & TODOs

### 🔜 Incomplete Features

1. **FFmpeg Kit Integration**
   - Dependency added but not used
   - Planned for: precise duration/resolution extraction, thumbnail generation
   - Files affected: `VideoScanner.kt`

2. **Settings Persistence**
   - `SettingsScreen` has UI (auto-scan, skip-corrupt, scan-limit)
   - No DataStore/SharedPreferences backend
   - Toggles are local UI state only

3. **Thumbnail Generation**
   - Placeholder in `VideoEntity.thumbnailPath`
   - Not yet implemented

4. **Checksum-based Corruption Detection**
   - `VideoEntity.checksum` field exists
   - Not generated during scan

### ⚠️ Code Issues

1. **VideoViewModel.searchVideos() issue** (line in VideoViewModel.kt)
   ```kotlin
   fun searchVideos(query: String): Flow<List<VideoEntity>> {
       return videoDao.searchVideos(query)  // ❌ videoDao not accessible
   }
   ```
   **Fix**: Delegate to repository: `return repository.searchVideos(query)`

2. **SettingsScreen Callback Mismatch**
   - Function parameter: `onClearDatabaseClick`
   - MainActivity passes: `onClearDatabase`
   - **Impact**: Settings clear button non-functional

3. **VideoViewModel Instantiation**
   - `MainActivity` creates ViewModel via default `viewModels()` delegate
   - No ViewModelFactory provided for repository injection
   - **Current**: ViewModelProvider.Factory uses reflection/Hilt
   - **Issue**: Repository not injected; ViewModel defaults to null dependencies

### 📝 Testing Gaps
- No instrumentation tests for UI (Compose)
- No integration tests for Room migrations
- No mocking of MediaStore scanner

---

## Performance Considerations

### Database Optimization
- **Indices**: 4 indices on frequently queried columns
- **FTS5**: BM25 ranking for search relevance
- **LIMIT 50**: SearchVideos caps results to prevent memory issues

### Coroutine Management
- **SupervisorJob** - Service continues if one scan fails
- **Dispatchers.IO** - Database and file I/O on background thread
- **Lifecycle-aware** - ViewModel.viewModelScope cancels on destruction

### UI Responsiveness
- **LazyColumn** - Renders only visible items
- **StateFlow** - Only emits on change (no redundant recompositions)
- **collectAsStateWithLifecycle** - Pauses collection on STOPPED state

---

## Security Considerations

### Scoped Storage Compliance
- **API 33+**: Uses `READ_MEDIA_VIDEO` instead of blanket storage access
- **API ≤32**: Falls back to `READ_EXTERNAL_STORAGE`
- Avoids direct file access; uses MediaStore API

### File Sharing
- **FileProvider** - Sandboxed file sharing for logs
- Temporary share scope (not persistent)

### Data Extraction Rules
- `data_extraction_rules.xml` - Controls backup inclusion
- `file_provider_paths.xml` - Defines shareable paths

---

## Development Workflow

### Build Tasks
```bash
./gradlew assembleDebug         # Build debug APK
./gradlew installDebug          # Install on device/emulator
./gradlew test                  # Unit tests
./gradlew connectedAndroidTest  # Instrumentation tests
./gradlew clean                 # Clean build
```

### Debugging
```bash
# Logcat filtering
adb logcat -s MainActivity VideoScannerService VideoScanner VideoViewModel VideoDatabase BugLogger

# Pull persistent log
adb shell run-as com.example.videolibrarymanager cat files/vlm_runlog.txt

# Clear app data
adb shell pm clear com.example.videolibrarymanager
```

### Project Structure Conventions
- **Packages by feature**: `data/`, `scanner/`, `service/`, `ui/`, `util/`
- **Naming**: CamelCase classes, snake_case resources/filenames
- **Kotlin idioms**: Data classes, extension functions, scope functions

---

## Version History

### v1.0 (Current)
- Initial release
- Jetpack Compose UI with Material3
- Room database with FTS5 search
- Persistent run/bug log (BugLogger)
- Foreground service scanning
- Settings, search, and log viewer screens
- FFmpeg Kit dependency (unused)

---

## Future Roadmap

1. **FFmpeg Integration** - Extract codec, bitrate, frame rate
2. **Thumbnail Generation** - UI preview images
3. **Settings Persistence** - DataStore backend for preferences
4. **Video Playback** - Integration with ExoPlayer
5. **Cloud Sync** - Backup/restore metadata
6. **Batch Operations** - Multi-select delete, move, tag
7. **Collection Management** - User-defined categories
8. **Advanced Filtering** - By resolution, duration, date range

---

## References

- [Android Room Persistence Library](https://developer.android.com/training/data-storage/room)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [FTS5 (Full-Text Search)](https://www.sqlite.org/fts5.html)
- [MediaStore API](https://developer.android.com/reference/android/provider/MediaStore)
- [Scoped Storage](https://developer.android.com/about/versions/11/privacy/storage)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-06-13 | CodeAssistant | Initial comprehensive analysis |

