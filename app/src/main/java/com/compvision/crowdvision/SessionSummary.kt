package com.compvision.crowdvision

data class SessionSummary(
    val id: Long,
    val title: String,
    val startedAt: Long,
    val endedAt: Long?,
    val measurementCount: Int,
    val uniqueVisitors: Int?,
    val avgPeople: Float?
)