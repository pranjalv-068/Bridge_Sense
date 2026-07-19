package com.bridgesense.app.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bridgesense.app.R
import com.bridgesense.app.databinding.ItemNodeBinding
import com.bridgesense.app.model.NodeState
import com.bridgesense.app.model.NodeStatus

class NodeAdapter(
    private val onCaptureClick: (Int) -> Unit
) : ListAdapter<NodeStatus, NodeAdapter.NodeViewHolder>(NodeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val binding = ItemNodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NodeViewHolder(binding, onCaptureClick)
    }

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NodeViewHolder(
        private val binding: ItemNodeBinding,
        private val onCaptureClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(node: NodeStatus) {
            binding.tvNodeName.text = node.nodeName
            binding.tvNodeStatus.text = node.status.name
            
            // Format sensor values
            binding.tvVibration.text = String.format("%.1f", node.vibration)
            binding.tvTilt.text = String.format("%.1f", node.tilt)
            binding.tvTemp.text = String.format("%.1f", node.temperature)
            
            // Format AI prediction
            val confPct = (node.aiConfidence * 100).toInt()
            binding.tvAiLabel.text = "${node.predictionLabel} (${confPct}%)"

            val context = binding.root.context
            
            // Update colors based on status
            val statusColor = when (node.status) {
                NodeState.NORMAL -> context.getColor(R.color.status_normal)
                NodeState.WARNING -> context.getColor(R.color.status_warning)
                NodeState.CRITICAL -> context.getColor(R.color.status_critical)
                NodeState.OFFLINE -> context.getColor(R.color.status_offline)
            }
            binding.tvNodeStatus.setTextColor(statusColor)
            
            if (node.status == NodeState.CRITICAL || node.status == NodeState.WARNING) {
                binding.tvAiLabel.setTextColor(context.getColor(R.color.status_warning))
            } else {
                binding.tvAiLabel.setTextColor(context.getColor(R.color.accent_cyan))
            }

            binding.btnCapture.setOnClickListener {
                onCaptureClick(node.nodeId)
            }
        }
    }

    class NodeDiffCallback : DiffUtil.ItemCallback<NodeStatus>() {
        override fun areItemsTheSame(oldItem: NodeStatus, newItem: NodeStatus) = oldItem.nodeId == newItem.nodeId
        override fun areContentsTheSame(oldItem: NodeStatus, newItem: NodeStatus) = oldItem == newItem
    }
}
