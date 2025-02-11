package com.example.agentapp

data class ChatMessage(
    val sender: String,        // "user", "agent", "system", or "typing"
    val message: String,
    val timestamp: Long,
    val imageBase64: String? = null
)
