package com.example.videolibrarymanager.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.videolibrarymanager.databinding.ActivityPlayerBinding
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem

class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val videoPath = intent.getStringExtra("VIDEO_PATH") ?: run {
            finish()
            return
        }

        player = ExoPlayer.Builder(this).build().apply {
            binding.playerView.player = this
            setMediaItem(MediaItem.fromUri(videoPath))
            prepare()
            playWhenReady = true
        }
    }

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }
}
