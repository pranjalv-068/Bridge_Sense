package com.bridgesense.app.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bridgesense.app.model.*
import com.bridgesense.app.network.ConnectionState
import com.bridgesense.app.network.MockDataProvider
import com.bridgesense.app.network.WebSocketManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * BridgeViewModel
 *
 * Shared ViewModel for all fragments. Holds:
 * - Node status list (2 nodes)
 * - Alert list
 * - Last captured image + YOLOX detections
 * - Connection state
 * - Mock mode flag
 *
 * Handles switching between Mock Mode and Live Mode automatically.
 */
class BridgeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "BridgeSense_VM"
        private const val PREFS_NAME = "bridgesense_prefs"
        private const val KEY_IP = "ai_pc_ip"
        private const val KEY_PORT = "ai_pc_port"
        private const val KEY_MOCK_MODE = "mock_mode"
        private const val KEY_ENGINEER_NAME = "engineer_name"
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val wsManager = WebSocketManager()

    // ─── Settings ─────────────────────────────────────────────────────────

    var aiPcIp: String
        get() = prefs.getString(KEY_IP, "192.168.1.100") ?: "192.168.1.100"
        set(v) { prefs.edit().putString(KEY_IP, v).apply() }

    var aiPcPort: Int
        get() = prefs.getInt(KEY_PORT, 8080)
        set(v) { prefs.edit().putInt(KEY_PORT, v).apply() }

    var isMockMode: Boolean
        get() = prefs.getBoolean(KEY_MOCK_MODE, true) // Default: mock mode ON
        set(v) {
            prefs.edit().putBoolean(KEY_MOCK_MODE, v).apply()
            if (v) startMockMode() else startLiveMode()
        }

    var engineerName: String
        get() = prefs.getString(KEY_ENGINEER_NAME, "Engineer") ?: "Engineer"
        set(v) { prefs.edit().putString(KEY_ENGINEER_NAME, v).apply() }

    // ─── Node Status ───────────────────────────────────────────────────────

    private val _nodeStatuses = MutableStateFlow<List<NodeStatus>>(emptyList())
    val nodeStatuses: StateFlow<List<NodeStatus>> = _nodeStatuses.asStateFlow()

    val bridgeHealthPercent: Int get() {
        val nodes = _nodeStatuses.value
        if (nodes.isEmpty()) return 0
        val score = nodes.map { node ->
            when (node.status) {
                NodeState.NORMAL   -> 100
                NodeState.WARNING  -> 60
                NodeState.CRITICAL -> 20
                NodeState.OFFLINE  -> 0
            }
        }.average().toInt()
        return score
    }

    val activeNodeCount: Int get() =
        _nodeStatuses.value.count { it.status != NodeState.OFFLINE }

    // ─── Alerts ───────────────────────────────────────────────────────────

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    val unreadAlertCount: Int get() = _alerts.value.count { !it.isRead }

    // ─── Camera / YOLOX state ──────────────────────────────────────────────

    private val _inferenceResult = MutableStateFlow<InferenceResult?>(null)
    val inferenceResult: StateFlow<InferenceResult?> = _inferenceResult.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    private val _selectedNodeId = MutableStateFlow(1)
    val selectedNodeId: StateFlow<Int> = _selectedNodeId.asStateFlow()

    // ─── Upload state ──────────────────────────────────────────────────────

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    // ─── Connection ────────────────────────────────────────────────────────

    val connectionState: StateFlow<ConnectionState> = wsManager.connectionState

    // ─── Toast / Snackbar events ───────────────────────────────────────────

    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    // ─── Private ───────────────────────────────────────────────────────────

    private var mockJob: Job? = null
    private var liveJob: Job? = null

    // ─── Init ──────────────────────────────────────────────────────────────

    init {
        if (isMockMode) startMockMode() else startLiveMode()
    }

    // ─── Mock Mode ─────────────────────────────────────────────────────────

    private fun startMockMode() {
        Log.d(TAG, "Starting MOCK MODE")
        liveJob?.cancel()
        wsManager.disconnect()

        mockJob?.cancel()
        mockJob = viewModelScope.launch {
            launch {
                MockDataProvider.nodeStatusFlow().collect { nodes ->
                    _nodeStatuses.value = nodes
                }
            }
            launch {
                MockDataProvider.alertFlow().collect { alert ->
                    _alerts.value = listOf(alert) + _alerts.value
                }
            }
        }
    }

    // ─── Live Mode ─────────────────────────────────────────────────────────

    private fun startLiveMode() {
        Log.d(TAG, "Starting LIVE MODE → ws://$aiPcIp:$aiPcPort")
        mockJob?.cancel()

        wsManager.connect(aiPcIp, aiPcPort)

        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            launch {
                wsManager.nodeUpdates.collect { nodes ->
                    _nodeStatuses.value = nodes
                }
            }
            launch {
                wsManager.alerts.collect { alert ->
                    _alerts.value = listOf(alert) + _alerts.value
                }
            }
        }
    }

    // ─── Public actions ────────────────────────────────────────────────────

    fun selectNode(nodeId: Int) { _selectedNodeId.value = nodeId }

    fun setInferenceResult(result: InferenceResult) { _inferenceResult.value = result }
    fun setCapturedBitmap(bitmap: Bitmap?) { _capturedBitmap.value = bitmap }
    fun clearCapture() {
        _capturedBitmap.value = null
        _inferenceResult.value = null
        _uploadState.value = UploadState.Idle
    }

    fun markAllAlertsRead() {
        _alerts.value = _alerts.value.map { it.copy(isRead = true) }
    }

    fun clearAlerts() { _alerts.value = emptyList() }

    /** Upload captured photo + YOLOX detections to AI PC */
    fun uploadPhoto(bitmap: Bitmap, nodeId: Int, detections: List<com.bridgesense.app.model.Detection>) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            try {
                // Save bitmap to temp file
                val tempFile = File(getApplication<Application>().cacheDir, "capture_node${nodeId}.jpg")
                FileOutputStream(tempFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                val success = wsManager.uploadPhoto(tempFile, nodeId, detections)
                _uploadState.value = if (success) UploadState.Success else UploadState.Failed
                _uiMessage.emit(if (success) "Photo uploaded to AI PC ✓" else "Upload failed — check connection")
            } catch (e: Exception) {
                Log.e(TAG, "Upload error: ${e.message}")
                _uploadState.value = UploadState.Failed
                _uiMessage.emit("Upload error: ${e.message}")
            }
        }
    }

    fun testConnection(ip: String, port: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = wsManager.testConnection(ip, port)
            onResult(ok)
        }
    }

    fun showMessage(msg: String) {
        viewModelScope.launch { _uiMessage.emit(msg) }
    }

    override fun onCleared() {
        super.onCleared()
        mockJob?.cancel()
        liveJob?.cancel()
        wsManager.disconnect()
    }
}

sealed class UploadState {
    object Idle      : UploadState()
    object Uploading : UploadState()
    object Success   : UploadState()
    object Failed    : UploadState()
}
