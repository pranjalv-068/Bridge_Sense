package com.bridgesense.app.ui.alerts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bridgesense.app.databinding.FragmentAlertsBinding
import com.bridgesense.app.ui.BridgeViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BridgeViewModel by activityViewModels()
    
    private lateinit var adapter: AlertAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AlertAdapter()
        binding.rvAlerts.adapter = adapter

        binding.tvClearAll.setOnClickListener {
            viewModel.clearAlerts()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.alerts.collectLatest { alerts ->
                    adapter.submitList(alerts)
                    
                    if (alerts.isEmpty()) {
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.rvAlerts.visibility = View.GONE
                        binding.tvClearAll.visibility = View.GONE
                    } else {
                        binding.tvEmptyState.visibility = View.GONE
                        binding.rvAlerts.visibility = View.VISIBLE
                        binding.tvClearAll.visibility = View.VISIBLE
                    }
                    
                    // Mark as read when viewing the screen
                    viewModel.markAllAlertsRead()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
