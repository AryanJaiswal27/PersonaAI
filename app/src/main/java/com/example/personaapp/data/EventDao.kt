package com.example.personaapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>)

    @Update
    suspend fun updateEvent(event: EventEntity)

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: Long): EventEntity?

    @Query("SELECT * FROM events WHERE processed = 0 ORDER BY timestamp ASC")
    suspend fun getUnprocessedEvents(): List<EventEntity>

    @Query("SELECT * FROM events ORDER BY timestamp DESC")
    suspend fun getAllEvents(): List<EventEntity>

    @Query("SELECT * FROM events WHERE isCoreMemory = 1 OR emotionalWeight >= 7 OR eventType = 'MANUAL_ENTRY' ORDER BY timestamp DESC")
    suspend fun getCoreMemories(): List<EventEntity>

    @Query("SELECT COUNT(*) FROM events WHERE eventType = :eventType AND timestamp = :timestamp")
    suspend fun countEventByTimestampAndType(eventType: String, timestamp: Long): Int

    @Query("SELECT COUNT(*) FROM events WHERE eventType = :eventType AND content = :content")
    suspend fun countEventByContentAndType(eventType: String, content: String): Int

    @Query("SELECT * FROM events WHERE eventType = 'APP_USAGE_SUMMARY' AND packageName = :packageName AND processed = 0 LIMIT 1")
    suspend fun getUnprocessedAppUsageSummary(packageName: String): EventEntity?

    @Query("SELECT COUNT(*) FROM events WHERE eventType = 'NOTIFICATION' AND packageName = :packageName AND content = :content AND timestamp >= :sinceTimestamp")
    suspend fun countRecentNotification(packageName: String, content: String, sinceTimestamp: Long): Int

    @Query("DELETE FROM events WHERE id NOT IN (SELECT MIN(id) FROM events GROUP BY eventType, content)")
    suspend fun purgeDuplicateEvents()

    @Query("UPDATE events SET processed = 1 WHERE id IN (:eventIds)")
    suspend fun markEventsAsProcessed(eventIds: List<Int>)
    
    @Query("DELETE FROM events WHERE processed = 1")
    suspend fun deleteProcessedEvents()

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteEventById(id: Int)

    @Query("SELECT * FROM events WHERE content LIKE '%' || :name || '%' ORDER BY timestamp DESC")
    suspend fun getEventsForPerson(name: String): List<EventEntity>
}
