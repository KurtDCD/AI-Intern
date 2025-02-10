package com.example.agentapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.agentapp.databinding.ActivityMainBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private val handler = Handler(Looper.getMainLooper())
    private val pollingInterval = 5000L  // Poll every 5 seconds
    private var lastAgentState: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load saved host settings
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val ip = prefs.getString(SettingsActivity.KEY_SERVER_IP, null)
        val port = prefs.getString(SettingsActivity.KEY_SERVER_PORT, null)
        if (!ip.isNullOrEmpty() && !port.isNullOrEmpty()) {
            val newUrl = "http://$ip:$port/"
            Log.d("MainActivity", "Loaded host settings: $newUrl")
            RetrofitClient.setBaseUrl(newUrl)
        } else {
            Log.d("MainActivity", "No saved host settings; using default.")
        }

        // Set up toolbar menu actions
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.action_progress -> {
                    startActivity(Intent(this, ProgressActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // Set up RecyclerView
        chatAdapter = ChatAdapter(chatMessages)
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatRecyclerView.adapter = chatAdapter

        // Handle send button click
        binding.sendButton.setOnClickListener {
            val text = binding.messageEditText.text.toString().trim()
            if (text.isNotEmpty()) {
                Log.d("MainActivity", "Send button clicked with: $text")
                sendInstruction(text)
                binding.messageEditText.text.clear()
            } else {
                Log.d("MainActivity", "Send button clicked but text is empty")
            }
        }

        // Start polling agent status
        startPollingStatus()
    }

    private fun sendInstruction(instructionText: String) {
        // Add user message immediately.
        addChatMessage(ChatMessage("user", instructionText, System.currentTimeMillis()))
        RetrofitClient.api.sendInstruction(InstructionRequest(instructionText))
            .enqueue(object : Callback<InstructionResponse> {
                override fun onResponse(
                    call: Call<InstructionResponse>,
                    response: Response<InstructionResponse>
                ) {
                    if (response.isSuccessful) {
                        Log.d("MainActivity", "sendInstruction onResponse: ${response.body()}")
                        // You may choose to add an agent response here if your server returns one.
                    } else {
                        Log.e("MainActivity", "sendInstruction onResponse not successful")
                        addChatMessage(
                            ChatMessage("system", "Error sending instruction", System.currentTimeMillis())
                        )
                    }
                }

                override fun onFailure(call: Call<InstructionResponse>, t: Throwable) {
                    Log.e("MainActivity", "sendInstruction onFailure: ${t.localizedMessage}", t)
                    addChatMessage(
                        ChatMessage("system", "Network error: ${t.localizedMessage}", System.currentTimeMillis())
                    )
                }
            })
    }

    private fun addChatMessage(message: ChatMessage) {
        chatMessages.add(message)
        runOnUiThread {
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            binding.chatRecyclerView.scrollToPosition(chatMessages.size - 1)
        }
    }

    private fun startPollingStatus() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                pollStatus()
                handler.postDelayed(this, pollingInterval)
            }
        }, pollingInterval)
    }

    private fun pollStatus() {
        RetrofitClient.api.getStatus().enqueue(object : Callback<AgentStatus> {
            override fun onResponse(call: Call<AgentStatus>, response: Response<AgentStatus>) {
                if (response.isSuccessful) {
                    response.body()?.let { status ->
                        Log.d("MainActivity", "Agent status: ${status.state}")
                        if (status.state != lastAgentState) {
                            lastAgentState = status.state
                            handleAgentStateChange(status)
                        }
                    }
                } else {
                    Log.e("MainActivity", "pollStatus response not successful")
                }
            }

            override fun onFailure(call: Call<AgentStatus>, t: Throwable) {
                Log.e("MainActivity", "Failed to poll agent status: ${t.localizedMessage}", t)
            }
        })
    }

    private fun handleAgentStateChange(status: AgentStatus) {
        when (status.state) {
            "waiting_for_user" -> {
                NotificationHelper.showNotification(this, "Agent Input Needed", status.message)
                addChatMessage(ChatMessage("system", "Agent is waiting for your input.", System.currentTimeMillis()))
            }
            "finished" -> {
                NotificationHelper.showNotification(this, "Task Finished", status.message)
                addChatMessage(ChatMessage("system", "Task finished: ${status.message}", System.currentTimeMillis()))
            }
            else -> {
                addChatMessage(ChatMessage("system", "Agent state: ${status.state}", System.currentTimeMillis()))
            }
        }
    }
}
