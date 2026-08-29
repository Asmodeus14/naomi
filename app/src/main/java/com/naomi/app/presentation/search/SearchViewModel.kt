package com.naomi.app.presentation.search

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naomi.app.NaomiApp
import com.naomi.app.domain.model.SearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

/** The kinds of result a user can narrow to. */
enum class SearchFilter(val label: String) {
    ALL("ALL"),
    NOTES("MEMORIES"),
    TOPICS("TOPICS"),
    TASKS("TASKS")
}

/**
 * What the search screen is showing.
 *
 * [Idle] and [Empty] are separate states because they need different words:
 * "search your memories" invites, "nothing found" reports. Collapsing them into
 * one nullable result made the screen say "Nothing found." before the user had
 * typed anything.
 */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Searching : SearchUiState
    data class Results(val result: SearchResult) : SearchUiState
    data object Empty : SearchUiState
    data class Failed(val message: String) : SearchUiState
}

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val searchUseCase = (application as NaomiApp).searchMemoriesUseCase

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedFilter = MutableStateFlow(SearchFilter.ALL)
    val selectedFilter: StateFlow<SearchFilter> = _selectedFilter.asStateFlow()

    /**
     * Each keystroke used to launch its own coroutine with nothing cancelling
     * the previous one, so a slow query for "ri" could land after "ring" and
     * overwrite it. `flatMapLatest` cancels the outstanding search, and the
     * debounce means a typed word costs one query rather than one per letter.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SearchUiState> = _query
        .debounce { if (it.isBlank()) 0L else DEBOUNCE_MS }
        .flatMapLatest { raw ->
            val trimmed = raw.trim()
            if (trimmed.length < MIN_QUERY_LENGTH) {
                flowOf(SearchUiState.Idle)
            } else {
                flow {
                    emit(SearchUiState.Searching)
                    val result = searchUseCase(trimmed)
                    emit(
                        if (result.isEmpty) SearchUiState.Empty
                        else SearchUiState.Results(result)
                    )
                }.catch { e ->
                    Log.e(TAG, "Search for '$trimmed' failed", e)
                    emit(SearchUiState.Failed("Something went wrong searching your memories."))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUiState.Idle)

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun setFilter(filter: SearchFilter) {
        _selectedFilter.value = filter
    }

    private companion object {
        const val TAG = "SearchViewModel"

        /** Long enough to skip intermediate keystrokes, short enough to feel live. */
        const val DEBOUNCE_MS = 250L

        /** Single characters match nearly everything and are not worth querying. */
        const val MIN_QUERY_LENGTH = 2
    }
}
