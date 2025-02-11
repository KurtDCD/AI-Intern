package com.example.agentapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.agentapp.databinding.ActivityAgentSettingsBinding

class AgentSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAgentSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgentSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val agentId = intent.getIntExtra("agentId", -1)
        val agent = AgentRepository.agents.find { it.id == agentId }

        if (agent != null) {
            binding.nameEditText.setText(agent.name)
            binding.ipEditText.setText(agent.serverIp)
            binding.portEditText.setText(agent.serverPort)
            binding.screenshotCountEditText.setText(agent.screenshotCount.toString())
        }

        binding.saveSettingsButton.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()
            val ip = binding.ipEditText.text.toString().trim()
            val port = binding.portEditText.text.toString().trim()
            val screenshotCount = binding.screenshotCountEditText.text.toString().toIntOrNull() ?: 3

            if (agent != null) {
                agent.name = name
                agent.serverIp = ip
                agent.serverPort = port
                agent.screenshotCount = screenshotCount
            } else {
                // Create a new agent.
                val newId = (AgentRepository.agents.maxByOrNull { it.id }?.id ?: 0) + 1
                val newAgent = Agent(
                    id = newId,
                    name = name,
                    serverIp = ip,
                    serverPort = port,
                    screenshotCount = screenshotCount
                )
                AgentRepository.agents.add(newAgent)
            }
            finish()
        }
    }
}
