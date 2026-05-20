package com.compvision.crowdvision

data class SessionAnalytics(
    val currentPeopleCount: Int,
    val uniqueVisitorsCount: Int,
    val activeTrackIds: List<Int>
)

class PeopleAnalyticsManager(
    private val minHitsForUniqueVisitor: Int = 4
) {

    private val seenDisplayIds = mutableSetOf<Int>()

    fun update(tracks: List<Track>): SessionAnalytics {
        val confirmedTracks = tracks.filter { track ->
            track.isConfirmed && track.displayId != null
        }

        val stableTracksForUnique = confirmedTracks.filter { track ->
            track.hits >= minHitsForUniqueVisitor
        }

        for (track in stableTracksForUnique) {
            track.displayId?.let { displayId ->
                seenDisplayIds.add(displayId)
            }
        }

        val activeDisplayIds = confirmedTracks.mapNotNull { it.displayId }.sorted()

        return SessionAnalytics(
            currentPeopleCount = confirmedTracks.size,
            uniqueVisitorsCount = seenDisplayIds.size,
            activeTrackIds = activeDisplayIds
        )
    }

    fun reset() {
        seenDisplayIds.clear()
    }
}