# Phase 0: Preparation & Issue Resolution

## Overview
Phase 0 focuses on fixing critical issues identified in the codebase analysis and preparing the project for feature development.

## Analysis Summary
- **Repository**: Video Library Manager (Android Jetpack Compose)
- **Status**: Functional but with 3 critical issues
- **Launch Config**: ✅ Created with 8 debug configurations
- **Deep Analysis**: ✅ Completed (150+ line document)

---

## Critical Issues to Fix

### 1. ⚠️ VideoViewModel.searchVideos() - Undefined DAO Reference
**File**: [app/src/main/java/com/example/videolibrarymanager/ui/VideoViewModel.kt](app/src/main/java/com/example/videolibrarymanager/ui/VideoViewModel.kt)

**Current Code**:
```kotlin
fun searchVideos(query: String): kotlinx.coroutines.flow.Flow<List<com.example.videolibrarymanager.data.VideoEntity>> {
    return videoDao.searchVideos(query)  // ❌ videoDao not accessible
}
```

**Issue**: `videoDao` is not a member of VideoViewModel. The class only has access to `repository`.

**Fix**: 
```kotlin
fun searchVideos(query: String): Flow<List<VideoEntity>> {
    return repository.searchVideos(query)  // ✅ Delegate to repository
}
```

**Impact**: Search functionality currently broken in VideoSearchScreen

---

### 2. ⚠️ SettingsScreen Callback Mismatch
**File**: [app/src/main/java/com/example/videolibrarymanager/ui/SettingsScreen.kt](app/src/main/java/com/example/videolibrarymanager/ui/SettingsScreen.kt)

**Issue**: Function signature mismatch between MainActivity and SettingsScreen

**MainActivity passes**:
```kotlin
onClearDatabase = { /* clear logic */ }
```

**SettingsScreen expects**:
```kotlin
fun SettingsScreen(
    onClearDatabaseClick: () -> Unit,  // ❌ Different name
    modifier: Modifier = Modifier
)
```

**Fix**: Align parameter naming in SettingsScreen to `onClearDatabase` or update MainActivity call site

---

### 3. ⚠️ VideoViewModel Instantiation Without Repository
**File**: [app/src/main/java/com/example/videolibrarymanager/MainActivity.kt](app/src/main/java/com/example/videolibrarymanager/MainActivity.kt)

**Issue**: ViewModel created without ViewModelFactory
```kotlin
private val viewModel: VideoViewModel by viewModels()  // ❌ No factory
```

**Problem**: VideoViewModel requires VideoRepository constructor parameter, but default instantiation cannot provide it.

**Current ViewModel Code**:
```kotlin
class VideoViewModel(
    private val repository: VideoRepository
) : ViewModel() { ... }
```

**Solution Options**:

**Option A: Create ViewModelFactory**
```kotlin
class VideoViewModelFactory(private val repository: VideoRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VideoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VideoViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
```

Then in MainActivity:
```kotlin
private val viewModel: VideoViewModel by viewModels {
    VideoViewModelFactory(
        VideoRepository(VideoDatabase.getDatabase(this).videoDao())
    )
}
```

**Option B: Make repository lazy-initialized in ViewModel (Simpler)**
```kotlin
class VideoViewModel(
    private val context: Context? = null
) : ViewModel() {
    private val repository by lazy {
        val dao = VideoDatabase.getDatabase(context!!).videoDao()
        VideoRepository(dao)
    }
    // ... rest of class
}
```

---

## Phase 0 Tasks

### Task 1: Fix VideoViewModel.searchVideos()
**Priority**: HIGH  
**Impact**: Unblocks search functionality  
**Steps**:
1. Open VideoViewModel.kt
2. Locate searchVideos() method
3. Change `videoDao.searchVideos(query)` to `repository.searchVideos(query)`
4. Remove invalid import if present
5. Test compilation

---

### Task 2: Fix SettingsScreen Callback Mismatch
**Priority**: HIGH  
**Impact**: Enables clear database functionality  
**Steps**:
1. Open SettingsScreen.kt
2. Change parameter name from `onClearDatabaseClick` to `onClearDatabase`
3. Update all internal references to callback
4. Verify MainActivity passes correct parameter name
5. Test UI navigation

---

### Task 3: Implement VideoViewModel Factory
**Priority**: MEDIUM  
**Impact**: Proper dependency injection for ViewModel  
**Steps**:
1. Create new file: `app/src/main/java/com/example/videolibrarymanager/ui/VideoViewModelFactory.kt`
2. Implement ViewModelProvider.Factory
3. Update MainActivity to use factory
4. Test ViewModel initialization

---

### Task 4: Verify Build & Tests Pass
**Priority**: CRITICAL  
**Impact**: Baseline validation  
**Steps**:
```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Build with warnings
./gradlew build --warning-mode all
```

---

### Task 5: Documentation & Launch Config Validation
**Priority**: MEDIUM  
**Impact**: Developer experience, debugging  
**Steps**:
1. Verify launch.json works with all 8 configurations
2. Test "Build and Run Debug APK" configuration
3. Test "ADB Logcat" configuration
4. Update DEEP_ANALYSIS.md with post-fix notes

---

## Execution Checklist

- [ ] **Fix 1** - VideoViewModel.searchVideos() - estimate 5 min
- [ ] **Fix 2** - SettingsScreen callback - estimate 10 min
- [ ] **Fix 3** - VideoViewModelFactory - estimate 20 min
- [ ] **Compile & Test** - estimate 30 min
- [ ] **Verify Launch Config** - estimate 10 min
- [ ] **Phase 0 Complete** ✅

**Total Estimated Time**: ~75 minutes

---

## Files to Modify

| File | Change Type | Severity |
|------|-------------|----------|
| VideoViewModel.kt | Fix method body | HIGH |
| SettingsScreen.kt | Fix callback name | HIGH |
| VideoViewModelFactory.kt | Create new file | MEDIUM |
| MainActivity.kt | Update ViewModel instantiation | MEDIUM |

---

## Success Criteria for Phase 0

✅ All compilation errors resolved  
✅ All unit tests pass  
✅ SearchVideos() delegates correctly to repository  
✅ SettingsScreen callbacks align with MainActivity  
✅ ViewModelFactory properly injects dependencies  
✅ Launch configurations all functional  
✅ No warnings in build  

---

## Post-Phase 0 Goals (Phase 1+)

- [ ] FFmpeg Kit integration (codec extraction, metadata)
- [ ] Thumbnail generation from video files
- [ ] Settings persistence (DataStore)
- [ ] Video playback integration (ExoPlayer)
- [ ] Comprehensive UI tests (Compose)
- [ ] Database instrumentation tests

---

## References

- **Launch Config**: [.vscode/launch.json](.vscode/launch.json)
- **Deep Analysis**: [DEEP_ANALYSIS.md](DEEP_ANALYSIS.md)
- **Session Notes**: Memory file with detailed breakdown

