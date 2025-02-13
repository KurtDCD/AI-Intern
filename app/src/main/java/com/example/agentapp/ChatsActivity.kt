package com.example.agentapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.agentapp.databinding.ActivityChatsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ChatsActivity : AppCompatActivity(), AgentListAdapter.AgentItemListener {

    private lateinit var binding: ActivityChatsBinding
    private lateinit var adapter: AgentListAdapter
    private val pollHandler = Handler(Looper.getMainLooper())
    private val pollInterval = 10000L // Poll every 10 seconds (adjust as needed)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Use the full Agent list from the repository.
        adapter = AgentListAdapter(AgentRepository.agents, this)
        binding.agentsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.agentsRecyclerView.adapter = adapter

        binding.fabAddAgent.setOnClickListener {
            // Open AgentSettingsActivity in "new" mode (no agentId extra)
            val intent = Intent(this, AgentSettingsActivity::class.java)
            startActivity(intent)
        }

        // Start polling for status updates
        startStatusPolling()
    }

    private fun startStatusPolling() {
        pollHandler.post(object : Runnable {
            override fun run() {
                AgentRepository.agents.forEachIndexed { index, agent ->
                    val baseUrl = "http://${agent.serverIp}:${agent.serverPort}/"
                    val retrofit = retrofit2.Retrofit.Builder()
                        .baseUrl(baseUrl)
                        .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                        .build()
                    val api = retrofit.create(AgentApi::class.java)
                    api.getStatus().enqueue(object : Callback<AgentStatus> {
                        override fun onResponse(call: Call<AgentStatus>, response: Response<AgentStatus>) {
                            if (response.isSuccessful) {
                                agent.status = response.body()
                                adapter.notifyItemChanged(index)
                            }
                        }
                        override fun onFailure(call: Call<AgentStatus>, t: Throwable) {
                            // Set status to null (offline) on failure.
                            agent.status = null
                            adapter.notifyItemChanged(index)
                        }
                    })
                }
                pollHandler.postDelayed(this, pollInterval)
            }
        })
    }

    override fun onAgentClicked(agent: Agent) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("agentId", agent.id)
        startActivity(intent)
    }

    override fun onAgentLongPressed(agent: Agent) {
        val options = arrayOf("Edit", "Delete")
        MaterialAlertDialogBuilder(this)
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

    override fun onDestroy() {
        super.onDestroy()
        pollHandler.removeCallbacksAndMessages(null)
    }
}
