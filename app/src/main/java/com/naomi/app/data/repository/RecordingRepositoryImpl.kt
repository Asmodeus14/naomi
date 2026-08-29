package com.naomi.app.data.repository

import android.content.Context
import com.naomi.app.data.database.NaomiDatabase
import com.naomi.app.data.database.entities.RecordingEntity
import com.naomi.app.domain.model.RetentionPolicy
import com.naomi.app.domain.model.StorageStats
import com.naomi.app.domain.repository.RecordingRepository
import com.naomi.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class RecordingRepositoryImpl(
    private val context: Context,
    private val db: NaomiDatabase,
    private val settingsRepository: SettingsRepository
) : RecordingRepository {

    private val recordingDao = db.recordingDao()
    private val noteDao = db.noteDao()

    override fun getAllRecordingsFlow(): Flow<List<RecordingEntity>> = recordingDao.getAllRecordingsFlow()

    override suspend fun saveRecording(
        file: File,
        durationMs: Long,
        noteId: Long?
    ): RecordingEntity = withContext(Dispatchers.IO) {
        val policy = settingsRepository.getRetentionPolicy()
        val size = if (file.exists()) file.length() else 0L

        if (policy == RetentionPolicy.NEVER) {
            if (file.exists()) {
                file.delete()
            }
            val entity = RecordingEntity(
                noteId = noteId,
                filePath = "",
                fileSizeBytes = 0L,
                durationMs = durationMs,
                retentionPolicy = policy.value,
                expiresAt = null,
                isDeleted = true
            )
            val id = recordingDao.insert(entity)
            return@withContext entity.copy(id = id)
        }

        val expiresAt = when (policy) {
            RetentionPolicy.HOURS_24 -> System.currentTimeMillis() + (24 * 60 * 60 * 1000L)
            RetentionPolicy.DAYS_7 -> System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L)
            RetentionPolicy.FOREVER -> null
            RetentionPolicy.NEVER -> null
        }

        val entity = RecordingEntity(
            noteId = noteId,
            filePath = file.absolutePath,
            fileSizeBytes = size,
            durationMs = durationMs,
            retentionPolicy = policy.value,
            expiresAt = expiresAt,
            isDeleted = false
        )
        val id = recordingDao.insert(entity)
        entity.copy(id = id)
    }

    override suspend fun cleanExpiredRecordings(): Int = withContext(Dispatchers.IO) {
        val expired = recordingDao.getExpiredRecordings()
        var deletedCount = 0
        for (rec in expired) {
            val file = File(rec.filePath)
            if (file.exists()) {
                file.delete()
            }
            recordingDao.markDeleted(rec.id)
            deletedCount++
        }
        deletedCount
    }

    override suspend fun cleanAllRecordings(): Int = withContext(Dispatchers.IO) {
        val all = recordingDao.getAllRecordings()
        var count = 0
        for (rec in all) {
            val file = File(rec.filePath)
            if (file.exists()) {
                file.delete()
            }
            recordingDao.markDeleted(rec.id)
            count++
        }
        count
    }

    override suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        // The -wal and -shm sidecars can hold megabytes of committed data
        // between checkpoints, so counting only the main file understates what
        // Naomi actually occupies.
        val dbFile = context.getDatabasePath("naomi_knowledge.db")
        val dbSize = listOf(dbFile, File("${dbFile.path}-wal"), File("${dbFile.path}-shm"))
            .filter { it.exists() }
            .sumOf { it.length() }

        val audioSize = recordingDao.getTotalAudioSizeBytes()
        val noteCount = noteDao.getNoteCount()
        val allRecordings = recordingDao.getAllRecordings()
        val policy = settingsRepository.getRetentionPolicy()

        // There used to be a "Notes & Hierarchy" figure here computed as
        // noteCount * 512 — a made-up number presented as a measurement, and one
        // that double-counted bytes already inside the database file.
        val totalBytes = dbSize + audioSize

        StorageStats(
            databaseBytes = dbSize,
            audioBytes = audioSize,
            totalBytes = totalBytes,
            noteCount = noteCount,
            recordingCount = allRecordings.size,
            retentionPolicy = policy
        )
    }
}
