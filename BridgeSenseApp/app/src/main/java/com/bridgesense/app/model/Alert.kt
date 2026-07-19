package com.bridgesense.app.model

/**
 * Alert generated when AI PC detects anomaly on a node,
 * or when a node goes offline / has low battery.
 */
data class Alert(
    val id: String,
    val nodeId: Int,
    val nodeName: String,
    val type: AlertType,
    val message: String,
    val severity: AlertSeverity,
    val timestamp: Long = System.currentTimeMillis(),
    var isRead: Boolean = false,
    val sensorValues: Map<String, Float> = emptyMap()
) {
    val timeFormatted: String get() {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000      -> "${diff / 1000}s ago"
            diff < 3_600_000   -> "${diff / 60_000}m ago"
            diff < 86_400_000  -> "${diff / 3_600_000}h ago"
            else               -> "${diff / 86_400_000}d ago"
        }
    }

    /** Short one-line description of the alert type */
    val typeLabel: String get() = when (type) {
        AlertType.VIBRATION_SPIKE      -> "Vibration Spike"
        AlertType.TILT_ANOMALY         -> "Tilt Anomaly"
        AlertType.TEMPERATURE_HIGH     -> "High Temperature"
        AlertType.NODE_OFFLINE         -> "Node Offline"
        AlertType.AI_ANOMALY_DETECTED  -> "AI Anomaly"
        AlertType.BATTERY_LOW          -> "Battery Low"
        AlertType.SIGNAL_LOST          -> "Signal Lost"
    }

    /** Emoji icon for the alert type */
    val typeIcon: String get() = when (type) {
        AlertType.VIBRATION_SPIKE      -> "〰"
        AlertType.TILT_ANOMALY         -> "📐"
        AlertType.TEMPERATURE_HIGH     -> "🌡"
        AlertType.NODE_OFFLINE         -> "⚫"
        AlertType.AI_ANOMALY_DETECTED  -> "🤖"
        AlertType.BATTERY_LOW          -> "🔋"
        AlertType.SIGNAL_LOST          -> "📡"
    }
}

enum class AlertType {
    VIBRATION_SPIKE,
    TILT_ANOMALY,
    TEMPERATURE_HIGH,
    NODE_OFFLINE,
    AI_ANOMALY_DETECTED,
    BATTERY_LOW,
    SIGNAL_LOST
}

enum class AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}
