package com.parentalcontrol.mvp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitoring_events")
data class MonitoringEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val confidence: Float,
    val description: String,
    val ocrText: String? = null,
    val screenshotPath: String? = null,
    val isReviewed: Boolean = false,
    val isFalsePositive: Boolean = false
)
