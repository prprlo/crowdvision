package com.compvision.crowdvision

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.compvision.crowdvision.databinding.ItemHomeSessionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeSessionAdapter(
    private val onOpenCamera: (SessionSummary) -> Unit,
    private val onOpenDetails: (SessionSummary) -> Unit,
    private val onDeleteSession: (SessionSummary) -> Unit,
    private val repository: AnalyticsRepository
) : RecyclerView.Adapter<HomeSessionAdapter.HomeSessionViewHolder>() {

    private var items: List<SessionSummary> = emptyList()
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    inner class HomeSessionViewHolder(
        private val binding: ItemHomeSessionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SessionSummary, session: ObservationSessionEntity?) {
            binding.titleText.text = item.title
            binding.timeText.text = "Создана: ${dateFormat.format(Date(item.startedAt))}"

            binding.locationText.text =
                if (!session?.location.isNullOrBlank()) {
                    "Место: ${session?.location}"
                } else {
                    "Место не указано"
                }

            binding.noteText.text =
                if (!session?.notes.isNullOrBlank()) {
                    "Заметка: ${session?.notes}"
                } else {
                    "Заметка отсутствует"
                }

            val avgText = item.avgPeople?.let { "%.1f".format(it) } ?: "0.0"
            val uniqueText = item.uniqueVisitors ?: 0

            binding.statsText.text =
                "Уникальных посетителей: $uniqueText\n" +
                        "Среднее число людей в кадре: $avgText\n" +
                        "Количество замеров: ${item.measurementCount}"

            binding.openCameraButton.setOnClickListener {
                onOpenCamera(item)
            }

            binding.openChartButton.setOnClickListener {
                onOpenDetails(item)
            }

            binding.deleteButton.setOnClickListener {
                onDeleteSession(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeSessionViewHolder {
        val binding = ItemHomeSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HomeSessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HomeSessionViewHolder, position: Int) {
        val item = items[position]

        kotlinx.coroutines.runBlocking {
            val session = repository.getSessionById(item.id)
            holder.bind(item, session)
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<SessionSummary>) {
        items = newItems
        notifyDataSetChanged()
    }
}