package com.naomi.app.presentation.ambient

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.naomi.app.NaomiApp
import com.naomi.app.audio.AmbientSessionState
import kotlinx.coroutines.flow.StateFlow

class AmbientViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = (application as NaomiApp).ambientSessionManager
    val sessionState: StateFlow<AmbientSessionState> = sessionManager.sessionState

    fun toggleSession() {
        if (sessionState.value.isActive) {
            sessionManager.stopSession()
        } else {
            sessionManager.startSession()
        }
    }
}
