package com.example.agentapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.agentapp.databinding.ItemAgentBinding
import com.example.agentapp.Agent

class AgentListAdapter(
    private val agents: List<Agent>,
    private val listener: AgentItemListener
) : RecyclerView.Adapter<AgentListAdapter.AgentViewHolder>() {

    interface AgentItemListener {
        fun onAgentClicked(agent: Agent)
        fun onAgentLongPressed(agent: Agent)
    }

    inner class AgentViewHolder(val binding: ItemAgentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(agent: Agent) {
            binding.agentNameTextView.text = agent.name

            // Set the status icon based on agent.status
            val statusDrawable = when (agent.status?.state) {
                "running", "idle", "finished" -> R.drawable.online_green
                "error", "waiting_for_user" -> R.drawable.need_h_red
                else -> R.drawable.offline_grey
            }
            binding.root.findViewById<android.widget.ImageView>(R.id.statusIcon)
                .setImageResource(statusDrawable)

            binding.root.setOnClickListener { listener.onAgentClicked(agent) }
            binding.root.setOnLongClickListener {
                listener.onAgentLongPressed(agent)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AgentViewHolder {
        val binding = ItemAgentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AgentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AgentViewHolder, position: Int) {
        holder.bind(agents[position])
    }

    override fun getItemCount(): Int = agents.size
}
