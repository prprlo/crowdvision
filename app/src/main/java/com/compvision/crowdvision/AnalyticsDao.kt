package com.compvision.crowdvision

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AnalyticsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: AnalyticsSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ObservationSessionEntity): Long

    @Query("UPDATE observation_sessions SET endedAt = :endedAt WHERE id = :sessionId")
    suspend fun finishSession(sessionId: Long, endedAt: Long)

    @Query("UPDATE observation_sessions SET notes = :notes WHERE id = :sessionId")
    suspend fun updateSessionNotes(sessionId: Long, notes: String)

    @Query("SELECT * FROM observation_sessions ORDER BY startedAt DESC")
    suspend fun getAllSessions(): List<ObservationSessionEntity>

    @Query("SELECT * FROM observation_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): ObservationSessionEntity?

    @Query("SELECT * FROM observation_sessions ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestSession(): ObservationSessionEntity?

    @Query("SELECT * FROM analytics_snapshots WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getSnapshotsBySession(sessionId: Long): List<AnalyticsSnapshotEntity>

    @Query("SELECT * FROM analytics_snapshots WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getSnapshotsBySessionAsc(sessionId: Long): List<AnalyticsSnapshotEntity>

    @Query("DELETE FROM analytics_snapshots WHERE sessionId = :sessionId")
    suspend fun deleteSnapshotsBySession(sessionId: Long)

    @Query("DELETE FROM observation_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("DELETE FROM analytics_snapshots")
    suspend fun clearAllSnapshots()

    @Query("DELETE FROM observation_sessions")
    suspend fun clearAllSessions()
}