package com.example.videolibrarymanager.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.videolibrarymanager.data.VideoEntity
import com.example.videolibrarymanager.databinding.ItemVideoBinding

class VideoAdapter(private val onClick: (VideoEntity) -> Unit) :
    ListAdapter<VideoEntity, VideoAdapter.ViewHolder>(VideoDiffCallback()) {

    class ViewHolder(private val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(video: VideoEntity) {
            binding.title.text = video.title
            val minutes = video.duration / 60
            val seconds = video.duration % 60
            binding.duration.text = String.format("%d:%02d", minutes, seconds)
            binding.size.text = "${video.size / (1024 * 1024)} MB"

            Glide.with(binding.thumbnail.context)
                .load(if (video.thumbnailPath.isNotEmpty()) video.thumbnailPath else android.R.drawable.ic_menu_gallery)
                .centerCrop()
                .into(binding.thumbnail)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = getItem(position)
        holder.bind(video)
        holder.itemView.setOnClickListener { onClick(video) }
    }
}

class VideoDiffCallback : DiffUtil.ItemCallback<VideoEntity>() {
    override fun areItemsTheSame(old: VideoEntity, new: VideoEntity) = old.id == new.id
    override fun areContentsTheSame(old: VideoEntity, new: VideoEntity) = old == new
}
