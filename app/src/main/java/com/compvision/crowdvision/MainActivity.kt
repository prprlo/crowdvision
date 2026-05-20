package com.compvision.crowdvision

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.compvision.crowdvision.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.ViewGroup

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private lateinit var objectDetector: YOLODetector
    private lateinit var repository: AnalyticsRepository

    private var currentSessionId: Long? = null

    private var lastSavedTimestamp = 0L
    private val saveIntervalMs = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            val topPanelBaseTopMargin =
                (binding.topInfoPanel.layoutParams as ViewGroup.MarginLayoutParams).topMargin

            val bottomPanelBaseBottomMargin =
                (binding.bottomControlPanel.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin

            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

                val topParams = binding.topInfoPanel.layoutParams as ViewGroup.MarginLayoutParams
                topParams.topMargin = systemBars.top + topPanelBaseTopMargin
                binding.topInfoPanel.layoutParams = topParams

                val bottomParams = binding.bottomControlPanel.layoutParams as ViewGroup.MarginLayoutParams
                bottomParams.bottomMargin = systemBars.bottom + bottomPanelBaseBottomMargin
                binding.bottomControlPanel.layoutParams = bottomParams

                insets
            }

            currentSessionId = intent.getLongExtra("session_id", -1L).takeIf { it != -1L }

            cameraExecutor = Executors.newSingleThreadExecutor()

            val database = AppDatabase.getDatabase(this)
            repository = AnalyticsRepository(database.analyticsDao())

            lifecycleScope.launch(Dispatchers.IO) {
                val session = currentSessionId?.let { repository.getSessionById(it) }

                runOnUiThread {
                    binding.sessionTitleText.text =
                        session?.title ?: "Сессия не выбрана"
                }
            }

            objectDetector = YOLODetector(
                this,
                binding.overlay,
                statusCallback = { status ->
                    runOnUiThread { updateStatus(status) }
                },
                analyticsCallback = { analytics ->
                    runOnUiThread {
                        updateAnalytics(analytics)
                        saveAnalyticsIfNeeded(analytics)
                    }
                }
            )

            binding.historyButton.setOnClickListener {
                val intent = Intent(this, HomeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
                finish()
            }

            binding.resetSessionButton.setOnClickListener {
                objectDetector.resetSession()

                updateAnalytics(
                    SessionAnalytics(
                        currentPeopleCount = 0,
                        uniqueVisitorsCount = 0,
                        activeTrackIds = emptyList()
                    )
                )

                Toast.makeText(this, "Сессия сброшена", Toast.LENGTH_SHORT).show()
            }

            updateProcessingButton()

            binding.toggleProcessingButton.setOnClickListener {
                if (objectDetector.isProcessingEnabled()) {
                    objectDetector.stopProcessing()

                    lifecycleScope.launch(Dispatchers.IO) {
                        currentSessionId?.let { sessionId ->
                            repository.finishSession(sessionId)
                            Log.d("MainActivity", "Finished session: $sessionId")
                        }
                    }

                    updateAnalytics(
                        SessionAnalytics(
                            currentPeopleCount = 0,
                            uniqueVisitorsCount = 0,
                            activeTrackIds = emptyList()
                        )
                    )

                    Toast.makeText(this, "Обработка остановлена", Toast.LENGTH_SHORT).show()
                } else {
                    if (currentSessionId == null) {
                        Toast.makeText(this, "Сессия не выбрана", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    objectDetector.startProcessing()
                    Toast.makeText(this, "Обработка запущена", Toast.LENGTH_SHORT).show()
                }

                updateProcessingButton()
            }

            if (allPermissionsGranted()) {
                startCamera()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS
                )
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}", e)
            Toast.makeText(
                this,
                "Ошибка инициализации приложения: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private fun updateStatus(message: String) {
        binding.infoText.text = message
    }

    private fun hideStatus() {
        binding.statusContainer.visibility = View.GONE
    }

    private fun updateAnalytics(analytics: SessionAnalytics) {
        binding.objectCountText.text =
            "Сейчас в кадре: ${analytics.currentPeopleCount}\n" +
                    "Уникальных за сессию: ${analytics.uniqueVisitorsCount}"
        binding.objectCountText.visibility = View.VISIBLE
    }

    private fun updateProcessingButton() {
        binding.toggleProcessingButton.text =
            if (objectDetector.isProcessingEnabled()) "Остановить"
            else "Начать"
    }

    private fun saveAnalyticsIfNeeded(analytics: SessionAnalytics) {
        val now = System.currentTimeMillis()
        val sessionId = currentSessionId ?: return

        if (now - lastSavedTimestamp < saveIntervalMs) return
        if (analytics.currentPeopleCount == 0 && analytics.uniqueVisitorsCount == 0) return

        lastSavedTimestamp = now

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                repository.insertSnapshot(sessionId, analytics)
                Log.d("MainActivity", "Analytics snapshot saved. Session=$sessionId")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error saving analytics: ${e.message}", e)
            }
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(baseContext, it) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                    }

                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, objectDetector)
                    }

                val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )

                binding.infoText.postDelayed({
                    hideStatus()
                    binding.objectCountText.visibility = View.VISIBLE
                }, 1500)

            } catch (exc: Exception) {
                Log.e("MainActivity", "Camera binding failed: ${exc.message}", exc)
                Toast.makeText(
                    this,
                    "Ошибка запуска камеры: ${exc.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            cameraExecutor.shutdown()
            if (::objectDetector.isInitialized) {
                objectDetector.close()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onDestroy: ${e.message}", e)
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}