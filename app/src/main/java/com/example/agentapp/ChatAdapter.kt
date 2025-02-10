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

        // Adjust bubble alignment based on sender.
        val params = holder.binding.bubbleContainer.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        if (msg.sender == "user") {
            params.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            params.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            holder.binding.bubbleContainer.setBackgroundResource(R.drawable.chat_bubble_user)
        } else if (msg.sender == "agent") {
            params.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            holder.binding.bubbleContainer.setBackgroundResource(R.drawable.chat_bubble_agent)
        }
        holder.binding.bubbleContainer.layoutParams = params

        // Format timestamp.
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        holder.binding.timestampTextView.text = sdf.format(Date(msg.timestamp))

        // If there is an image, decode and display it.
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
