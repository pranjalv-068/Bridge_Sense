package com.bridgesense.app.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.bridgesense.app.R
import com.bridgesense.app.ai.YoloxDetector
import com.bridgesense.app.databinding.FragmentCameraBinding
import com.bridgesense.app.network.MockDataProvider
import com.bridgesense.app.ui.BridgeViewModel
import com.bridgesense.app.ui.UploadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BridgeViewModel by activityViewModels()

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    
    private var yoloxDetector: YoloxDetector? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Initialize YOLOX detector
        yoloxDetector = YoloxDetector(requireContext())
        updateModelStatus()

        // Setup UI
        binding.tvSelectedNode.text = "Capturing for Node-${String.format("%02d", viewModel.selectedNodeId.value)}"

        binding.btnCapture.setOnClickListener { takePhoto() }
        
        binding.btnRetake.setOnClickListener {
            viewModel.clearCapture()
            binding.overlayView.clear()
            binding.ivCaptured.visibility = View.GONE
            binding.viewFinder.visibility = View.VISIBLE
            binding.btnCapture.visibility = View.VISIBLE
            binding.layoutPostCapture.visibility = View.GONE
            binding.tvResultSummary.visibility = View.GONE
        }
        
        binding.btnUpload.setOnClickListener {
            val bmp = viewModel.capturedBitmap.value
            val res = viewModel.inferenceResult.value
            if (bmp != null && res != null) {
                viewModel.uploadPhoto(bmp, viewModel.selectedNodeId.value, res.detections)
            }
        }

        // Observe Upload State
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uploadState.collect { state ->
                when (state) {
                    UploadState.Uploading -> {
                        binding.overlayLoading.visibility = View.VISIBLE
                        binding.tvLoadingText.text = getString(R.string.uploading)
                    }
                    UploadState.Success, UploadState.Failed -> {
                        binding.overlayLoading.visibility = View.GONE
                        // After success, user usually stays to look or presses back
                    }
                    else -> binding.overlayLoading.visibility = View.GONE
                }
            }
        }

        // Check Permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        
        // If we navigated here with a pre-existing captured image (e.g. rotated device), restore it
        if (viewModel.capturedBitmap.value != null) {
            showCapturedState()
        }
    }
    
    private fun updateModelStatus() {
        if (yoloxDetector?.isModelLoaded == true && !viewModel.isMockMode) {
            binding.tvModelStatus.text = getString(R.string.camera_yolox_ready)
            binding.tvModelStatus.setTextColor(requireContext().getColor(R.color.status_normal))
        } else {
            binding.tvModelStatus.text = getString(R.string.camera_yolox_mock)
            binding.tvModelStatus.setTextColor(requireContext().getColor(R.color.status_warning))
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch(exc: Exception) {
                Log.e("CameraFragment", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        binding.overlayLoading.visibility = View.VISIBLE
        binding.tvLoadingText.text = getString(R.string.camera_analyzing)

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    
                    // Run inference in background
                    lifecycleScope.launch {
                        runInference(bitmap)
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraFragment", "Photo capture failed: ${exc.message}", exc)
                    binding.overlayLoading.visibility = View.GONE
                    Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    private suspend fun runInference(bitmap: Bitmap) {
        val result = withContext(Dispatchers.Default) {
            if (viewModel.isMockMode || yoloxDetector?.isModelLoaded != true) {
                // Determine mock severity based on selected node status
                val selectedNode = viewModel.nodeStatuses.value.find { it.nodeId == viewModel.selectedNodeId.value }
                val severity = selectedNode?.status ?: com.bridgesense.app.model.NodeState.NORMAL
                MockDataProvider.generateMockDetections(severity, bitmap.width, bitmap.height)
            } else {
                yoloxDetector!!.detect(bitmap)
            }
        }
        
        viewModel.setCapturedBitmap(bitmap)
        viewModel.setInferenceResult(result)
        
        showCapturedState()
    }
    
    private fun showCapturedState() {
        binding.overlayLoading.visibility = View.GONE
        
        val bitmap = viewModel.capturedBitmap.value ?: return
        val result = viewModel.inferenceResult.value ?: return
        
        // Hide preview, show static image
        binding.viewFinder.visibility = View.GONE
        binding.ivCaptured.visibility = View.VISIBLE
        binding.ivCaptured.setImageBitmap(bitmap)
        
        // Draw bounding boxes
        binding.overlayView.setDetections(result.detections, result.isMock)
        
        // Update controls
        binding.btnCapture.visibility = View.GONE
        binding.layoutPostCapture.visibility = View.VISIBLE
        
        // Show summary
        binding.tvResultSummary.visibility = View.VISIBLE
        binding.tvResultSummary.text = result.summaryText
        
        val color = if (result.hasDamage) R.color.status_warning else R.color.status_normal
        binding.tvResultSummary.setTextColor(requireContext().getColor(color))
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes)
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        yoloxDetector?.close()
        _binding = null
    }
}
