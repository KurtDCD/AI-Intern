package com.example.agentapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.agentapp.databinding.ActivityChatListBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

class ChatListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatListBinding
    private lateinit var chatListAdapter: ChatListAdapter
    private val agentChats = mutableListOf<AgentChat>()
    private val gson = Gson()

    companion object {
        const val PREFS_NAME = "AgentAppPrefs"
        const val KEY_AGENT_CHATS = "agent_chats"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadAgentChats()

        chatListAdapter = ChatListAdapter(agentChats) { agentChat ->
            // Open ChatActivity for the selected agent.
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("agent_id", agentChat.id)
            startActivity(intent)
        }
        binding.chatListRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatListRecyclerView.adapter = chatListAdapter

        binding.fabAddChat.setOnClickListener {
            showAddChatDialog()
        }
    }

    private fun loadAgentChats() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_AGENT_CHATS, "[]")
        val type = object : TypeToken<MutableList<AgentChat>>() {}.type
        val list: MutableList<AgentChat> = gson.fromJson(json, type)
        agentChats.clear()
        agentChats.addAll(list)
    }

    private fun saveAgentChats() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_AGENT_CHATS, gson.toJson(agentChats)).apply()
    }

    private fun showAddChatDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_chat, null)
        val nameEditText = dialogView.findViewById<android.widget.EditText>(R.id.editTextAgentName)
        val ipEditText = dialogView.findViewById<android.widget.EditText>(R.id.editTextAgentIP)
        val portEditText = dialogView.findViewById<android.widget.EditText>(R.id.editTextAgentPort)
        val screenshotCountEditText = dialogView.findViewById<android.widget.EditText>(R.id.editTextScreenshotCount)

        MaterialAlertDialogBuilder(this)
            .setTitle("Add New Agent")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameEditText.text.toString().trim()
                val ip = ipEditText.text.toString().trim()
                val port = portEditText.text.toString().trim()
                val screenshotCount = screenshotCountEditText.text.toString().toIntOrNull() ?: 3
                if (name.isNotEmpty() && ip.isNotEmpty() && port.isNotEmpty()) {
                    val newAgent = AgentChat(UUID.randomUUID().toString(), name, ip, port, screenshotCount)
                    agentChats.add(newAgent)
                    saveAgentChats()
                    chatListAdapter.notifyItemInserted(agentChats.size - 1)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
