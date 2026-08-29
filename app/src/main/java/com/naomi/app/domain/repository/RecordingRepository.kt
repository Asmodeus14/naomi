package com.naomi.app.domain.repository

import com.naomi.app.data.database.entities.RecordingEntity
import com.naomi.app.domain.model.StorageStats
import kotlinx.coroutines.flow.Flow
import java.io.File

interface RecordingRepository {
    fun getAllRecordingsFlow(): Flow<List<RecordingEntity>>
    suspend fun saveRecording(
        file: File,
        durationMs: Long,
        noteId: Long? = null
    ): RecordingEntity
    suspend fun cleanExpiredRecordings(): Int
    suspend fun cleanAllRecordings(): Int
    suspend fun getStorageStats(): StorageStats
}
