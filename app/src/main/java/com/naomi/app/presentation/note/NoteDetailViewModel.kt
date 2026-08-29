package com.naomi.app.presentation.note

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naomi.app.NaomiApp
import com.naomi.app.data.database.entities.TopicEntity
import com.naomi.app.domain.model.NoteDetail
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * What the detail screen can be showing.
 *
 * A nullable [NoteDetail] could not distinguish "still loading" from "this
 * memory no longer exists", so a stale widget deep-link to a deleted note left
 * the screen spinning forever.
 */
sealed interface NoteDetailUiState {
    data object Loading : NoteDetailUiState
    data class Loaded(val detail: NoteDetail) : NoteDetailUiState
    data object NotFound : NoteDetailUiState
    data class Failed(val message: String) : NoteDetailUiState
}

class NoteDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as NaomiApp).knowledgeRepository

    private val _uiState = MutableStateFlow<NoteDetailUiState>(NoteDetailUiState.Loading)
    val uiState: StateFlow<NoteDetailUiState> = _uiState.asStateFlow()

    val allTopics: StateFlow<List<TopicEntity>> = repository
        .getAllTopicsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadNote(noteId: Long) {
        viewModelScope.launch {
            _uiState.value = NoteDetailUiState.Loading
            _uiState.value = fetch(noteId)
        }
    }

    private suspend fun fetch(noteId: Long): NoteDetailUiState = try {
        repository.getNoteDetail(noteId)
            ?.let { NoteDetailUiState.Loaded(it) }
            ?: NoteDetailUiState.NotFound
    } catch (e: Exception) {
        Log.e(TAG, "Could not read memory $noteId", e)
        NoteDetailUiState.Failed("This memory could not be opened.")
    }

    fun toggleTask(taskId: Long, isCompleted: Boolean) {
        val loaded = _uiState.value as? NoteDetailUiState.Loaded ?: return
        viewModelScope.launch {
            // Applied optimistically so the checkbox responds on the same frame
            // as the tap; the write is small and local, so a rollback path would
            // be more machinery than the risk warrants.
            _uiState.value = NoteDetailUiState.Loaded(
                loaded.detail.copy(
                    tasks = loaded.detail.tasks.map {
                        if (it.id == taskId) it.copy(isCompleted = !isCompleted) else it
                    }
                )
            )
            try {
                repository.setTaskCompleted(taskId, !isCompleted)
            } catch (e: Exception) {
                Log.e(TAG, "Could not update task $taskId", e)
                _uiState.value = loaded
            }
        }
    }

    fun moveTopic(noteId: Long, newTopicId: Long) {
        viewModelScope.launch {
            try {
                repository.moveNoteToTopic(noteId, newTopicId)
            } catch (e: Exception) {
                Log.e(TAG, "Could not move memory $noteId", e)
            }
            _uiState.value = fetch(noteId)
        }
    }

    fun createTopicAndMove(noteId: Long, newTopicName: String) {
        viewModelScope.launch {
            try {
                val created = repository.insertOrGetTopic(newTopicName.trim())
                repository.moveNoteToTopic(noteId, created.id)
            } catch (e: Exception) {
                Log.e(TAG, "Could not create topic '$newTopicName'", e)
            }
            _uiState.value = fetch(noteId)
        }
    }

    fun deleteNote(noteId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.deleteNote(noteId)
                onDone()
            } catch (e: Exception) {
                Log.e(TAG, "Could not delete memory $noteId", e)
                _uiState.value = NoteDetailUiState.Failed("This memory could not be deleted.")
            }
        }
    }

    private companion object {
        const val TAG = "NoteDetailViewModel"
    }
}
