package com.compvision.crowdvision

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.compvision.crowdvision.databinding.ActivityHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var repository: AnalyticsRepository
    private lateinit var adapter: HomeSessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val startPadding = binding.root.paddingStart
        val topPadding = binding.root.paddingTop
        val endPadding = binding.root.paddingEnd
        val bottomPadding = binding.root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.setPaddingRelative(
                startPadding,
                topPadding + systemBars.top,
                endPadding,
                bottomPadding + systemBars.bottom
            )

            insets
        }

        val database = AppDatabase.getDatabase(this)
        repository = AnalyticsRepository(database.analyticsDao())

        adapter = HomeSessionAdapter(
            onOpenCamera = { session ->
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("session_id", session.id)
                }
                startActivity(intent)
            },
            onOpenDetails = { session ->
                val intent = Intent(this, SessionDetailsActivity::class.java).apply {
                    putExtra("session_id", session.id)
                }
                startActivity(intent)
            },
            onDeleteSession = { session ->
                confirmDeleteSession(session)
            },
            repository = repository
        )

        binding.sessionsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.sessionsRecyclerView.adapter = adapter

        binding.createSessionButton.setOnClickListener {
            startActivity(Intent(this, CreateSessionActivity::class.java))
        }

        loadSessions()
    }

    override fun onResume() {
        super.onResume()
        loadSessions()
    }

    private fun loadSessions() {
        lifecycleScope.launch(Dispatchers.IO) {
            val sessions = repository.getSessionSummaries()

            val totalCount = sessions.size
            val completedCount = sessions.count { it.endedAt != null }

            withContext(Dispatchers.Main) {
                adapter.submitList(sessions)

                binding.totalSessionsValue.text = totalCount.toString()
                binding.completedSessionsValue.text = completedCount.toString()

                if (sessions.isEmpty()) {
                    binding.emptyText.visibility = android.view.View.VISIBLE
                    binding.sessionsRecyclerView.visibility = android.view.View.GONE
                    binding.emptyText.text =
                        "Сессий пока нет. Создайте новую сессию, чтобы начать наблюдение."
                } else {
                    binding.emptyText.visibility = android.view.View.GONE
                    binding.sessionsRecyclerView.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    private fun confirmDeleteSession(session: SessionSummary) {
        AlertDialog.Builder(this)
            .setTitle("Удалить сессию")
            .setMessage("Удалить сессию «${session.title}»? Это действие нельзя отменить.")
            .setPositiveButton("Удалить") { _, _ ->
                deleteSession(session.id)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteSession(sessionId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            repository.clearSession(sessionId)

            withContext(Dispatchers.Main) {
                loadSessions()
            }
        }
    }
}