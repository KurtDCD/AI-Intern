package com.example.agentapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.agentapp.databinding.ActivityChatsBinding

class ChatsActivity : AppCompatActivity(), AgentListAdapter.AgentItemListener {

    private lateinit var binding: ActivityChatsBinding
    private lateinit var adapter: AgentListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AgentListAdapter(AgentRepository.agents, this)
        binding.agentsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.agentsRecyclerView.adapter = adapter

        binding.fabAddAgent.setOnClickListener {
            // Open AgentSettingsActivity in "new" mode (no agentId extra)
            val intent = Intent(this, AgentSettingsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onAgentClicked(agent: Agent) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("agentId", agent.id)
        startActivity(intent)
    }

    override fun onAgentLongPressed(agent: Agent) {
        val options = arrayOf("Edit", "Delete")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(agent.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(this, AgentSettingsActivity::class.java)
                        intent.putExtra("agentId", agent.id)
                        startActivity(intent)
                    }
                    1 -> {
                        AgentRepository.agents.remove(agent)
                        adapter.notifyDataSetChanged()
                    }
                }
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
    }
}
