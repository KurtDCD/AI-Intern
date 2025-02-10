// AgentApi.kt
package com.example.agentapp

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class InstructionRequest(val instruction: String)
data class InstructionResponse(val status: String, val agent_state: String?)
data class AgentStatus(val state: String, val message: String)
data class HistoryResponse(
    val conversations: List<Map<String, Any>>,
    val images: List<String>,
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
