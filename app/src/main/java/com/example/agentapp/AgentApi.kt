// AgentApi.kt
package com.example.agentapp

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class InstructionRequest(val instruction: String)


data class InstructionResponse(val status: String, val agent_state: String?)
data class AgentStatus(val state: String, val message: String)

// For the new /agent_thoughts
data class AgentThought(val thought: String, val timestamp: Long)
data class AgentThoughtsResponse(val agent_thoughts: List<AgentThought>)

// For screenshots
data class ScreenshotEntryResponse(val title: String, val image: String, val timestamp: Long)
data class HistoryResponse(
    val conversations: List<Map<String, Any>>,
    val agent_thoughts: List<Map<String, Any>>,
    val images: List<ScreenshotEntryResponse>,
    val agent_status: AgentStatus
)

interface AgentApi {

    // Old calls remain:
    @POST("instruction")
    fun sendInstruction(@Body request: InstructionRequest): Call<InstructionResponse>

    // NEW: to handle "instruction + screenshot_count" if your server uses the same /instruction
    // endpoint. If you prefer the same endpoint, you can unify these into one function with
    // the proper request data shape. E.g. agent_server expects { instruction, screenshot_count }:
    @POST("instruction")
    fun sendInstructionWithCount(@Body request: InstructionRequestWithCount): Call<InstructionResponse>

    @GET("status")
    fun getStatus(): Call<AgentStatus>

    @GET("history")
    fun getHistory(): Call<HistoryResponse>

    // The new /agent_thoughts endpoint
    @GET("agent_thoughts")
    fun getAgentThoughts(): Call<AgentThoughtsResponse>
}
