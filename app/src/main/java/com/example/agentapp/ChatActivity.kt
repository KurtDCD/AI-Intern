package com.example.agentapp

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import com.example.agentapp.databinding.ActivityChatBinding
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private val handler = Handler(Looper.getMainLooper())
    private val pollingInterval = 5000L  // Poll every 5 seconds
    private var lastAgentState: String = ""
    private var agentId: String? = null
    private var agentChat: AgentChat? = null
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Enable up navigation in the top bar.
        binding.topAppBar.setNavigationOnClickListener { finish() }

        // Get agent id from intent extras and load its settings.
        agentId = intent.getStringExtra("agent_id")
        if (agentId != null) {
            agentChat = loadAgentChat(agentId!!)
            if (agentChat != null) {
                // Set header title to agent name.
                title = agentChat!!.name
                val newUrl = "http://${agentChat!!.ip}:${agentChat!!.port}/"
                Log.d("ChatActivity", "Loaded agent URL: $newUrl")
                RetrofitClient.setBaseUrl(newUrl)
            }
        }

        // Load conversation for this agent from persistent storage.
        if (agentId != null) {
            loadConversation(agentId!!)
        }

        // Set up the conversation RecyclerView.
        chatAdapter = ChatAdapter(chatMessages)
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatRecyclerView.adapter = chatAdapter

        // Set up the horizontal progress RecyclerView.
        binding.progressRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.progressRecyclerView.adapter = ProgressAdapter(emptyList())
        LinearSnapHelper().attachToRecyclerView(binding.progressRecyclerView)

        binding.sendButton.setOnClickListener {
            val text = binding.messageEditText.text.toString().trim()
            if (text.isNotEmpty()) {
                sendInstruction(text)
                binding.messageEditText.text.clear()
            }
        }

        startPollingStatus()
        fetchProgress()
    }

    private fun sendInstruction(instructionText: String) {
        addChatMessage(ChatMessage("user", instructionText, System.currentTimeMillis()))
        RetrofitClient.api.sendInstruction(InstructionRequest(instructionText))
            .enqueue(object : Callback<InstructionResponse> {
                override fun onResponse(call: Call<InstructionResponse>, response: Response<InstructionResponse>) {
                    if (response.isSuccessful) {
                        Log.d("ChatActivity", "sendInstruction onResponse: ${response.body()}")
                        // Optionally, process agent reply here.
                    } else {
                        addSystemMessage("Error sending instruction")
                    }
                }
                override fun onFailure(call: Call<InstructionResponse>, t: Throwable) {
                    Log.e("ChatActivity", "sendInstruction onFailure: ${t.localizedMessage}", t)
                    addSystemMessage("Network error: ${t.localizedMessage}")
                }
            })
    }

    private fun addChatMessage(message: ChatMessage) {
        chatMessages.add(message)
        runOnUiThread {
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            binding.chatRecyclerView.scrollToPosition(chatMessages.size - 1)
        }
        // Persist conversation
        agentId?.let { saveConversation(it, chatMessages) }
    }

    private fun addSystemMessage(message: String) {
        // Display system messages as a transient Snackbar.
        runOnUiThread {
            Snackbar.make(binding.coordinatorLayout, message, Snackbar.LENGTH_SHORT).show()
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
                        Log.d("ChatActivity", "Agent status: ${status.state}")
                        if (status.state != lastAgentState) {
                            lastAgentState = status.state
                            handleAgentStateChange(status)
                        }
                    }
                } else {
                    Log.e("ChatActivity", "pollStatus response not successful")
                }
            }
            override fun onFailure(call: Call<AgentStatus>, t: Throwable) {
                Log.e("ChatActivity", "Failed to poll agent status: ${t.localizedMessage}", t)
            }
        })
    }

    private fun handleAgentStateChange(status: AgentStatus) {
        when (status.state) {
            "waiting_for_user" -> addSystemMessage("Agent is waiting for your input.")
            "finished" -> addSystemMessage("Task finished: ${status.message}")
            else -> addSystemMessage("Agent state: ${status.state}")
        }
    }

    private fun fetchProgress() {
        RetrofitClient.api.getHistory().enqueue(object : Callback<HistoryResponse> {
            override fun onResponse(call: Call<HistoryResponse>, response: Response<HistoryResponse>) {
                if (response.isSuccessful) {
                    response.body()?.let { history ->
                        runOnUiThread {
                            binding.progressRecyclerView.adapter = ProgressAdapter(history.images)
                        }
                    }
                }
            }
            override fun onFailure(call: Call<HistoryResponse>, t: Throwable) {
                Log.e("ChatActivity", "Failed to fetch progress: ${t.localizedMessage}", t)
            }
        })
    }

    // Conversation persistence functions
    private fun loadConversation(agentId: String) {
        val prefs = getSharedPreferences("ConversationPrefs", Context.MODE_PRIVATE)
        val json = prefs.getString(agentId, "[]")
        val type = object : TypeToken<MutableList<ChatMessage>>() {}.type
        val list: MutableList<ChatMessage> = gson.fromJson(json, type)
        chatMessages.clear()
        chatMessages.addAll(list)
    }

    private fun saveConversation(agentId: String, messages: List<ChatMessage>) {
        val prefs = getSharedPreferences("ConversationPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString(agentId, gson.toJson(messages)).apply()
    }

    private fun loadAgentChat(agentId: String): AgentChat? {
        val prefs = getSharedPreferences(ChatListActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(ChatListActivity.KEY_AGENT_CHATS, "[]")
        val type = object : TypeToken<MutableList<AgentChat>>() {}.type
        val list: MutableList<AgentChat> = gson.fromJson(json, type)
        return list.find { it.id == agentId }
    }
}
