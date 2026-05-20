package com.compvision.crowdvision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

import android.os.SystemClock


class YOLODetector(
    private val context: Context,
    private val overlay: ObjectDetectionOverlay,
    private val statusCallback: ((String) -> Unit)? = null,
    private val analyticsCallback: ((SessionAnalytics) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    private var interpreter: Interpreter? = null
    private lateinit var inputBuffer: ByteBuffer
    private var outputBuffer: Array<Array<FloatArray>>? = null

    private val modelInputSize = 640
    private val modelOutputSize = 8400
    private val numClasses = 80

    private val confidenceThreshold = 0.4f
    private val nmsThreshold = 0.4f

    private var outputShape: IntArray? = null

    private val tracker = SimpleTracker(
        iouThreshold = 0.2f,
        maxAge = 10,
        nInit = 2
    )


    private val modelName = "yolov8n.tflite"
    private val perfTag = "CrowdVisionPerf"

    private var perfCount = 0
    private var perfTotalMs = 0L
    private var perfMaxMs = 0L
    private var perfDetectionTotalMs = 0L
    private var perfTrackingTotalMs = 0L

    private val perfLogEvery = 30


    private val analyticsManager = PeopleAnalyticsManager()

    @Volatile
    private var isProcessingEnabled = false

    private val classNames = arrayOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck",
        "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench",
        "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra",
        "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove",
        "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup",
        "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
        "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
        "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
        "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
        "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
        "hair drier", "toothbrush"
    )

    private var frameCount = 0
    private var lastLogTime = System.currentTimeMillis()
    private var lastProcessedTime = 0L

    private val frameSkipInterval = 2
    private val minProcessingInterval = 100L

    init {
        try {
            Log.d("YOLODetector", "init: Starting model loading")
            statusCallback?.invoke("Чтение файла модели...")

            //val modelBuffer = loadModelFile("yolov8n.tflite")

            val modelBuffer = loadModelFile(modelName)
            Log.d(perfTag, "MODEL_LOADED model=$modelName size=${modelBuffer.capacity()} bytes")

            Log.d("YOLODetector", "init: Model file loaded, size: ${modelBuffer.capacity()} bytes")

            statusCallback?.invoke("Инициализация интерпретатора...")
            interpreter = Interpreter(modelBuffer)
            Log.d("YOLODetector", "init: Interpreter created")

            statusCallback?.invoke("Анализ структуры модели...")
            val outputTensor = interpreter?.getOutputTensor(0)
            outputShape = outputTensor?.shape()
            Log.d("YOLODetector", "Model output shape: ${outputShape?.contentToString()}")

            statusCallback?.invoke("Выделение памяти...")
            inputBuffer = ByteBuffer.allocateDirect(4 * modelInputSize * modelInputSize * 3)
            inputBuffer.order(ByteOrder.nativeOrder())

            val shape = outputShape ?: intArrayOf(1, 84, 8400)
            val batchSize = shape[0]
            val features = shape[1]
            val detections = shape[2]

            outputBuffer = Array(batchSize) { Array(features) { FloatArray(detections) } }

            Log.d("YOLODetector", "Model loaded successfully")
            statusCallback?.invoke("Модель загружена")
        } catch (e: Exception) {
            Log.e("YOLODetector", "Error loading model: ${e.message}", e)
            statusCallback?.invoke("Ошибка загрузки модели: ${e.message}")

            inputBuffer = ByteBuffer.allocateDirect(4 * modelInputSize * modelInputSize * 3)
            inputBuffer.order(ByteOrder.nativeOrder())
            outputBuffer = Array(1) { Array(84) { FloatArray(modelOutputSize) } }
        }
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val assetManager = context.assets
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {
            if (interpreter == null || !::inputBuffer.isInitialized || outputBuffer == null) {
                imageProxy.close()
                return
            }

            if (!isProcessingEnabled) {
                imageProxy.close()
                return
            }

            frameCount++
            val currentTime = System.currentTimeMillis()

            if (frameCount % frameSkipInterval != 0) {
                imageProxy.close()
                return
            }

            if (currentTime - lastProcessedTime < minProcessingInterval) {
                imageProxy.close()
                return
            }

            if (currentTime - lastLogTime > 5000) {
                Log.d("YOLODetector", "analyze: Processing frame $frameCount")
                lastLogTime = currentTime
            }

            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap == null) {
                imageProxy.close()
                return
            }

            lastProcessedTime = currentTime

//            val detections = detectObjects(bitmap)
//            val tracks = tracker.update(detections)
//            val analytics = analyticsManager.update(tracks)
//
//            overlay.post {
//                overlay.setTracks(tracks, bitmap.width, bitmap.height)
//            }
//
//            analyticsCallback?.invoke(analytics)

            val totalStart = SystemClock.elapsedRealtime()

            val detectionStart = SystemClock.elapsedRealtime()
            val detections = detectObjects(bitmap)
            val detectionMs = SystemClock.elapsedRealtime() - detectionStart

            val trackingStart = SystemClock.elapsedRealtime()
            val tracks = tracker.update(detections)
            val analytics = analyticsManager.update(tracks)
            val trackingMs = SystemClock.elapsedRealtime() - trackingStart

            val totalMs = SystemClock.elapsedRealtime() - totalStart

            logPerformance(
                totalMs = totalMs,
                detectionMs = detectionMs,
                trackingMs = trackingMs,
                detectionsCount = detections.size,
                tracksCount = tracks.size,
                analytics = analytics
            )

            overlay.post {
                overlay.setTracks(tracks, bitmap.width, bitmap.height)
            }

            analyticsCallback?.invoke(analytics)

        } catch (e: Exception) {
            Log.e("YOLODetector", "Error in analyze: ${e.message}", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun logPerformance(
        totalMs: Long,
        detectionMs: Long,
        trackingMs: Long,
        detectionsCount: Int,
        tracksCount: Int,
        analytics: SessionAnalytics
    ) {
        perfCount++
        perfTotalMs += totalMs
        perfDetectionTotalMs += detectionMs
        perfTrackingTotalMs += trackingMs
        perfMaxMs = maxOf(perfMaxMs, totalMs)

        Log.d(
            perfTag,
            "FRAME model=$modelName " +
                    "total=${totalMs}ms " +
                    "detection=${detectionMs}ms " +
                    "tracking=${trackingMs}ms " +
                    "detections=$detectionsCount " +
                    "tracks=$tracksCount " +
                    "current=${analytics.currentPeopleCount} " +
                    "unique=${analytics.uniqueVisitorsCount} " +
                    "ids=${analytics.activeTrackIds.joinToString("|")}"
        )

        if (perfCount % perfLogEvery == 0) {
            Log.d(
                perfTag,
                "AVG model=$modelName " +
                        "frames=$perfCount " +
                        "avgTotal=${perfTotalMs / perfCount}ms " +
                        "avgDetection=${perfDetectionTotalMs / perfCount}ms " +
                        "avgTracking=${perfTrackingTotalMs / perfCount}ms " +
                        "maxTotal=${perfMaxMs}ms"
            )
        }
    }
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val planes = imageProxy.planes
            if (planes.size < 2) {
                Log.e("YOLODetector", "Unexpected image format: ${planes.size} planes")
                return null
            }

            val width = imageProxy.width
            val height = imageProxy.height
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = if (planes.size > 2) planes[2] else null

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane?.buffer

            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            val uRowStride = uPlane.rowStride
            val uPixelStride = uPlane.pixelStride
            val vRowStride = vPlane?.rowStride ?: uRowStride
            val vPixelStride = vPlane?.pixelStride ?: uPixelStride

            val yArray = ByteArray(yBuffer.remaining())
            yBuffer.get(yArray)

            val uArray = ByteArray(uBuffer.remaining())
            uBuffer.get(uArray)

            val vArray = if (vBuffer != null) {
                val arr = ByteArray(vBuffer.remaining())
                vBuffer.get(arr)
                arr
            } else {
                null
            }

            val pixels = IntArray(width * height)
            var pixelIndex = 0

            for (y in 0 until height) {
                val yRowOffset = y * yRowStride
                val uvRowOffset = (y / 2) * uRowStride

                for (x in 0 until width) {
                    val yOffset = yRowOffset + x * yPixelStride
                    val uvOffset = uvRowOffset + (x / 2) * uPixelStride

                    val yVal = (yArray[yOffset].toInt() and 0xFF)
                    val uVal = (uArray[uvOffset].toInt() and 0xFF)

                    val vVal = if (vArray != null) {
                        val vOffset = (y / 2) * vRowStride + (x / 2) * vPixelStride
                        (vArray[vOffset].toInt() and 0xFF)
                    } else {
                        val vOffset = uvOffset + if (x % 2 == 0) 0 else 1
                        if (vOffset < uArray.size) (uArray[vOffset].toInt() and 0xFF) else 128
                    }

                    val c = yVal - 16
                    val d = uVal - 128
                    val e = vVal - 128

                    val r = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
                    val g = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
                    val b = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)

                    pixels[pixelIndex++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
                bitmap.recycle()
                rotated
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e("YOLODetector", "Error converting ImageProxy to Bitmap: ${e.message}", e)
            null
        }
    }

    private fun detectObjects(bitmap: Bitmap): List<Detection> {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, modelInputSize, modelInputSize, true)

        inputBuffer.rewind()

        val intValues = IntArray(modelInputSize * modelInputSize)
        resizedBitmap.getPixels(
            intValues,
            0,
            resizedBitmap.width,
            0,
            0,
            resizedBitmap.width,
            resizedBitmap.height
        )

        var pixel = 0
        for (i in 0 until modelInputSize) {
            for (j in 0 until modelInputSize) {
                val value = intValues[pixel++]
                inputBuffer.putFloat(((value shr 16) and 0xFF) / 255f)
                inputBuffer.putFloat(((value shr 8) and 0xFF) / 255f)
                inputBuffer.putFloat((value and 0xFF) / 255f)
            }
        }

        val output = outputBuffer ?: return emptyList()
        interpreter?.run(inputBuffer, output)

        val detections = mutableListOf<Detection>()
        val batch = output[0]
        val numDetections = batch[0].size

        for (i in 0 until numDetections) {
            val xCenter = batch[0][i]
            val yCenter = batch[1][i]
            val width = batch[2][i]
            val height = batch[3][i]

            var maxClass = -1
            var maxScore = 0f

            for (j in 0 until numClasses) {
                val score = batch[4 + j][i]
                if (score > maxScore) {
                    maxScore = score
                    maxClass = j
                }
            }

            if (maxClass != 0) continue
            if (maxScore < confidenceThreshold) continue

            val left = (xCenter - width / 2) * bitmap.width
            val top = (yCenter - height / 2) * bitmap.height
            val right = (xCenter + width / 2) * bitmap.width
            val bottom = (yCenter + height / 2) * bitmap.height

            if (left < 0 || top < 0 || right > bitmap.width || bottom > bitmap.height) continue

            detections.add(
                Detection(
                    className = classNames[maxClass],
                    confidence = maxScore,
                    box = RectF(left, top, right, bottom)
                )
            )
        }

        return applyNMS(detections)
    }

    private fun applyNMS(detections: List<Detection>): List<Detection> {
        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<Detection>()
        val suppressed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (suppressed[i]) continue

            selected.add(sorted[i])

            for (j in (i + 1) until sorted.size) {
                if (suppressed[j]) continue

                val iou = calculateIoU(sorted[i].box, sorted[j].box)
                if (iou > nmsThreshold) {
                    suppressed[j] = true
                }
            }
        }

        return selected
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectionLeft = max(box1.left, box2.left)
        val intersectionTop = max(box1.top, box2.top)
        val intersectionRight = min(box1.right, box2.right)
        val intersectionBottom = min(box1.bottom, box2.bottom)

        if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) {
            return 0f
        }

        val intersectionArea =
            (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
        val box1Area = box1.width() * box1.height()
        val box2Area = box2.width() * box2.height()
        val unionArea = box1Area + box2Area - intersectionArea

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }

    fun startProcessing() {
        isProcessingEnabled = true
    }

    fun stopProcessing() {
        isProcessingEnabled = false
        resetSession()
    }

    fun isProcessingEnabled(): Boolean {
        return isProcessingEnabled
    }

    fun resetSession() {
        tracker.reset()
        analyticsManager.reset()

        overlay.post {
            overlay.setTracks(emptyList(), 1, 1)
        }
    }

    fun close() {
        interpreter?.close()
    }

    data class Detection(
        val className: String,
        val confidence: Float,
        val box: RectF
    )
}