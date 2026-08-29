package com.naomi.app.domain.usecases

import com.naomi.app.domain.model.StorageStats
import com.naomi.app.domain.repository.RecordingRepository

class CleanStorageUseCase(
    private val recordingRepository: RecordingRepository
) {
    suspend fun cleanExpired(): Int {
        return recordingRepository.cleanExpiredRecordings()
    }

    suspend fun cleanAllAudio(): Int {
        return recordingRepository.cleanAllRecordings()
    }

    suspend fun getStats(): StorageStats {
        return recordingRepository.getStorageStats()
    }
}
