package com.example.agentapp

object AgentRepository {
    val agents = mutableListOf<Agent>()
    // For testing, prepopulate with one agent.
    init {
        agents.add(
            Agent(
                id = 1,
                name = "Agent 1",
                serverIp = "100.xx.xx.xx",
                serverPort = "5000",
                screenshotCount = 3
            )
        )
    }
}
