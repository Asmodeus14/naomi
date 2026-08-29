package com.naomi.app

import android.app.Application
import com.naomi.app.ai.speech.AndroidSpeechToTextEngine
import com.naomi.app.ai.speech.SpeechToTextEngine
import com.naomi.app.audio.AmbientSessionManager
import com.naomi.app.audio.AudioRecorder
import com.naomi.app.data.database.NaomiDatabase
import com.naomi.app.data.repository.KnowledgeRepositoryImpl
import com.naomi.app.data.repository.RecordingRepositoryImpl
import com.naomi.app.data.repository.SettingsRepositoryImpl
import com.naomi.app.domain.intelligence.GeminiNanoProvider
import com.naomi.app.domain.intelligence.IntelligenceProvider
import com.naomi.app.domain.intelligence.LocalHeuristicProvider
import com.naomi.app.domain.repository.KnowledgeRepository
import com.naomi.app.domain.repository.RecordingRepository
import com.naomi.app.domain.repository.SettingsRepository
import com.naomi.app.domain.usecases.*
import com.naomi.app.reminder.ReminderReceiver
import com.naomi.app.reminder.ReminderScheduler

class NaomiApp : Application() {

    lateinit var database: NaomiDatabase
        private set
    lateinit var knowledgeRepository: KnowledgeRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var recordingRepository: RecordingRepository
        private set
    lateinit var speechEngine: SpeechToTextEngine
        private set
    lateinit var audioRecorder: AudioRecorder
        private set
    lateinit var ambientSessionManager: AmbientSessionManager
        private set

    /** Exposed so Settings can report which provider is actually running. */
    lateinit var nanoProvider: GeminiNanoProvider
        private set
    lateinit var intelligenceProviders: List<IntelligenceProvider>
        private set

    // Use cases
    lateinit var processThoughtUseCase: ProcessThoughtUseCase
        private set
    lateinit var processAmbientChunkUseCase: ProcessAmbientChunkUseCase
        private set
    lateinit var searchMemoriesUseCase: SearchMemoriesUseCase
        private set
    lateinit var askNaomiUseCase: AskNaomiUseCase
        private set
    lateinit var syncRemindersUseCase: SyncRemindersUseCase
        private set
    lateinit var exportMarkdownUseCase: ExportMarkdownUseCase
        private set
    lateinit var cleanStorageUseCase: CleanStorageUseCase
        private set

    companion object {
        lateinit var instance: NaomiApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = NaomiDatabase.getInstance(this)
        settingsRepository = SettingsRepositoryImpl(database.settingDao())
        recordingRepository = RecordingRepositoryImpl(this, database, settingsRepository)
        knowledgeRepository = KnowledgeRepositoryImpl(database)

        speechEngine = AndroidSpeechToTextEngine(this)
        audioRecorder = AudioRecorder(this)

        // Ordered best-effort first, guaranteed last. Gemini Nano runs inside
        // the platform's AICore service on devices that support it; the
        // heuristic engine backs every other device and every failure. Neither
        // touches the network — the app holds no INTERNET permission.
        nanoProvider = GeminiNanoProvider()
        intelligenceProviders = listOf(nanoProvider, LocalHeuristicProvider())

        processThoughtUseCase = ProcessThoughtUseCase(knowledgeRepository, intelligenceProviders)
        processAmbientChunkUseCase = ProcessAmbientChunkUseCase(knowledgeRepository)
        searchMemoriesUseCase = SearchMemoriesUseCase(knowledgeRepository)
        askNaomiUseCase = AskNaomiUseCase(knowledgeRepository)
        syncRemindersUseCase = SyncRemindersUseCase(knowledgeRepository, ReminderScheduler(this))
        ReminderReceiver.ensureChannel(this)
        exportMarkdownUseCase = ExportMarkdownUseCase(knowledgeRepository)
        cleanStorageUseCase = CleanStorageUseCase(recordingRepository)

        ambientSessionManager = AmbientSessionManager(speechEngine, processAmbientChunkUseCase)
    }
}
