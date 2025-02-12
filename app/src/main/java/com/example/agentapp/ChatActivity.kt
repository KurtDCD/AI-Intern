package com.example.agentapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.agentapp.databinding.ActivityChatBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var chatAdapter: ChatAdapter
    private var agent: Agent? = null

    // Polling handlers and intervals
    private val handlerStatus = Handler(Looper.getMainLooper())
    private val handlerThoughts = Handler(Looper.getMainLooper())
    private val statusInterval = 5000L
    private val thoughtsInterval = 3000L
    private var lastAgentState: String = ""

    // For tracking the latest thought timestamp
    private var lastThoughtTimestamp: Long = 0

    // Flag for the typing bubble (for agent thoughts)
    private var isTypingBubbleVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve the agent from our repository.
        val agentId = intent.getIntExtra("agentId", -1)
        agent = AgentRepository.agents.find { it.id == agentId }
        if (agent == null) {
            finish()
            return
        }

        // Set the action bar title to the agent’s name.
        supportActionBar?.title = agent!!.name
        // Set an initial subtitle (we’ll update it as we poll)
        updateActionBarStatus(null)

        // Set up the chat RecyclerView using the agent’s conversation list.
        chatAdapter = ChatAdapter(agent!!.conversation)
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatRecyclerView.adapter = chatAdapter

        // Set up the send button.
        binding.sendButton.setOnClickListener {
            val text = binding.messageEditText.text.toString().trim()
            if (text.isNotEmpty()) {
                sendInstruction(text)
                binding.messageEditText.text.clear()
            }
        }

        // Set up a FAB to show progress history inside the chat.
        binding.progressFab.setOnClickListener {
            showProgressDialog()
        }

        startPollingStatus()
        startPollingThoughts()
    }

    /**
     * Updates the action bar’s subtitle with a colored status indicator.
     * If status is null or not provided, it will display "Offline".
     */
    private fun updateActionBarStatus(status: AgentStatus?) {
        val statusStr = when (status?.state) {
            "running", "idle" -> "🟢 Online"
            "error" -> "🔴 Error"
            else -> "⚪ Offline"
        }
        supportActionBar?.subtitle = statusStr
    }

    private fun sendInstruction(instructionText: String) {
        // Add the user message locally.
        val userMsg = ChatMessage("user", instructionText, System.currentTimeMillis())
        agent?.conversation?.add(userMsg)
        chatAdapter.notifyItemInserted(agent!!.conversation.size - 1)
        binding.chatRecyclerView.scrollToPosition(agent!!.conversation.size - 1)

        // Set the base URL using the agent’s settings.
        val newUrl = "http://${agent!!.serverIp}:${agent!!.serverPort}/"
        RetrofitClient.setBaseUrl(newUrl)

        // Create the request body with screenshot_count.
        val requestBody = InstructionRequestWithCount(
            instruction = instructionText,
            screenshot_count = agent!!.screenshotCount
        )
        RetrofitClient.api.sendInstructionWithCount(requestBody)
            .enqueue(object : Callback<InstructionResponse> {
                override fun onResponse(
                    call: Call<InstructionResponse>,
                    response: Response<InstructionResponse>
                ) {
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

    // Poll the /status endpoint.
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
                    updateActionBarStatus(status)
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
            // For a "running" state we let the typing bubble logic (below) handle things.
            else -> {
                addChatMessage(ChatMessage("system", "Agent state: ${status.state}", System.currentTimeMillis()))
            }
        }
    }

    // Poll the new /agent_thoughts endpoint to get agent thoughts.
    private fun startPollingThoughts() {
        handlerThoughts.postDelayed(object : Runnable {
            override fun run() {
                pollThoughts()
                handlerThoughts.postDelayed(this, thoughtsInterval)
            }
        }, thoughtsInterval)
    }

    private fun pollThoughts() {
        // Only poll thoughts if the agent is running.
        if (lastAgentState != "running") {
            removeTypingBubble()
            return
        } else {
            showTypingBubbleIfNeeded()
        }

        val newUrl = "http://${agent!!.serverIp}:${agent!!.serverPort}/"
        RetrofitClient.setBaseUrl(newUrl)
        RetrofitClient.api.getAgentThoughts().enqueue(object : Callback<AgentThoughtsResponse> {
            override fun onResponse(
                call: Call<AgentThoughtsResponse>,
                response: Response<AgentThoughtsResponse>
            ) {
                if (!response.isSuccessful) {
                    showBanner("Error fetching thoughts", onlyIfRunning = true)
                    return
                }
                val thoughtsResponse = response.body()!!
                // For each new thought (with timestamp greater than lastThoughtTimestamp), add it.
                for (thoughtObj in thoughtsResponse.agent_thoughts) {
                    if (thoughtObj.timestamp > lastThoughtTimestamp) {
                        addChatMessage(ChatMessage("agent", thoughtObj.thought, thoughtObj.timestamp))
                        lastThoughtTimestamp = thoughtObj.timestamp
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
            val typingMsg = ChatMessage("typing", "", System.currentTimeMillis())
            agent?.conversation?.add(typingMsg)
            chatAdapter.notifyItemInserted(agent!!.conversation.size - 1)
            binding.chatRecyclerView.scrollToPosition(agent!!.conversation.size - 1)
            isTypingBubbleVisible = true
        }
    }

    private fun removeTypingBubble() {
        if (isTypingBubbleVisible) {
            val lastIndex = agent!!.conversation.lastIndex
            if (lastIndex >= 0 && agent!!.conversation[lastIndex].sender == "typing") {
                agent!!.conversation.removeAt(lastIndex)
                chatAdapter.notifyItemRemoved(lastIndex)
            }
            isTypingBubbleVisible = false
        }
    }

    /**
     * Show an ephemeral banner (Snackbar) for errors only if agent is running.
     */
    private fun showBanner(message: String, onlyIfRunning: Boolean) {
        if (onlyIfRunning && lastAgentState != "running") return
        Snackbar.make(binding.chatCoordinatorLayout, message, Snackbar.LENGTH_SHORT).show()
    }

    /**
     * Opens a bottom sheet dialog that displays the progress (screenshot history).
     * The progress items are fetched from the /history endpoint.
     */
    private fun showProgressDialog() {
        val dialog = BottomSheetDialog(this)
        // Inflate a custom layout for the progress dialog.
        val view = layoutInflater.inflate(R.layout.dialog_progress, null)
        dialog.setContentView(view)

        // Get the RecyclerView from the dialog layout.
        val progressRecyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.dialogProgressRecyclerView)
        progressRecyclerView.layoutManager = LinearLayoutManager(this)
        val progressItems = mutableListOf<ScreenshotEntryResponse>()
        val progressAdapter = ProgressAdapter(progressItems)
        progressRecyclerView.adapter = progressAdapter

        // Fetch progress items from the /history endpoint.
        val newUrl = "http://${agent!!.serverIp}:${agent!!.serverPort}/"
        RetrofitClient.setBaseUrl(newUrl)
        RetrofitClient.api.getHistory().enqueue(object : Callback<HistoryResponse> {
            override fun onResponse(call: Call<HistoryResponse>, response: Response<HistoryResponse>) {
                if (response.isSuccessful) {
                    response.body()?.let { history ->
                        progressItems.clear()
                        progressItems.addAll(history.images)
                        progressAdapter.notifyDataSetChanged()
                    }
                }
            }
            override fun onFailure(call: Call<HistoryResponse>, t: Throwable) {
                showBanner("Failed to fetch progress: ${t.localizedMessage}", onlyIfRunning = false)
            }
        })

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handlerStatus.removeCallbacksAndMessages(null)
        handlerThoughts.removeCallbacksAndMessages(null)
    }
}

// Data class for sending instructions with screenshot_count.
data class InstructionRequestWithCount(
    val instruction: String,
    val screenshot_count: Int
)
