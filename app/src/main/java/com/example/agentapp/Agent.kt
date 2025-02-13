package com.example.agentapp

data class Agent(
    val id: Int,
    var name: String,
    var serverIp: String,
    var serverPort: String,
    var screenshotCount: Int,
    var conversation: MutableList<ChatMessage> = mutableListOf(),
    var progress: MutableList<ScreenshotEntry> = mutableListOf(),
    var status: AgentStatus? = null  // new: current status from the server
)

data class ScreenshotEntry(
    val title: String,
    val imageBase64: String,
    val timestamp: Long
)
