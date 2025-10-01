package com.parentalcontrol.mvp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert
    suspend fun insertEvent(event: MonitoringEvent)
    
    @Update
    suspend fun updateEvent(event: MonitoringEvent)
    
    @Delete
    suspend fun deleteEvent(event: MonitoringEvent)
    
    @Query("SELECT * FROM monitoring_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<MonitoringEvent>>
    
    @Query("SELECT * FROM monitoring_events WHERE isReviewed = 0 ORDER BY timestamp DESC")
    fun getUnreviewedEvents(): Flow<List<MonitoringEvent>>
    
    @Query("SELECT * FROM monitoring_events WHERE timestamp > :since ORDER BY timestamp DESC")
    fun getEventsSince(since: Long): Flow<List<MonitoringEvent>>
    
    @Query("UPDATE monitoring_events SET isReviewed = 1 WHERE id = :eventId")
    suspend fun markAsReviewed(eventId: Long)
    
    @Query("UPDATE monitoring_events SET isFalsePositive = 1 WHERE id = :eventId")
    suspend fun markAsFalsePositive(eventId: Long)
    
    @Query("DELETE FROM monitoring_events WHERE timestamp < :before")
    suspend fun deleteOldEvents(before: Long)
}
