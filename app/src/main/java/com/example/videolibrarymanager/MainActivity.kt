package com.example.videolibrarymanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.videolibrarymanager.data.VideoDatabase
import com.example.videolibrarymanager.data.VideoRepository
import com.example.videolibrarymanager.ui.MainNavigationShell
import com.example.videolibrarymanager.ui.VideoViewModel
import com.example.videolibrarymanager.ui.VideoViewModelFactory
import com.example.videolibrarymanager.ui.theme.VideoLibraryManagerTheme
import com.example.videolibrarymanager.util.BugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: VideoViewModel by viewModels {
        val database = VideoDatabase.getDatabase(this)
        val repository = VideoRepository(database.videoDao())
        VideoViewModelFactory(repository)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            BugLogger.info(TAG, "Storage permission granted by user callback.")
            startVideoScanner()
        } else {
            BugLogger.warn(TAG, "Storage permission denied by user callback.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BugLogger.info(TAG, "onCreate initialized.")

        setContent {
            VideoLibraryManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigationShell(
                        viewModel = viewModel,
                        onVideoClick = { video ->
                            BugLogger.debug(TAG, "Selected Video Playback Event: \")
                        },
                        onClearDatabase = {
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    VideoDatabase.getDatabase(this@MainActivity).videoDao().clearAllVideos()
                                    BugLogger.info(TAG, "Video catalog database cleared by user preference configuration.")
                                } catch (e: Exception) {
                                    BugLogger.error(TAG, "Failed to completely wipe the catalog database", e)
                                }
                            }
                        }
                    )
                }
            }
        }

        if (hasStoragePermission()) {
            startVideoScanner()
        } else {
            requestStoragePermission()
        }
    }

    override fun onResume()  { super.onResume();  BugLogger.debug(TAG, "onResume called")  }
    override fun onPause()   { super.onPause();   BugLogger.debug(TAG, "onPause called")   }
    override fun onDestroy() { super.onDestroy(); BugLogger.info(TAG, "onDestroy called")  }

    private fun requestStoragePermission() {
        val perm = storagePermission()
        BugLogger.info(TAG, "Requesting permission token: \")
        permissionLauncher.launch(perm)
    }

    private fun startVideoScanner() {
        BugLogger.info(TAG, "Spawning VideoScannerService background foreground context.")
        try {
            val intent = Intent(this, Class.forName("com.example.videolibrarymanager.service.VideoScannerService"))
            ContextCompat.startForegroundService(this, intent)
        } catch (e: ClassNotFoundException) {
            BugLogger.error(TAG, "VideoScannerService definition missing from package context.", e)
        }
    }

    private fun hasStoragePermission(): Boolean {
        val perm = storagePermission()
        val granted = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        BugLogger.debug(TAG, "Permission verification evaluation state for (\) = \")
        return granted
    }

    private fun storagePermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_VIDEO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

    companion object {
        private const val TAG = "MainActivity"
    }
}
