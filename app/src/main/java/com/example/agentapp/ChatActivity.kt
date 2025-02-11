// ChatActivity.kt
package com.example.agentapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.agentapp.databinding.ActivityChatBinding
import com.google.android.material.snackbar.Snackbar
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var chatAdapter: ChatAdapter
    private var agent: Agent? = null

    // Polling intervals:
    private val handlerStatus = Handler(Looper.getMainLooper())
    private val handlerThoughts = Handler(Looper.getMainLooper())
    private val statusInterval = 5000L
    private val thoughtsInterval = 3000L

    private var lastAgentState: String = ""

    // Flag to track if we have a "typing" bubble displayed:
    private var isTypingBubbleVisible = false

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

        // Set up the action bar title to the agent name:
        supportActionBar?.title = agent!!.name

        // Prepare the adapter with the agent’s conversation list:
        chatAdapter = ChatAdapter(agent!!.conversation)
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatRecyclerView.adapter = chatAdapter

        // Send button:
        binding.sendButton.setOnClickListener {
            val text = binding.messageEditText.text.toString().trim()
            if (text.isNotEmpty()) {
                sendInstruction(text)
                binding.messageEditText.text.clear()
            }
        }

        startPollingStatus()
        startPollingThoughts()
    }

    private fun sendInstruction(instructionText: String) {
        // Immediately add user message locally:
        val userMsg = ChatMessage("user", instructionText, System.currentTimeMillis())
        agent?.conversation?.add(userMsg)
        chatAdapter.notifyItemInserted(agent!!.conversation.size - 1)
        binding.chatRecyclerView.scrollToPosition(agent!!.conversation.size - 1)

        // Construct the API request with instruction + screenshotCount:
        val newUrl = "http://${agent!!.serverIp}:${agent!!.serverPort}/"
        RetrofitClient.setBaseUrl(newUrl)

        val requestBody = InstructionRequestWithCount(
            instruction = instructionText,
            screenshot_count = agent!!.screenshotCount
        )
        // If you introduced a new data class:
        // data class InstructionRequestWithCount(
        //   val instruction: String,
        //   val screenshot_count: Int
        // )

        RetrofitClient.api.sendInstructionWithCount(requestBody)
            .enqueue(object : Callback<InstructionResponse> {
                override fun onResponse(call: Call<InstructionResponse>, response: Response<InstructionResponse>) {
                    if (!response.isSuccessful) {
                        showBanner("Error sending instruction", onlyIfRunning = true)
                        addChatMessage(ChatMessage("system", "Error sending instruction", System.currentTimeMillis()))
                    }
                }

                override fun onFailure(call: Call<InstructionResponse>, t: Throwable) {
                    showBanner("Network error: ${t.localizedMessage}", onlyIfRunning = true)
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

    // Poll the server’s /status endpoint, same as before:
    private fun startPollingStatus() {
        handlerStatus.postDelayed(object : Runnable {
            override fun run() {
                pollStatus()
                handlerStatus.postDelayed(this, statusInterval)
            }
        }, statusInterval)
    }

    private fun pollStatus() {
        val newUrl = "http://${agent!!.serverIp}:${agent!!.serverPort}/"
        RetrofitClient.setBaseUrl(newUrl)
        RetrofitClient.api.getStatus().enqueue(object : Callback<AgentStatus> {
            override fun onResponse(call: Call<AgentStatus>, response: Response<AgentStatus>) {
                if (response.isSuccessful) {
                    val status = response.body()!!
                    Log.d("ChatActivity", "Agent status: ${status.state}")
                    if (status.state != lastAgentState) {
                        lastAgentState = status.state
                        handleAgentStateChange(status)
                    }
                } else {
                    showBanner("Poll status not successful", onlyIfRunning = true)
                }
            }
            override fun onFailure(call: Call<AgentStatus>, t: Throwable) {
                showBanner("Failed to poll status: ${t.localizedMessage}", onlyIfRunning = true)
            }
        })
    }

    private fun handleAgentStateChange(status: AgentStatus) {
        when (status.state) {
            "waiting_for_user" -> {
                NotificationHelper.showNotification(this, "Agent Input Needed", status.message)
                addChatMessage(ChatMessage("system", "Agent is waiting for your input.", System.currentTimeMillis()))
                removeTypingBubble()
            }
            "finished" -> {
                NotificationHelper.showNotification(this, "Task Finished", status.message)
                addChatMessage(ChatMessage("system", "Task finished: ${status.message}", System.currentTimeMillis()))
                removeTypingBubble()
            }
            "running" -> {
                // Possibly do nothing special here, just let the typing bubble logic handle itself.
            }
            else -> {
                // Possibly an unknown state
                addChatMessage(ChatMessage("system", "Agent state: ${status.state}", System.currentTimeMillis()))
            }
        }
    }

    // Poll the new /agent_thoughts endpoint to get any new thoughts:
    private fun startPollingThoughts() {
        handlerThoughts.postDelayed(object : Runnable {
            override fun run() {
                pollThoughts()
                handlerThoughts.postDelayed(this, thoughtsInterval)
            }
        }, thoughtsInterval)
    }

    private var lastThoughtTimestamp: Long = 0

    private fun pollThoughts() {
        // If the agent is not running, we can remove any typing bubble and skip polling quickly:
        if (lastAgentState != "running") {
            removeTypingBubble()
            return
        } else {
            // If agent is running, show the typing bubble if not displayed:
            showTypingBubbleIfNeeded()
        }

        val newUrl = "http://${agent!!.serverIp}:${agent!!.serverPort}/"
        RetrofitClient.setBaseUrl(newUrl)
        RetrofitClient.api.getAgentThoughts().enqueue(object : Callback<AgentThoughtsResponse> {
            override fun onResponse(call: Call<AgentThoughtsResponse>, response: Response<AgentThoughtsResponse>) {
                if (!response.isSuccessful) {
                    showBanner("Error fetching thoughts", onlyIfRunning = true)
                    return
                }
                val thoughtsResponse = response.body()!!
                // agent_thoughts is a list of {thought, timestamp}
                // Add any new thoughts that are newer than lastThoughtTimestamp:
                for (thoughtObj in thoughtsResponse.agent_thoughts) {
                    if (thoughtObj.timestamp > lastThoughtTimestamp) {
                        // Add to chat as “agent”
                        addChatMessage(ChatMessage("agent", thoughtObj.thought, thoughtObj.timestamp))
                        lastThoughtTimestamp = thoughtObj.timestamp
                        // Remove typing bubble once we get an actual agent message
                        removeTypingBubble()
                    }
                }
            }

            override fun onFailure(call: Call<AgentThoughtsResponse>, t: Throwable) {
                showBanner("Failed to poll thoughts: ${t.localizedMessage}", onlyIfRunning = true)
            }
        })
    }

    private fun showTypingBubbleIfNeeded() {
        if (!isTypingBubbleVisible) {
            // Insert a ChatMessage with sender="typing"
            val typingMsg = ChatMessage("typing", "", System.currentTimeMillis())
            agent?.conversation?.add(typingMsg)
            chatAdapter.notifyItemInserted(agent!!.conversation.size - 1)
            binding.chatRecyclerView.scrollToPosition(agent!!.conversation.size - 1)
            isTypingBubbleVisible = true
        }
    }

    private fun removeTypingBubble() {
        if (isTypingBubbleVisible) {
            // Remove the last "typing" message if it exists at the end:
            val lastIndex = agent!!.conversation.lastIndex
            if (lastIndex >= 0 && agent!!.conversation[lastIndex].sender == "typing") {
                agent!!.conversation.removeAt(lastIndex)
                chatAdapter.notifyItemRemoved(lastIndex)
            }
            isTypingBubbleVisible = false
        }
    }

    /**
     * Show an ephemeral banner (Snackbar) for errors only if agent is running
     *  (if onlyIfRunning=true).
     */
    private fun showBanner(message: String, onlyIfRunning: Boolean) {
        if (onlyIfRunning && lastAgentState != "running") {
            return
        }
        Snackbar.make(binding.chatCoordinatorLayout, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handlerStatus.removeCallbacksAndMessages(null)
        handlerThoughts.removeCallbacksAndMessages(null)
    }
}

// A new request body data class:
data class InstructionRequestWithCount(
    val instruction: String,
    val screenshot_count: Int
)
