package com.naomi.app.presentation.ask

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naomi.app.NaomiApp
import com.naomi.app.domain.model.Answer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AskUiState {
    data object Idle : AskUiState
    data object Thinking : AskUiState
    data class Answered(val answer: Answer) : AskUiState
    data class Failed(val message: String) : AskUiState
}

class AskViewModel(application: Application) : AndroidViewModel(application) {

    private val askNaomi = (application as NaomiApp).askNaomiUseCase

    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    private val _uiState = MutableStateFlow<AskUiState>(AskUiState.Idle)
    val uiState: StateFlow<AskUiState> = _uiState.asStateFlow()

    fun onQuestionChange(value: String) {
        _question.value = value
        // Editing the question invalidates the answer on screen. Leaving the old
        // one visible under a new question reads as a reply to that question.
        if (_uiState.value !is AskUiState.Idle) _uiState.value = AskUiState.Idle
    }

    /**
     * Asked on submit rather than as the user types. A question is a whole
     * thought, and answering half of one would show Naomi confidently replying
     * about "the ring" while "buffer" is still being typed.
     */
    fun ask() {
        val asked = _question.value.trim()
        if (asked.isBlank()) return

        viewModelScope.launch {
            _uiState.value = AskUiState.Thinking
            _uiState.value = try {
                AskUiState.Answered(askNaomi(asked))
            } catch (e: Exception) {
                Log.e(TAG, "Could not answer '$asked'", e)
                AskUiState.Failed("Naomi couldn't look that up.")
            }
        }
    }

    fun clear() {
        _question.value = ""
        _uiState.value = AskUiState.Idle
    }

    private companion object {
        const val TAG = "AskViewModel"
    }
}
