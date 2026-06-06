package com.example.videolibrarymanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.example.videolibrarymanager.data.VideoDatabase
import com.example.videolibrarymanager.data.VideoRepository
import com.example.videolibrarymanager.service.VideoScannerService
import com.example.videolibrarymanager.ui.AppNavigation
import com.example.videolibrarymanager.ui.PermissionScreen
import com.example.videolibrarymanager.ui.VideoViewModel
import com.example.videolibrarymanager.util.BugLogger

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            BugLogger.info(TAG, "Storage permission GRANTED by user")
            startVideoScanner()
        } else {
            BugLogger.warn(TAG, "Storage permission DENIED by user")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BugLogger.info(TAG, "onCreate — savedInstanceState=${savedInstanceState != null}")

        val db = VideoDatabase.getDatabase(this)
        BugLogger.debug(TAG, "Room DB opened: video_library.db v${VideoDatabase.DB_VERSION}")

        val repository = VideoRepository(db.videoDao())
        val viewModel = VideoViewModel(repository)

        setContent {
            MaterialTheme {
                Surface {
                    if (hasStoragePermission()) {
                        BugLogger.debug(TAG, "Permission already granted — showing AppNavigation")
                        AppNavigation(viewModel = viewModel)
                    } else {
                        BugLogger.info(TAG, "No storage permission — showing PermissionScreen")
                        PermissionScreen(onGrantClicked = { requestStoragePermission() })
                    }
                }
            }
        }

        if (hasStoragePermission()) startVideoScanner()
    }

    override fun onResume()  { super.onResume();  BugLogger.debug(TAG, "onResume")  }
    override fun onPause()   { super.onPause();   BugLogger.debug(TAG, "onPause")   }
    override fun onDestroy() { super.onDestroy(); BugLogger.info(TAG, "onDestroy")  }

    private fun requestStoragePermission() {
        val perm = storagePermission()
        BugLogger.info(TAG, "Requesting permission: $perm")
        permissionLauncher.launch(perm)
    }

    private fun startVideoScanner() {
        BugLogger.info(TAG, "Starting VideoScannerService (foreground)")
        val intent = Intent(this, VideoScannerService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun hasStoragePermission(): Boolean {
        val perm = storagePermission()
        val granted = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        BugLogger.debug(TAG, "hasStoragePermission($perm) = $granted")
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
