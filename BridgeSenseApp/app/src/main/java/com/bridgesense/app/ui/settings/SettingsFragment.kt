package com.bridgesense.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.bridgesense.app.R
import com.bridgesense.app.databinding.FragmentSettingsBinding
import com.bridgesense.app.ui.BridgeViewModel
import java.io.File

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BridgeViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Populate initial values
        binding.etIp.setText(viewModel.aiPcIp)
        binding.etPort.setText(viewModel.aiPcPort.toString())
        binding.switchMockMode.isChecked = viewModel.isMockMode

        // Save IP on focus lost or enter
        binding.etIp.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val ip = binding.etIp.text.toString()
                if (ip.isNotEmpty()) viewModel.aiPcIp = ip
            }
        }
        
        binding.etPort.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val portStr = binding.etPort.text.toString()
                if (portStr.isNotEmpty()) viewModel.aiPcPort = portStr.toIntOrNull() ?: 8080
            }
        }

        binding.switchMockMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.isMockMode = isChecked
            viewModel.showMessage(if (isChecked) "Mock Mode Enabled" else "Live Mode Enabled")
        }

        binding.btnTestConnection.setOnClickListener {
            val ip = binding.etIp.text.toString()
            val port = binding.etPort.text.toString().toIntOrNull() ?: 8080
            viewModel.aiPcIp = ip
            viewModel.aiPcPort = port
            
            viewModel.showMessage("Testing connection to $ip:$port...")
            viewModel.testConnection(ip, port) { success ->
                if (success) {
                    viewModel.showMessage(getString(R.string.connection_success))
                } else {
                    viewModel.showMessage(getString(R.string.connection_failed))
                }
            }
        }

        checkYoloxStatus()
    }
    
    private fun checkYoloxStatus() {
        try {
            val hasModel = requireContext().assets.list("")?.contains("yolox_small.tflite") == true
            if (hasModel) {
                binding.tvYoloxStatus.text = getString(R.string.settings_model_loaded)
                binding.tvYoloxStatus.setTextColor(requireContext().getColor(R.color.status_normal))
            } else {
                binding.tvYoloxStatus.text = getString(R.string.settings_model_missing)
                binding.tvYoloxStatus.setTextColor(requireContext().getColor(R.color.status_warning))
            }
        } catch (e: Exception) {
            binding.tvYoloxStatus.text = "Error checking model status"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        // Save just in case
        val ip = binding.etIp.text.toString()
        if (ip.isNotEmpty()) viewModel.aiPcIp = ip
        val portStr = binding.etPort.text.toString()
        if (portStr.isNotEmpty()) viewModel.aiPcPort = portStr.toIntOrNull() ?: 8080
        
        _binding = null
    }
}
