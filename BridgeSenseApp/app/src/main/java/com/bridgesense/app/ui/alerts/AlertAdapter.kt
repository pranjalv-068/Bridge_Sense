package com.bridgesense.app.ui.alerts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bridgesense.app.R
import com.bridgesense.app.databinding.ItemAlertBinding
import com.bridgesense.app.model.Alert
import com.bridgesense.app.model.AlertSeverity

class AlertAdapter : ListAdapter<Alert, AlertAdapter.AlertViewHolder>(AlertDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemAlertBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AlertViewHolder(private val binding: ItemAlertBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(alert: Alert) {
            binding.tvAlertTitle.text = alert.typeLabel
            binding.tvAlertMessage.text = alert.message
            binding.tvAlertTime.text = alert.timeFormatted
            binding.tvNodeTag.text = alert.nodeName
            binding.tvAlertIcon.text = alert.typeIcon

            val context = binding.root.context
            val color = when (alert.severity) {
                AlertSeverity.CRITICAL -> context.getColor(R.color.status_critical)
                AlertSeverity.WARNING -> context.getColor(R.color.status_warning)
                AlertSeverity.INFO -> context.getColor(R.color.accent_cyan)
            }
            binding.tvAlertTitle.setTextColor(color)
            binding.tvAlertIcon.setTextColor(color)
        }
    }

    class AlertDiffCallback : DiffUtil.ItemCallback<Alert>() {
        override fun areItemsTheSame(oldItem: Alert, newItem: Alert) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Alert, newItem: Alert) = oldItem == newItem
    }
}
