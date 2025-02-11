// ChatAdapter.kt
package com.example.agentapp

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.agentapp.databinding.ItemChatBinding
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(private val messages: MutableList<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    inner class ChatViewHolder(val binding: ItemChatBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding =
            ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]

        // If this is a "typing" message, show a 3-dot animation and hide the normal text.
        if (msg.sender == "typing") {
            // Show a simple "..." or an animated dot view:
            holder.binding.messageTextView.text = "•••"
            holder.binding.progressImageView.visibility = View.GONE
            holder.binding.timestampTextView.visibility = View.GONE

            holder.binding.bubbleContainer.setBackgroundResource(R.drawable.chat_bubble_agent)
            holder.binding.bubbleContainer.gravity = Gravity.START
            val layoutParams = holder.binding.bubbleContainer.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.marginStart = 8
            layoutParams.marginEnd = 50
            holder.binding.bubbleContainer.layoutParams = layoutParams
            return
        }

        // Otherwise, normal message rendering:
        holder.binding.messageTextView.text = msg.message
        holder.binding.timestampTextView.visibility = View.VISIBLE

        // Adjust bubble background/alignment based on sender:
        val layoutParams = holder.binding.bubbleContainer.layoutParams as ViewGroup.MarginLayoutParams
        when (msg.sender) {
            "user" -> {
                holder.binding.bubbleContainer.setBackgroundResource(R.drawable.chat_bubble_user)
                layoutParams.marginStart = 50
                layoutParams.marginEnd = 8
                holder.binding.bubbleContainer.gravity = Gravity.END
            }
            "agent" -> {
                holder.binding.bubbleContainer.setBackgroundResource(R.drawable.chat_bubble_agent)
                layoutParams.marginStart = 8
                layoutParams.marginEnd = 50
                holder.binding.bubbleContainer.gravity = Gravity.START
            }
            "system" -> {
                holder.binding.bubbleContainer.setBackgroundResource(R.drawable.chat_bubble_system)
                layoutParams.marginStart = 8
                layoutParams.marginEnd = 8
                holder.binding.bubbleContainer.gravity = Gravity.CENTER
            }
            else -> {
                holder.binding.bubbleContainer.setBackgroundResource(R.drawable.chat_bubble_background)
                layoutParams.marginStart = 8
                layoutParams.marginEnd = 8
                holder.binding.bubbleContainer.gravity = Gravity.START
            }
        }
        holder.binding.bubbleContainer.layoutParams = layoutParams

        // Format timestamp:
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        holder.binding.timestampTextView.text = sdf.format(Date(msg.timestamp))

        if (!msg.imageBase64.isNullOrEmpty()) {
            val imageBytes = Base64.decode(msg.imageBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            holder.binding.progressImageView.setImageBitmap(bitmap)
            holder.binding.progressImageView.visibility = View.VISIBLE
        } else {
            holder.binding.progressImageView.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = messages.size
}
