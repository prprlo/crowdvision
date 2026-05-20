package com.compvision.crowdvision

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analytics_snapshots")
data class AnalyticsSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: Long,
    val timestamp: Long,
    val currentPeopleCount: Int,
    val uniqueVisitorsCount: Int
)