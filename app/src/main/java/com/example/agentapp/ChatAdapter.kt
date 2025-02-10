package com.example.agentapp

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
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

        // Set the bubble background based on the sender.
        when (msg.sender) {
            "user" -> holder.binding.bubbleContainer.setBackgroundResource(R.drawable.chat_bubble_user)
            "agent" -> holder.binding.bubbleContainer.setBackgroundResource(R.drawable.chat_bubble_agent)
            "system" -> holder.binding.bubbleContainer.setBackgroundResource(R.drawable.chat_bubble_system)
            else -> holder.binding.bubbleContainer.setBackgroundResource(R.drawable.chat_bubble_background)
        }

        // Format the timestamp.
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        holder.binding.timestampTextView.text = sdf.format(Date(msg.timestamp))

        // If there's an image, decode and display it; otherwise hide the ImageView.
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
