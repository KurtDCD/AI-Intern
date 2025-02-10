package com.example.agentapp

data class AgentChat(
    val id: String,       // A unique identifier (e.g. UUID)
    var name: String,
    var ip: String,
    var port: String,
    var screenshotCount: Int = 3
)
