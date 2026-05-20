package com.compvision.crowdvision

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class ObjectDetectionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val tracks = mutableListOf<Track>()
    private var imageWidth = 1
    private var imageHeight = 1

    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val textBgPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        alpha = 180
    }

    fun setTracks(newTracks: List<Track>, imgWidth: Int, imgHeight: Int) {
        tracks.clear()
        tracks.addAll(newTracks)
        imageWidth = imgWidth
        imageHeight = imgHeight
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (tracks.isEmpty() || imageWidth == 0 || imageHeight == 0) return

        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight

        for (track in tracks) {
            val displayId = track.displayId ?: continue

            val box = track.bbox
            val scaledBox = RectF(
                box.left * scaleX,
                box.top * scaleY,
                box.right * scaleX,
                box.bottom * scaleY
            )

            canvas.drawRect(scaledBox, boxPaint)

            val label = "ID $displayId"

            val textBounds = Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)

            val textX = scaledBox.left
            val textY = (scaledBox.top - 12f).coerceAtLeast(textBounds.height().toFloat() + 8f)

            val bgRect = RectF(
                textX - 10,
                textY - textBounds.height() - 10,
                textX + textBounds.width() + 10,
                textY + 10
            )

            canvas.drawRect(bgRect, textBgPaint)
            canvas.drawText(label, textX, textY, textPaint)
        }
    }
}