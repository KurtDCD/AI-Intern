package com.example.agentapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.agentapp.databinding.ActivityChatBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var chatAdapter: ChatAdapter
    private var agent: Agent? = null
    private val handler = Handler(Looper.getMainLooper())
    private val pollingInterval = 5000L  // Poll every 5 seconds
    private var lastAgentState: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val agentId = intent.getIntExtra("agentId", -1)
        agent = AgentRepository.agents.find { it.id == agentId }
        if (agent == null) {
            finish()
            return
        }

        // Set toolbar title to the agent's name.
        supportActionBar?.title = agent!!.name

        chatAdapter = ChatAdapter(agent!!.conversation)
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatRecyclerView.adapter = chatAdapter

        binding.sendButton.setOnClickListener {
            val text = binding.messageEditText.text.toString().trim()
            if (text.isNotEmpty()) {
                sendInstruction(text)
                binding.messageEditText.text.clear()
            }
        }

        startPollingStatus()
    }

    private fun sendInstruction(instructionText: String) {
        agent?.conversation?.add(ChatMessage("user", instructionText, System.currentTimeMillis()))
        runOnUiThread {
            chatAdapter.notifyItemInserted(agent!!.conversation.size - 1)
            binding.chatRecyclerView.scrollToPosition(agent!!.conversation.size - 1)
        }
        val newUrl = "http://${agent!!.serverIp}:${agent!!.serverPort}/"
        RetrofitClient.setBaseUrl(newUrl)
        RetrofitClient.api.sendInstruction(InstructionRequest(instructionText))
            .enqueue(object : Callback<InstructionResponse> {
                override fun onResponse(call: Call<InstructionResponse>, response: Response<InstructionResponse>) {
                    if (response.isSuccessful) {
                        Log.d("ChatActivity", "sendInstruction onResponse: ${response.body()}")
                    } else {
                        addChatMessage(ChatMessage("system", "Error sending instruction", System.currentTimeMillis()))
                    }
                }

                override fun onFailure(call: Call<InstructionResponse>, t: Throwable) {
                    addChatMessage(ChatMessage("system", "Network error: ${t.localizedMessage}", System.currentTimeMillis()))
                }
            })
    }

    private fun addChatMessage(message: ChatMessage) {
        agent?.conversation?.add(message)
        runOnUiThread {
            chatAdapter.notifyItemInserted(agent!!.conversation.size - 1)
            binding.chatRecyclerView.scrollToPosition(agent!!.conversation.size - 1)
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

    override fun onResume() {
        super.onResume()
        chatAdapter.notifyDataSetChanged()
    }
}
