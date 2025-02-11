package com.example.agentapp

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// Request and response data models for instructions and agent status.
data class InstructionRequest(val instruction: String)
data class InstructionResponse(val status: String, val agent_state: String?)
data class AgentStatus(val state: String, val message: String)

// New data model for each screenshot entry – now including a title.
data class ScreenshotEntryResponse(
    val title: String,
    val image: String,
    val timestamp: Long
)

// HistoryResponse now returns the conversation history and a list of screenshot entries.
data class HistoryResponse(
    val conversations: List<Map<String, Any>>,
    val images: List<ScreenshotEntryResponse>,
    val agent_status: AgentStatus
)

interface AgentApi {
    @POST("instruction")
    fun sendInstruction(@Body request: InstructionRequest): Call<InstructionResponse>

    @GET("history")
    fun getHistory(): Call<HistoryResponse>

    @GET("status")
    fun getStatus(): Call<AgentStatus>
}
