package com.bridgesense.app.network

import android.util.Log
import com.bridgesense.app.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * WebSocketManager
 *
 * Manages real-time WebSocket connection to the AI PC (Python server.py).
 * Receives JSON messages for node status updates and alerts.
 * Also handles HTTP photo uploads.
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │  Message format (AI PC → App):                             │
 * │  { "type": "node_update", "node_id": 1, "status": "...", ...} │
 * │  { "type": "alert", "node_id": 1, "alert_type": "...", ...}   │
 * │                                                             │
 * │  Message format (App → AI PC):                             │
 * │  { "type": "photo_uploaded", "node_id": 1, "detections": [...]} │
 * └─────────────────────────────────────────────────────────────┘
 */
class WebSocketManager {

    companion object {
        private const val TAG = "BridgeSense_WS"
        private const val RECONNECT_DELAY_MS = 3000L
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No timeout for WebSocket
        .build()

    private var webSocket: WebSocket? = null
    private var ipAddress: String = ""
    private var port: Int = 8080

    // ─── Connection State ────────────────────────────────────────────────────

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // ─── Incoming Data Flows ─────────────────────────────────────────────────

    private val _nodeUpdates = MutableSharedFlow<List<NodeStatus>>()
    val nodeUpdates: SharedFlow<List<NodeStatus>> = _nodeUpdates.asSharedFlow()

    private val _alerts = MutableSharedFlow<Alert>()
    val alerts: SharedFlow<Alert> = _alerts.asSharedFlow()

    // ─── Connect / Disconnect ────────────────────────────────────────────────

    fun connect(ip: String, port: Int) {
        this.ipAddress = ip
        this.port = port
        openWebSocket()
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun openWebSocket() {
        _connectionState.value = ConnectionState.CONNECTING
        val url = "ws://$ipAddress:$port/ws"
        Log.d(TAG, "Connecting to: $url")

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                _connectionState.value = ConnectionState.CONNECTED
                // Identify ourselves to the server
                webSocket.send(gson.toJson(mapOf(
                    "type" to "handshake",
                    "client" to "BridgeSense-Android",
                    "version" to "1.0"
                )))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Message received: $text")
                parseMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $reason")
                webSocket.close(1000, null)
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}")
                _connectionState.value = ConnectionState.DISCONNECTED
                // Auto-reconnect after delay
                CoroutineScope(Dispatchers.IO).launch {
                    delay(RECONNECT_DELAY_MS)
                    if (_connectionState.value == ConnectionState.DISCONNECTED) {
                        openWebSocket()
                    }
                }
            }
        })
    }

    // ─── Message Parsing ─────────────────────────────────────────────────────

    private fun parseMessage(json: String) {
        try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val type = obj.get("type")?.asString ?: return

            CoroutineScope(Dispatchers.Default).launch {
                when (type) {
                    "node_update" -> parseNodeUpdate(obj)
                    "alert"       -> parseAlert(obj)
                    "ping"        -> webSocket?.send("""{"type":"pong"}""")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: ${e.message}")
        }
    }

    private suspend fun parseNodeUpdate(obj: JsonObject) {
        try {
            // ── Live message format from server.py ──
            // {"type":"node_update","nodes":[{"node_id":1,"status":"normal","vibration":1.2,...},...]}
            val nodesArray = obj.getAsJsonArray("nodes") ?: return
            val nodeStatuses = nodesArray.mapNotNull { elem ->
                try {
                    val n = elem.asJsonObject
                    val stateStr = n.get("status")?.asString?.uppercase() ?: "NORMAL"
                    NodeStatus(
                        nodeId = n.get("node_id")?.asInt ?: 0,
                        nodeName = n.get("node_name")?.asString ?: "Node-?",
                        status = NodeState.valueOf(stateStr),
                        vibration = n.get("vibration")?.asFloat ?: 0f,
                        tilt = n.get("tilt")?.asFloat ?: 0f,
                        temperature = n.get("temperature")?.asFloat ?: 0f,
                        batteryLevel = n.get("battery")?.asInt ?: 100,
                        signalStrength = n.get("signal")?.asInt ?: 100,
                        aiConfidence = n.get("ai_confidence")?.asFloat ?: 0f,
                        predictionLabel = n.get("ai_label")?.asString ?: "Stable"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing node: ${e.message}")
                    null
                }
            }
            if (nodeStatuses.isNotEmpty()) {
                _nodeUpdates.emit(nodeStatuses)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing node_update: ${e.message}")
        }
    }

    private suspend fun parseAlert(obj: JsonObject) {
        try {
            val alertTypeStr = obj.get("alert_type")?.asString?.uppercase() ?: "AI_ANOMALY_DETECTED"
            val severityStr  = obj.get("severity")?.asString?.uppercase() ?: "WARNING"

            val alert = Alert(
                id = obj.get("id")?.asString ?: System.currentTimeMillis().toString(),
                nodeId = obj.get("node_id")?.asInt ?: 0,
                nodeName = obj.get("node_name")?.asString ?: "Node-?",
                type = try { AlertType.valueOf(alertTypeStr) } catch (e: Exception) { AlertType.AI_ANOMALY_DETECTED },
                message = obj.get("message")?.asString ?: "Anomaly detected",
                severity = try { AlertSeverity.valueOf(severityStr) } catch (e: Exception) { AlertSeverity.WARNING }
            )
            _alerts.emit(alert)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing alert: ${e.message}")
        }
    }

    // ─── Send Message ────────────────────────────────────────────────────────

    fun sendMessage(json: String): Boolean {
        return webSocket?.send(json) ?: false
    }

    // ─── HTTP Photo Upload ───────────────────────────────────────────────────

    /**
     * Uploads annotated photo + YOLOX detections to the AI PC via HTTP POST.
     * Endpoint: http://<ip>:<port>/upload
     *
     * @return true on success
     */
    suspend fun uploadPhoto(
        imageFile: File,
        nodeId: Int,
        detections: List<com.bridgesense.app.model.Detection>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val detectionsJson = gson.toJson(detections.map { d ->
                mapOf(
                    "label"      to d.label,
                    "confidence" to d.confidence,
                    "bbox_left"  to d.boundingBox.left,
                    "bbox_top"   to d.boundingBox.top,
                    "bbox_right" to d.boundingBox.right,
                    "bbox_bottom" to d.boundingBox.bottom
                )
            })

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("node_id", nodeId.toString())
                .addFormDataPart("detections", detectionsJson)
                .addFormDataPart(
                    "image", imageFile.name,
                    imageFile.asRequestBody("image/jpeg".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("http://$ipAddress:$port/upload")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            response.close()

            // Notify AI PC that detections have been uploaded
            if (success) {
                sendMessage(gson.toJson(mapOf(
                    "type"       to "photo_uploaded",
                    "node_id"    to nodeId,
                    "num_detections" to detections.size
                )))
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: ${e.message}")
            false
        }
    }

    // ─── Connection Test ─────────────────────────────────────────────────────

    /**
     * Tests connection to AI PC by attempting a short HTTP GET request.
     * Used by SettingsFragment "Test Connection" button.
     */
    suspend fun testConnection(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$ip:$port/ping")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val ok = response.isSuccessful
            response.close()
            ok
        } catch (e: Exception) {
            false
        }
    }
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}
