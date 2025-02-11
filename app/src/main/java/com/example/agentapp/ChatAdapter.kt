package com.example.agentapp

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.agentapp.databinding.ItemChatBinding
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(private val messages: List<ChatMessage>) :
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
        holder.binding.messageTextView.text = msg.message

        // Adjust bubble background and alignment based on sender.
        val layoutParams = holder.binding.bubbleContainer.layoutParams as ViewGroup.MarginLayoutParams
        when (msg.sender) {
            "user" -> {
                holder.binding.bubbleContainer.setBackgroundResource(R.drawable.chat_bubble_user)
                layoutParams.marginStart = 50
                layoutParams.marginEnd = 8
                holder.binding.bubbleContainer.layoutParams = layoutParams
                // Align to right:
                holder.binding.bubbleContainer.gravity = Gravity.END
            }
            "agent" -> {
                holder.binding.bubbleContainer.setBackgroundResource(R.drawable.chat_bubble_agent)
                layoutParams.marginStart = 8
                layoutParams.marginEnd = 50
                holder.binding.bubbleContainer.layoutParams = layoutParams
                holder.binding.bubbleContainer.gravity = Gravity.START
            }
            "system" -> {
                holder.binding.bubbleContainer.setBackgroundResource(R.drawable.chat_bubble_system)
                layoutParams.marginStart = 8
                layoutParams.marginEnd = 8
                holder.binding.bubbleContainer.layoutParams = layoutParams
                holder.binding.bubbleContainer.gravity = Gravity.CENTER
            }
            else -> {
                holder.binding.bubbleContainer.setBackgroundResource(R.drawable.chat_bubble_background)
                layoutParams.marginStart = 8
                layoutParams.marginEnd = 8
                holder.binding.bubbleContainer.layoutParams = layoutParams
                holder.binding.bubbleContainer.gravity = Gravity.START
            }
        }

        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        holder.binding.timestampTextView.text = sdf.format(Date(msg.timestamp))

        if (!msg.imageBase64.isNullOrEmpty()) {
            val imageBytes = Base64.decode(msg.imageBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            holder.binding.progressImageView.setImageBitmap(bitmap)
            holder.binding.progressImageView.visibility = android.view.View.VISIBLE
        } else {
            holder.binding.progressImageView.visibility = android.view.View.GONE
        }
    }

    override fun getItemCount(): Int = messages.size
}
