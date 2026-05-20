package com.compvision.crowdvision

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "observation_sessions")
data class ObservationSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val startedAt: Long,
    val endedAt: Long? = null,
    val title: String = "Сессия наблюдения",
    val location: String = "",
    val notes: String = ""
)

