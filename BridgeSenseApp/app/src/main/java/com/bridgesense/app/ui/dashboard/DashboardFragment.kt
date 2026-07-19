package com.bridgesense.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bridgesense.app.R
import com.bridgesense.app.databinding.FragmentDashboardBinding
import com.bridgesense.app.ui.BridgeViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BridgeViewModel by activityViewModels()

    private lateinit var adapter: NodeAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = NodeAdapter { clickedNodeId ->
            // On Capture button clicked for a node
            viewModel.selectNode(clickedNodeId)
            findNavController().navigate(R.id.cameraFragment)
        }
        binding.rvNodes.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe node statuses
                launch {
                    viewModel.nodeStatuses.collectLatest { nodes ->
                        adapter.submitList(nodes)
                        
                        // Update Health Score
                        val score = viewModel.bridgeHealthPercent
                        binding.tvHealthScore.text = "$score%"
                        
                        val scoreColor = when {
                            score >= 90 -> R.color.status_normal
                            score >= 50 -> R.color.status_warning
                            else -> R.color.status_critical
                        }
                        binding.tvHealthScore.setTextColor(requireContext().getColor(scoreColor))
                    }
                }

                // Observe Mock Mode state to show/hide badge
                launch {
                    val isMock = viewModel.isMockMode
                    binding.tvMockBadge.visibility = if (isMock) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
