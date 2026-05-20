package com.compvision.crowdvision

class AnalyticsRepository(private val analyticsDao: AnalyticsDao) {

    suspend fun createSession(
        title: String,
        location: String = "",
        notes: String = ""
    ): Long {
        val session = ObservationSessionEntity(
            startedAt = System.currentTimeMillis(),
            endedAt = null,
            title = title,
            location = location,
            notes = notes
        )
        return analyticsDao.insertSession(session)
    }

    suspend fun startSession(
        title: String = "Сессия наблюдения",
        location: String = "",
        notes: String = ""
    ): Long {
        val session = ObservationSessionEntity(
            startedAt = System.currentTimeMillis(),
            title = title,
            location = location,
            notes = notes
        )
        return analyticsDao.insertSession(session)
    }

    suspend fun finishSession(sessionId: Long) {
        analyticsDao.finishSession(sessionId, System.currentTimeMillis())
    }

    suspend fun updateSessionNotes(sessionId: Long, notes: String) {
        analyticsDao.updateSessionNotes(sessionId, notes)
    }

    suspend fun insertSnapshot(sessionId: Long, sessionAnalytics: SessionAnalytics) {
        val snapshot = AnalyticsSnapshotEntity(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            currentPeopleCount = sessionAnalytics.currentPeopleCount,
            uniqueVisitorsCount = sessionAnalytics.uniqueVisitorsCount
        )
        analyticsDao.insertSnapshot(snapshot)
    }

    suspend fun getAllSessions(): List<ObservationSessionEntity> {
        return analyticsDao.getAllSessions()
    }

    suspend fun getSessionById(sessionId: Long): ObservationSessionEntity? {
        return analyticsDao.getSessionById(sessionId)
    }

    suspend fun getLatestSession(): ObservationSessionEntity? {
        return analyticsDao.getLatestSession()
    }

    suspend fun getSnapshotsBySession(sessionId: Long): List<AnalyticsSnapshotEntity> {
        return analyticsDao.getSnapshotsBySession(sessionId)
    }

    suspend fun getSnapshotsBySessionAsc(sessionId: Long): List<AnalyticsSnapshotEntity> {
        return analyticsDao.getSnapshotsBySessionAsc(sessionId)
    }

    suspend fun getSessionSummaries(): List<SessionSummary> {
        val sessions = analyticsDao.getAllSessions()

        return sessions.map { session ->
            val snapshots = analyticsDao.getSnapshotsBySession(session.id)

            val measurementCount = snapshots.size
            val uniqueVisitors = snapshots.maxOfOrNull { it.uniqueVisitorsCount }
            val avgPeople = if (snapshots.isNotEmpty()) {
                snapshots.map { it.currentPeopleCount }.average().toFloat()
            } else {
                null
            }

            SessionSummary(
                id = session.id,
                title = session.title,
                startedAt = session.startedAt,
                endedAt = session.endedAt,
                measurementCount = measurementCount,
                uniqueVisitors = uniqueVisitors,
                avgPeople = avgPeople
            )
        }
    }

    suspend fun clearSession(sessionId: Long) {
        analyticsDao.deleteSnapshotsBySession(sessionId)
        analyticsDao.deleteSessionById(sessionId)
    }

    suspend fun clearAll() {
        analyticsDao.clearAllSnapshots()
        analyticsDao.clearAllSessions()
    }
}