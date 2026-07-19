package com.bridgesense.app.network

import android.graphics.RectF
import com.bridgesense.app.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import kotlin.math.sin
import kotlin.random.Random

/**
 * MockDataProvider
 *
 * Generates realistic simulated sensor data for 2 bridge nodes.
 * Used when Mock Mode is enabled in Settings, or when no AI PC is available.
 *
 * ─────────────────────────────────────────────────────────
 * TO SWITCH TO LIVE DATA:
 *   1. Disable Mock Mode in Settings screen
 *   2. Enter your AI PC's IP address
 *   3. Ensure the Python server (server.py) is running
 *   4. WebSocketManager.kt will take over the data stream
 * ─────────────────────────────────────────────────────────
 */
object MockDataProvider {

    // Node names for the 2-node demo bridge
    private val NODE_NAMES = mapOf(
        1 to "Node-01 (Mid-Span)",
        2 to "Node-02 (Support)"
    )

    // Cycle counter to simulate periodic anomalies for the demo
    private var cycleCount = 0

    // ─── Node Status Stream ──────────────────────────────────────────────────

    /**
     * Returns a Flow that emits updated NodeStatus for all nodes every 2 seconds.
     * Simulates a realistic mix of NORMAL, WARNING, and occasional CRITICAL states.
     */
    fun nodeStatusFlow(): Flow<List<NodeStatus>> = flow {
        while (true) {
            cycleCount++
            emit(generateNodeStatuses())
            delay(2000L) // Update every 2 seconds
        }
    }

    private fun generateNodeStatuses(): List<NodeStatus> {
        return listOf(
            generateNode(1),
            generateNode(2)
        )
    }

    private fun generateNode(nodeId: Int): NodeStatus {
        // Use sine waves + noise to simulate realistic sensor readings
        val t = cycleCount.toDouble() / 10.0
        val baseVibration = 0.5f + (sin(t + nodeId).toFloat() * 0.3f).coerceAtLeast(0f)
        val noise = Random.nextFloat() * 0.3f

        // Every 30 cycles, simulate a warning spike on Node 1 (for demo purposes)
        val spikeMultiplier = if (nodeId == 1 && cycleCount % 30 in 25..29) 3.5f else 1.0f

        val vibration = (baseVibration + noise) * spikeMultiplier
        val tilt = 0.2f + (sin(t * 0.7 + nodeId * 1.2).toFloat().coerceAtLeast(0f) * 0.5f) +
                   (if (nodeId == 1 && cycleCount % 30 in 25..29) 1.8f else 0f)
        val temperature = 28f + sin(t * 0.3).toFloat() * 5f + Random.nextFloat() * 2f
        val battery = (95 - (cycleCount * 0.02)).toInt().coerceIn(20, 100)
        val signal = (88 + sin(t).toFloat() * 8).toInt().coerceIn(50, 100)

        // Determine state based on thresholds
        val state = when {
            vibration > 4.0f || tilt > 3.0f -> NodeState.CRITICAL
            vibration > 2.0f || tilt > 1.5f  -> NodeState.WARNING
            battery < 25                      -> NodeState.WARNING
            else                              -> NodeState.NORMAL
        }

        // AI prediction label (simulates ONNX model output from AI PC)
        val (aiLabel, aiConf) = when (state) {
            NodeState.CRITICAL -> Pair("Structural Anomaly", 0.87f + Random.nextFloat() * 0.1f)
            NodeState.WARNING  -> Pair("Elevated Stress", 0.63f + Random.nextFloat() * 0.15f)
            NodeState.NORMAL   -> Pair("Stable", 0.91f + Random.nextFloat() * 0.08f)
            NodeState.OFFLINE  -> Pair("N/A", 0f)
        }

        return NodeStatus(
            nodeId = nodeId,
            nodeName = NODE_NAMES[nodeId] ?: "Node-$nodeId",
            status = state,
            vibration = vibration,
            tilt = tilt,
            temperature = temperature,
            batteryLevel = battery,
            signalStrength = signal,
            aiConfidence = aiConf,
            predictionLabel = aiLabel
        )
    }

    // ─── Alert Generation ────────────────────────────────────────────────────

    /**
     * Returns a Flow that emits a new Alert when a critical/warning event occurs.
     * In mock mode this is checked every 5 seconds against the current node states.
     */
    fun alertFlow(): Flow<Alert> = flow {
        val recentlyAlerted = mutableSetOf<String>() // Prevent duplicate alerts
        while (true) {
            delay(5000L)
            val nodes = generateNodeStatuses()
            for (node in nodes) {
                if (node.status == NodeState.CRITICAL) {
                    val alertKey = "${node.nodeId}_${node.status}_${cycleCount / 5}"
                    if (alertKey !in recentlyAlerted) {
                        recentlyAlerted.add(alertKey)
                        if (recentlyAlerted.size > 20) recentlyAlerted.clear()
                        emit(generateCriticalAlert(node))
                    }
                }
            }
        }
    }

    private fun generateCriticalAlert(node: NodeStatus): Alert {
        val type = when {
            node.vibration > 4.0f -> AlertType.VIBRATION_SPIKE
            node.tilt > 3.0f      -> AlertType.TILT_ANOMALY
            else                  -> AlertType.AI_ANOMALY_DETECTED
        }
        val message = when (type) {
            AlertType.VIBRATION_SPIKE     -> "Vibration %.1f m/s² detected at ${node.nodeName}. Threshold: 4.0 m/s²".format(node.vibration)
            AlertType.TILT_ANOMALY        -> "Tilt angle %.1f° exceeds limit at ${node.nodeName}. Threshold: 3.0°".format(node.tilt)
            AlertType.AI_ANOMALY_DETECTED -> "AI model detected structural anomaly at ${node.nodeName} (${(node.aiConfidence * 100).toInt()}% confidence)"
            else                          -> "Anomaly detected at ${node.nodeName}"
        }
        return Alert(
            id = UUID.randomUUID().toString(),
            nodeId = node.nodeId,
            nodeName = node.nodeName,
            type = type,
            message = message,
            severity = AlertSeverity.CRITICAL,
            sensorValues = mapOf(
                "vibration"   to node.vibration,
                "tilt"        to node.tilt,
                "temperature" to node.temperature
            )
        )
    }

    // ─── Mock YOLOX Detections ───────────────────────────────────────────────

    /** Bridge-specific damage labels YOLOX would detect in real mode */
    private val BRIDGE_DAMAGE_LABELS = listOf(
        "Crack",
        "Surface Crack",
        "Corrosion",
        "Spalling",
        "Deformation",
        "Efflorescence",
        "Exposed Rebar"
    )

    /**
     * Generates mock YOLOX detections for a captured image.
     * In real mode, YoloxDetector.kt would produce these from the TFLite model.
     * @param severity NodeState of the selected node (influences mock severity)
     */
    fun generateMockDetections(severity: NodeState, imageWidth: Int, imageHeight: Int): InferenceResult {
        val detections = mutableListOf<Detection>()

        val numDetections = when (severity) {
            NodeState.CRITICAL -> Random.nextInt(2, 4)
            NodeState.WARNING  -> Random.nextInt(1, 3)
            NodeState.NORMAL   -> if (Random.nextFloat() > 0.6f) 1 else 0
            NodeState.OFFLINE  -> 0
        }

        repeat(numDetections) {
            val label = BRIDGE_DAMAGE_LABELS.random()
            val confidence = when (severity) {
                NodeState.CRITICAL -> 0.65f + Random.nextFloat() * 0.30f
                NodeState.WARNING  -> 0.45f + Random.nextFloat() * 0.30f
                NodeState.NORMAL   -> 0.35f + Random.nextFloat() * 0.20f
                NodeState.OFFLINE  -> 0f
            }

            // Random bounding box in image (normalized 0–1 coords)
            val left   = Random.nextFloat() * 0.6f
            val top    = Random.nextFloat() * 0.6f
            val right  = (left + 0.15f + Random.nextFloat() * 0.25f).coerceAtMost(1.0f)
            val bottom = (top + 0.10f + Random.nextFloat() * 0.20f).coerceAtMost(1.0f)

            detections.add(
                Detection(
                    label = label,
                    confidence = confidence,
                    boundingBox = RectF(left, top, right, bottom),
                    isBridgeDamage = true
                )
            )
        }

        return InferenceResult(
            detections = detections,
            inferenceTimeMs = 0L,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            isMock = true
        )
    }
}
