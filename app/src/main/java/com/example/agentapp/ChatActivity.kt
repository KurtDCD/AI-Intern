package com.example.agentapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.agentapp.databinding.ActivityChatBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.text.style.RelativeSizeSpan
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

    // Reference to the persistent task bar view
    private lateinit var taskBar: View
    private lateinit var tvTaskInfo: TextView
    private lateinit var btnStopTask: ImageButton
    private lateinit var btnProgress: ImageButton

    // A timer for the elapsed time
    private var startTime: Long = 0
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            val elapsedMillis = System.currentTimeMillis() - startTime
            tvTaskInfo.text = "${agent?.name} • ${formatElapsedTime(elapsedMillis)}"
            timerHandler.postDelayed(this, 1000)
        }
    }

    private lateinit var customToolbarView: View
    private lateinit var tvAgentName: TextView
    private lateinit var ivStatusIcon: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up the toolbar (we’re using our custom toolbar layout)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.apply {
            // Enable custom view display
            setDisplayShowCustomEnabled(true)
            customToolbarView = layoutInflater.inflate(R.layout.custom_toolbar, null)
            customView = customToolbarView
        }
        // Get references from the custom toolbar view
        tvAgentName = customToolbarView.findViewById(R.id.agentNameTextView)
        ivStatusIcon = customToolbarView.findViewById(R.id.statusIcon)

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


        startPollingStatus()
        startPollingThoughts()

        // Get references to the task bar and its children
        taskBar = findViewById(R.id.taskStatusBarInclude)
        tvTaskInfo = taskBar.findViewById(R.id.tvTaskInfo)
        btnStopTask = taskBar.findViewById(R.id.btnStopTask)
        btnProgress = taskBar.findViewById(R.id.btnProgress)

        // Set click listeners for the icons and the bar (for example, tap on the text)
        btnStopTask.setOnClickListener { stopTask() }
        btnProgress.setOnClickListener { openProgressScreen() }
        taskBar.setOnClickListener { navigateToChat() }

        // Initially hide the task bar (it will be shown when a task is running)
        taskBar.visibility = View.GONE
    }



    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        // Retrieve the progress item’s custom view.
        val progressItem = menu.findItem(R.id.action_progress)
        progressItem?.actionView?.let { view ->
            // Set a long click listener to show the help message.
            view.setOnLongClickListener {
                Snackbar.make(binding.chatCoordinatorLayout, "Check progress", Snackbar.LENGTH_SHORT).show()
                true
            }
            // Set a normal click listener to open the progress screen.
            view.setOnClickListener {
                openProgressScreen()
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_progress -> {
                openProgressScreen()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
                    updateCustomToolbarStatus(status)
                } else {
                    showBanner("Poll status not successful", onlyIfRunning = true)
                }
            }
            override fun onFailure(call: Call<AgentStatus>, t: Throwable) {
                showBanner("Failed to poll status: ${t.localizedMessage}", onlyIfRunning = true)
            }
        })
    }

    // Call this function when a task starts (e.g., when an agent’s state becomes "running")
    private fun showTaskBar() {
        if (taskBar.visibility != View.VISIBLE) {
            taskBar.visibility = View.VISIBLE
            // record the start time for the timer (or retrieve it if already stored)
            startTime = System.currentTimeMillis()
            timerHandler.post(timerRunnable)
        }
    }

    private fun updateCustomToolbarStatus(status: AgentStatus?) {
        // Choose the appropriate drawable resource based on the status state.
        val drawableRes = when (status?.state) {
            "running", "idle" -> R.drawable.online_green
            "error" -> R.drawable.need_h_red
            else -> R.drawable.offline_grey // grey for offline or no connection
        }
        ivStatusIcon.setImageResource(drawableRes)
        // (Optionally, you can adjust the ImageView's layout params here if needed.)
    }

    // Call this function when the task ends or stops
    private fun hideTaskBar() {
        taskBar.visibility = View.GONE
        timerHandler.removeCallbacks(timerRunnable)
    }

    // Formats milliseconds to a string mm:ss
    private fun formatElapsedTime(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val remSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remSeconds)
    }

    // Function to stop the task (invoked when stop icon is pressed)
    private fun stopTask() {
        // Here you would call your API to stop the agent’s task.
        // For now, show a banner and hide the bar.
        Snackbar.make(findViewById(R.id.chatCoordinatorLayout), "Task stopped", Snackbar.LENGTH_SHORT).show()
        hideTaskBar()
    }

    // Function to open the progress screen for the current run.
    private fun openProgressScreen() {
        // Navigate to your ProgressActivity (or a dedicated current-run progress screen)
        startActivity(Intent(this, ProgressActivity::class.java))
    }

    // Function to navigate back to the chat screen if the task bar is tapped (excluding the icons)
    private fun navigateToChat() {
        // Since you are already in ChatActivity, you might simply scroll to the bottom.
        binding.chatRecyclerView.smoothScrollToPosition(agent?.conversation?.size ?: 0)
    }

    private fun handleAgentStateChange(status: AgentStatus) {
        when (status.state) {
            "waiting_for_user" -> {
                NotificationHelper.showNotification(this, "Agent Input Needed", status.message)
                addChatMessage(ChatMessage("system", "Agent is waiting for your input.", System.currentTimeMillis()))
                removeTypingBubble()
            }
            "finished" -> {
                hideTaskBar()
                NotificationHelper.showNotification(this, "Task Finished", status.message)
                addChatMessage(ChatMessage("system", "Task finished: ${status.message}", System.currentTimeMillis()))
                removeTypingBubble()
            }
            // For a "running" state we let the typing bubble logic (below) handle things.
            "running" -> {
                // Show the persistent task bar if not already visible.
                showTaskBar()
            }
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
