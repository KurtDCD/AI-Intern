package com.example.agentapp

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.agentapp.databinding.ItemProgressBinding

class ProgressAdapter(private val images: List<ScreenshotEntryResponse>) :
    RecyclerView.Adapter<ProgressAdapter.ProgressViewHolder>() {

    inner class ProgressViewHolder(val binding: ItemProgressBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgressViewHolder {
        val binding = ItemProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProgressViewHolder(binding)
    }

    override fun getItemCount(): Int = images.size

    override fun onBindViewHolder(holder: ProgressViewHolder, position: Int) {
        val entry = images[position]
        holder.binding.titleTextView.text = entry.title
        val imageBytes = Base64.decode(entry.image, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        holder.binding.progressImageView.setImageBitmap(bitmap)
    }
}
