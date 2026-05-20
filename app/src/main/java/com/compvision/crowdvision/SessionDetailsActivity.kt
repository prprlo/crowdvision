package com.compvision.crowdvision

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.compvision.crowdvision.databinding.ActivitySessionDetailsBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySessionDetailsBinding
    private lateinit var repository: AnalyticsRepository
    private var sessionId: Long = -1L

    private data class AggregatedPoint(
        val timestamp: Long,
        val avgCurrentPeople: Float
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySessionDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.root.setPadding(
                binding.root.paddingLeft,
                systemBars.top + 8,
                binding.root.paddingRight,
                systemBars.bottom + 8
            )

            insets
        }

        sessionId = intent.getLongExtra("session_id", -1L)

        val database = AppDatabase.getDatabase(this)
        repository = AnalyticsRepository(database.analyticsDao())

        binding.backButton.setOnClickListener {
            finish()
        }

        binding.openCameraButton.setOnClickListener {
            if (sessionId != -1L) {
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("session_id", sessionId)
                }
                startActivity(intent)
            }
        }

        binding.saveNoteButton.setOnClickListener {
            saveNote()
        }

        setupChart()
        loadSessionDetails()
    }

    override fun onResume() {
        super.onResume()
        loadSessionDetails()
    }

    private fun saveNote() {
        if (sessionId == -1L) return

        val newNote = binding.notesInput.text.toString().trim()

        lifecycleScope.launch(Dispatchers.IO) {
            repository.updateSessionNotes(sessionId, newNote)

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@SessionDetailsActivity,
                    "Заметка сохранена",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupChart() {
        val axisTextColor = Color.rgb(95, 102, 115)
        val gridColor = Color.rgb(230, 235, 243)

        binding.lineChart.description.isEnabled = false
        binding.lineChart.legend.isEnabled = false

        binding.lineChart.setTouchEnabled(false)
        binding.lineChart.isDragEnabled = false
        binding.lineChart.setScaleEnabled(false)
        binding.lineChart.setPinchZoom(false)

        binding.lineChart.setDrawGridBackground(false)
        binding.lineChart.setBackgroundColor(Color.TRANSPARENT)

        binding.lineChart.setNoDataText("Нет данных для отображения")
        binding.lineChart.setNoDataTextColor(axisTextColor)

        // X ось (время)
        val xAxis = binding.lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = axisTextColor
        xAxis.textSize = 10f
        xAxis.axisLineColor = gridColor
        xAxis.granularity = 1f
        xAxis.setAvoidFirstLastClipping(true)

        // Y ось (люди)
        val leftAxis = binding.lineChart.axisLeft
        leftAxis.textColor = axisTextColor
        leftAxis.textSize = 10f
        leftAxis.axisMinimum = 0f
        leftAxis.granularity = 1f
        leftAxis.setLabelCount(5, false)
        leftAxis.gridColor = gridColor
        leftAxis.axisLineColor = gridColor

        binding.lineChart.axisRight.isEnabled = false

        binding.lineChart.setExtraOffsets(8f, 8f, 12f, 16f)
    }

    private fun loadSessionDetails() {
        if (sessionId == -1L) return

        lifecycleScope.launch(Dispatchers.IO) {
            val session = repository.getSessionById(sessionId)
            val snapshots = repository.getSnapshotsBySessionAsc(sessionId)

            withContext(Dispatchers.Main) {
                if (session == null) {
                    binding.titleText.text = "Сессия не найдена"
                    return@withContext
                }

                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

                binding.titleText.text = session.title

                binding.locationText.text =
                    if (session.location.isNotBlank()) {
                        "Место: ${session.location}"
                    } else {
                        "Место не указано"
                    }

                binding.dateText.text = "Создана: ${dateFormat.format(Date(session.startedAt))}"
                binding.notesInput.setText(session.notes)

                if (snapshots.isEmpty()) {
                    binding.uniqueVisitorsValueText.text = "0"
                    binding.avgPeopleValueText.text = "0,0"
                    binding.measurementsValueText.text = "0"
                    binding.durationValueText.text = "0 мин 0 сек"

                    binding.lineChart.clear()
                    binding.lineChart.invalidate()
                    return@withContext
                }

                val totalUniqueVisitors = snapshots.maxOfOrNull { it.uniqueVisitorsCount } ?: 0
                val avgPeople = snapshots.map { it.currentPeopleCount }.average()
                val measurementCount = snapshots.size

                val durationText = buildDurationText(
                    snapshots.first().timestamp,
                    snapshots.last().timestamp
                )

                binding.uniqueVisitorsValueText.text = totalUniqueVisitors.toString()
                binding.avgPeopleValueText.text =
                    String.format(Locale.getDefault(), "%.1f", avgPeople)
                binding.measurementsValueText.text = measurementCount.toString()
                binding.durationValueText.text = durationText

                val filteredSnapshots = trimTrailingZeroSnapshots(snapshots)
                val aggregated = aggregateSnapshotsByWindow(filteredSnapshots, 10_000L)

                if (aggregated.isEmpty()) {
                    binding.lineChart.clear()
                    binding.lineChart.invalidate()
                    return@withContext
                }

                val xLabels = mutableListOf<String>()
                val entries = mutableListOf<Entry>()
                val startTimestamp = aggregated.first().timestamp
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

                aggregated.forEachIndexed { index, item ->
                    xLabels.add(timeFormat.format(Date(item.timestamp)))
                    entries.add(Entry(index.toFloat(), item.avgCurrentPeople))
                }

                val dataSet = LineDataSet(entries, "").apply {
                    color = Color.rgb(37, 99, 235)
                    lineWidth = 2.2f

                    setDrawCircles(true)
                    circleRadius = 2.6f
                    setCircleColor(Color.rgb(37, 99, 235))
                    circleHoleRadius = 1.2f
                    setCircleHoleColor(Color.WHITE)

                    setDrawValues(false)
                    mode = LineDataSet.Mode.LINEAR

                    setDrawFilled(true)
                    fillColor = Color.rgb(219, 234, 254)
                    fillAlpha = 140

                    setDrawHighlightIndicators(false)
                }

                binding.lineChart.data = LineData(dataSet)
                binding.lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(xLabels)
                binding.lineChart.xAxis.labelCount = xLabels.size.coerceAtMost(4)
                binding.lineChart.xAxis.labelRotationAngle = 0f
                binding.lineChart.invalidate()
            }
        }
    }

    private fun trimTrailingZeroSnapshots(
        snapshots: List<AnalyticsSnapshotEntity>
    ): List<AnalyticsSnapshotEntity> {
        if (snapshots.isEmpty()) return emptyList()

        var lastNonZeroIndex = -1

        for (i in snapshots.indices.reversed()) {
            if (snapshots[i].currentPeopleCount > 0) {
                lastNonZeroIndex = i
                break
            }
        }

        return if (lastNonZeroIndex == -1) {
            snapshots
        } else {
            snapshots.subList(0, lastNonZeroIndex + 1)
        }
    }

    private fun aggregateSnapshotsByWindow(
        snapshots: List<AnalyticsSnapshotEntity>,
        windowMs: Long
    ): List<AggregatedPoint> {
        if (snapshots.isEmpty()) return emptyList()

        val result = mutableListOf<AggregatedPoint>()
        val sortedSnapshots = snapshots.sortedBy { it.timestamp }

        var windowStart = sortedSnapshots.first().timestamp
        var currentWindowItems = mutableListOf<AnalyticsSnapshotEntity>()

        for (snapshot in sortedSnapshots) {
            if (snapshot.timestamp < windowStart + windowMs) {
                currentWindowItems.add(snapshot)
            } else {
                result.add(buildAggregatedPoint(windowStart, currentWindowItems))
                windowStart = snapshot.timestamp
                currentWindowItems = mutableListOf(snapshot)
            }
        }

        if (currentWindowItems.isNotEmpty()) {
            result.add(buildAggregatedPoint(windowStart, currentWindowItems))
        }

        return result
    }

    private fun buildAggregatedPoint(
        windowTimestamp: Long,
        items: List<AnalyticsSnapshotEntity>
    ): AggregatedPoint {

        val maxPeople = items.maxOfOrNull { it.currentPeopleCount } ?: 0

        return AggregatedPoint(
            timestamp = windowTimestamp,
            avgCurrentPeople = maxPeople.toFloat()
        )
    }

    private fun buildDurationText(startMillis: Long, endMillis: Long): String {
        val durationMs = endMillis - startMillis
        val totalSeconds = (durationMs / 1000).toInt()

        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60

        return "${minutes} мин ${seconds} сек"
    }
}