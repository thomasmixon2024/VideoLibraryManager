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
import com.example.videolibrarymanager.data.VideoDatabase
import com.example.videolibrarymanager.data.VideoRepository
import com.example.videolibrarymanager.service.VideoScannerService
import com.example.videolibrarymanager.ui.MainNavigationShell
import com.example.videolibrarymanager.ui.VideoViewModel
import com.example.videolibrarymanager.ui.VideoViewModelFactory
import com.example.videolibrarymanager.ui.theme.VideoLibraryManagerTheme
import com.example.videolibrarymanager.util.BugLogger

import com.example.videolibrarymanager.ui.SettingsViewModel
import com.example.videolibrarymanager.ui.SettingsViewModelFactory
import com.example.videolibrarymanager.data.SettingsRepository

class MainActivity : ComponentActivity() {

    private val viewModel: VideoViewModel by viewModels {
        val database = VideoDatabase.getDatabase(this)
        val repository = VideoRepository(database.videoDao())
        VideoViewModelFactory(repository)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        val repository = SettingsRepository.getInstance(applicationContext)
        SettingsViewModelFactory(repository)
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
                        settingsViewModel = settingsViewModel,
                        onClearDatabase = { viewModel.clearAllVideos() },
                        onRescan = { startVideoScanner() }
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
        BugLogger.info(TAG, "Requesting storage permission for: $perm")
        permissionLauncher.launch(perm)
    }

    private fun startVideoScanner() {
        BugLogger.info(TAG, "Spawning VideoScannerService foreground service.")
        val intent = Intent(this, VideoScannerService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun hasStoragePermission(): Boolean {
        val perm = storagePermission()
        val granted = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        BugLogger.debug(TAG, "Permission verification evaluation state for ($perm) = $granted")
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
