package com.compvision.crowdvision

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.compvision.crowdvision.databinding.ActivityCreateSessionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateSessionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateSessionBinding
    private lateinit var repository: AnalyticsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCreateSessionBinding.inflate(layoutInflater)
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

        val database = AppDatabase.getDatabase(this)
        repository = AnalyticsRepository(database.analyticsDao())

        binding.backButton.setOnClickListener {
            finish()
        }

        binding.saveButton.setOnClickListener {
            saveSession()
        }
    }

    private fun saveSession() {
        val title = binding.sessionTitleInput.text.toString().trim()
        val location = binding.sessionLocationInput.text.toString().trim()
        val notes = binding.sessionNotesInput.text.toString().trim()

        if (title.isBlank()) {
            binding.sessionTitleInput.error = "Введите название сессии"
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            repository.createSession(
                title = title,
                location = location,
                notes = notes
            )

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@CreateSessionActivity,
                    "Сессия создана",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
}