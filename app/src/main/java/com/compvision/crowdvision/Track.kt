package com.compvision.crowdvision

import android.graphics.RectF

data class Track(
    val internalId: Int,
    var displayId: Int? = null,
    var bbox: RectF,
    var lastSeen: Long,
    var hits: Int = 1,
    var timeSinceUpdate: Int = 0,
    var isConfirmed: Boolean = false
)