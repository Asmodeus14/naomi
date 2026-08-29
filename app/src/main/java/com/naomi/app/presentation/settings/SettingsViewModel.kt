package com.naomi.app.presentation.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naomi.app.NaomiApp
import com.naomi.app.domain.model.AppThemeMode
import com.naomi.app.domain.model.RetentionPolicy
import com.naomi.app.domain.model.StorageStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Whether on-device understanding is running through Gemini Nano or the
 * built-in heuristics.
 *
 * Surfaced in Settings because "which model read my note" is exactly the kind
 * of thing a local-first app should not leave the user guessing about. Both
 * options run on the device; neither sends anything anywhere.
 */
sealed interface NanoStatus {
    data object Checking : NanoStatus
    data object Active : NanoStatus
    data object Downloadable : NanoStatus
    data object Downloading : NanoStatus
    data object Unsupported : NanoStatus
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NaomiApp
    private val settingsRepository = app.settingsRepository
    private val recordingRepository = app.recordingRepository
    private val exportMarkdownUseCase = app.exportMarkdownUseCase
    private val nanoProvider = app.nanoProvider

    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _retentionPolicy = MutableStateFlow(RetentionPolicy.NEVER)
    val retentionPolicy: StateFlow<RetentionPolicy> = _retentionPolicy.asStateFlow()

    private val _storageStats = MutableStateFlow<StorageStats?>(null)
    val storageStats: StateFlow<StorageStats?> = _storageStats.asStateFlow()

    private val _nanoStatus = MutableStateFlow<NanoStatus>(NanoStatus.Checking)
    val nanoStatus: StateFlow<NanoStatus> = _nanoStatus.asStateFlow()

    /** One-shot text for a snackbar; cleared once shown. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        loadSettings()
        refreshNanoStatus()
    }

    fun loadSettings() {
        viewModelScope.launch {
            try {
                _themeMode.value = settingsRepository.getThemeMode()
                _retentionPolicy.value = settingsRepository.getRetentionPolicy()
                _storageStats.value = recordingRepository.getStorageStats()
            } catch (e: Exception) {
                Log.e(TAG, "Could not load settings", e)
                _message.value = "Some settings couldn't be loaded."
            }
        }
    }

    fun refreshNanoStatus() {
        viewModelScope.launch {
            _nanoStatus.value = try {
                if (nanoProvider.isAvailable()) NanoStatus.Active else NanoStatus.Unsupported
            } catch (e: Exception) {
                Log.w(TAG, "Could not read Nano status", e)
                NanoStatus.Unsupported
            }
        }
    }

    /**
     * Asks AICore to fetch the model. Deliberately user-initiated: a system
     * download must never happen behind a capture the user is waiting on.
     */
    fun downloadNano() {
        viewModelScope.launch {
            _nanoStatus.value = NanoStatus.Downloading
            _nanoStatus.value = try {
                if (nanoProvider.ensureReady()) NanoStatus.Active else NanoStatus.Unsupported
            } catch (e: Exception) {
                Log.w(TAG, "Nano download failed", e)
                _message.value = "Gemini Nano isn't available on this device."
                NanoStatus.Unsupported
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            try {
                settingsRepository.setThemeMode(mode)
                _themeMode.value = mode
            } catch (e: Exception) {
                Log.e(TAG, "Could not save theme", e)
                _message.value = "Your theme choice couldn't be saved."
            }
        }
    }

    fun setRetentionPolicy(policy: RetentionPolicy) {
        viewModelScope.launch {
            try {
                settingsRepository.setRetentionPolicy(policy)
                _retentionPolicy.value = policy
                _storageStats.value = recordingRepository.getStorageStats()
            } catch (e: Exception) {
                Log.e(TAG, "Could not save retention policy", e)
                _message.value = "That setting couldn't be saved."
            }
        }
    }

    fun cleanAudio() {
        viewModelScope.launch {
            try {
                recordingRepository.cleanAllRecordings()
                _storageStats.value = recordingRepository.getStorageStats()
                _message.value = "Stored audio deleted."
            } catch (e: Exception) {
                Log.e(TAG, "Could not delete recordings", e)
                _message.value = "Audio couldn't be deleted."
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    suspend fun getMarkdownExport(): String = try {
        exportMarkdownUseCase()
    } catch (e: Exception) {
        Log.e(TAG, "Export failed", e)
        _message.value = "Your memories couldn't be exported."
        ""
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
