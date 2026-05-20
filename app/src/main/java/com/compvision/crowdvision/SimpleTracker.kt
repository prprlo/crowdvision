package com.compvision.crowdvision

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

class SimpleTracker(
    private val iouThreshold: Float = 0.4f,
    private val maxAge: Int = 20,
    private val nInit: Int = 3
) {
    private val tracks = mutableListOf<Track>()

    private var nextInternalId = 1
    private var nextDisplayId = 1

    fun update(detections: List<YOLODetector.Detection>): List<Track> {
        val currentTime = System.currentTimeMillis()

        tracks.forEach { track ->
            track.timeSinceUpdate++
        }

        val assignedDetections = BooleanArray(detections.size)

        for (track in tracks) {
            var bestMatchIndex = -1
            var bestIoU = 0f

            for ((index, detection) in detections.withIndex()) {
                if (assignedDetections[index]) continue

                val iou = calculateIoU(track.bbox, detection.box)
                if (iou > bestIoU) {
                    bestIoU = iou
                    bestMatchIndex = index
                }
            }

            if (bestMatchIndex != -1 && bestIoU >= iouThreshold) {
                val detection = detections[bestMatchIndex]

                track.bbox = RectF(
                    track.bbox.left * 0.75f + detection.box.left * 0.25f,
                    track.bbox.top * 0.75f + detection.box.top * 0.25f,
                    track.bbox.right * 0.75f + detection.box.right * 0.25f,
                    track.bbox.bottom * 0.75f + detection.box.bottom * 0.25f
                )

                track.lastSeen = currentTime
                track.hits += 1
                track.timeSinceUpdate = 0

                if (!track.isConfirmed && track.hits >= nInit) {
                    track.isConfirmed = true
                    if (track.displayId == null) {
                        track.displayId = nextDisplayId++
                    }
                }

                assignedDetections[bestMatchIndex] = true
            }
        }

        for ((index, detection) in detections.withIndex()) {
            if (!assignedDetections[index]) {
                tracks.add(
                    Track(
                        internalId = nextInternalId++,
                        bbox = detection.box,
                        lastSeen = currentTime,
                        hits = 1,
                        timeSinceUpdate = 0,
                        isConfirmed = false
                    )
                )
            }
        }

        tracks.removeAll { it.timeSinceUpdate > maxAge }

        // Возвращаем только подтверждённые треки
        return tracks.filter { it.isConfirmed && it.displayId != null }
    }

    fun reset() {
        tracks.clear()
        nextInternalId = 1
        nextDisplayId = 1
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val left = max(box1.left, box2.left)
        val top = max(box1.top, box2.top)
        val right = min(box1.right, box2.right)
        val bottom = min(box1.bottom, box2.bottom)

        if (right <= left || bottom <= top) return 0f

        val intersection = (right - left) * (bottom - top)
        val union = box1.width() * box1.height() + box2.width() * box2.height() - intersection

        return if (union > 0) intersection / union else 0f
    }
}