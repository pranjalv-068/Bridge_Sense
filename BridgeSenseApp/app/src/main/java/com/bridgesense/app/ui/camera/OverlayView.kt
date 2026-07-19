package com.bridgesense.app.ui.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.bridgesense.app.model.Detection

/**
 * OverlayView
 * 
 * Draws bounding boxes and labels for YOLOX detections on top of the camera preview
 * or the captured image.
 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val detections = mutableListOf<Detection>()
    private var isMock = false

    // Paint for the bounding box
    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Paint for the text background
    private val textBackgroundPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    // Paint for the text label
    private val textPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textSize = 40f
        isAntiAlias = true
    }

    fun setDetections(newDetections: List<Detection>, mock: Boolean) {
        detections.clear()
        detections.addAll(newDetections)
        isMock = mock
        invalidate() // Trigger redraw
    }

    fun clear() {
        detections.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (detections.isEmpty()) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        for (detection in detections) {
            // Set colors based on whether it's bridge damage
            if (detection.isBridgeDamage) {
                boxPaint.color = Color.parseColor("#FF1744") // Critical Red
                textBackgroundPaint.color = Color.parseColor("#FF1744")
            } else {
                boxPaint.color = Color.parseColor("#00D4FF") // Accent Cyan
                textBackgroundPaint.color = Color.parseColor("#00D4FF")
            }

            // Detections have normalized coordinates (0.0 to 1.0)
            val rect = RectF(
                detection.boundingBox.left * viewWidth,
                detection.boundingBox.top * viewHeight,
                detection.boundingBox.right * viewWidth,
                detection.boundingBox.bottom * viewHeight
            )

            // Draw bounding box
            canvas.drawRect(rect, boxPaint)

            // Draw label
            val labelText = detection.labelDisplay
            val textWidth = textPaint.measureText(labelText)
            val textHeight = textPaint.textSize

            val textBgRect = RectF(
                rect.left,
                rect.top - textHeight - 16f,
                rect.left + textWidth + 16f,
                rect.top
            )
            
            // Prevent text from drawing off-screen at the top
            if (textBgRect.top < 0) {
                textBgRect.offsetTo(rect.left, rect.bottom)
            }

            canvas.drawRect(textBgRect, textBackgroundPaint)
            canvas.drawText(
                labelText,
                textBgRect.left + 8f,
                textBgRect.bottom - 8f,
                textPaint
            )
        }
    }
}
