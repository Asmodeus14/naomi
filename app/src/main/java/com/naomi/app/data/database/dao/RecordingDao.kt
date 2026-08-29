package com.naomi.app.data.database.dao

import androidx.room.*
import com.naomi.app.data.database.entities.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: RecordingEntity): Long

    @Update
    suspend fun update(recording: RecordingEntity)

    @Delete
    suspend fun delete(recording: RecordingEntity)

    @Query("SELECT * FROM recordings WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllRecordingsFlow(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getAllRecordings(): List<RecordingEntity>

    @Query("SELECT * FROM recordings WHERE isDeleted = 0 AND expiresAt IS NOT NULL AND expiresAt < :currentTime")
    suspend fun getExpiredRecordings(currentTime: Long = System.currentTimeMillis()): List<RecordingEntity>

    @Query("SELECT COALESCE(SUM(fileSizeBytes), 0) FROM recordings WHERE isDeleted = 0")
    suspend fun getTotalAudioSizeBytes(): Long

    @Query("UPDATE recordings SET isDeleted = 1 WHERE id = :id")
    suspend fun markDeleted(id: Long)
}
