package com.naomi.app.presentation.hierarchy

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naomi.app.NaomiApp
import com.naomi.app.data.database.entities.NoteEntity
import com.naomi.app.data.database.entities.TopicEntity
import com.naomi.app.domain.model.TimelineEntry
import com.naomi.app.domain.model.TopicNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The tree screen's state.
 *
 * [Loading] exists so the screen stops announcing "Nothing organized yet." for
 * the frame before the first query returns — an empty list is not the same
 * claim as an empty database.
 */
sealed interface TopicTreeUiState {
    data object Loading : TopicTreeUiState
    data class Loaded(val roots: List<TopicNode>) : TopicTreeUiState
    data class Failed(val message: String) : TopicTreeUiState
}

/** The detail screen for one topic. */
sealed interface TopicDetailUiState {
    data object Loading : TopicDetailUiState
    data class Loaded(
        val topic: TopicEntity,
        val summary: String,
        val notes: List<NoteEntity>,
        val subtopics: List<TopicEntity>,
        /** Everything said under this topic and below it, newest first. */
        val timeline: List<TimelineEntry> = emptyList()
    ) : TopicDetailUiState

    data object NotFound : TopicDetailUiState
    data class Failed(val message: String) : TopicDetailUiState
}

class TopicHierarchyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as NaomiApp).knowledgeRepository

    private val _treeState = MutableStateFlow<TopicTreeUiState>(TopicTreeUiState.Loading)
    val treeState: StateFlow<TopicTreeUiState> = _treeState.asStateFlow()

    private val _detailState = MutableStateFlow<TopicDetailUiState>(TopicDetailUiState.Loading)
    val detailState: StateFlow<TopicDetailUiState> = _detailState.asStateFlow()

    init {
        loadTopicTree()
    }

    fun loadTopicTree() {
        viewModelScope.launch {
            _treeState.value = try {
                TopicTreeUiState.Loaded(repository.buildTopicTree())
            } catch (e: Exception) {
                Log.e(TAG, "Could not build the topic tree", e)
                TopicTreeUiState.Failed("Your topics couldn't be loaded.")
            }
        }
    }

    fun loadTopicDetail(topicId: Long) {
        viewModelScope.launch {
            _detailState.value = TopicDetailUiState.Loading
            _detailState.value = try {
                val topic = repository.getTopicById(topicId)
                if (topic == null) {
                    TopicDetailUiState.NotFound
                } else {
                    TopicDetailUiState.Loaded(
                        topic = topic,
                        summary = repository.getTopicSummary(topicId),
                        notes = repository.getNotesForTopic(topicId),
                        subtopics = repository.getSubtopics(topicId),
                        timeline = repository.getTimelineForTopic(topicId)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not load topic $topicId", e)
                TopicDetailUiState.Failed("This topic couldn't be opened.")
            }
        }
    }

    private companion object {
        const val TAG = "TopicHierarchyViewModel"
    }
}
