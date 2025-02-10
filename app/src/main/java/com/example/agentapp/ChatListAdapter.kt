package com.example.agentapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.agentapp.databinding.ItemChatListBinding

class ChatListAdapter(
    private val agentChats: List<AgentChat>,
    private val onChatSelected: (AgentChat) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ChatListViewHolder>() {

    inner class ChatListViewHolder(val binding: ItemChatListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(agentChat: AgentChat) {
            binding.agentNameTextView.text = agentChat.name
            binding.root.setOnClickListener { onChatSelected(agentChat) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatListViewHolder {
        val binding = ItemChatListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatListViewHolder, position: Int) {
        holder.bind(agentChats[position])
    }

    override fun getItemCount(): Int = agentChats.size
}
