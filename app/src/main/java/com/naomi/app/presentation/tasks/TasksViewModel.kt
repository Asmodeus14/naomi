package com.naomi.app.presentation.tasks

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naomi.app.NaomiApp
import com.naomi.app.data.database.entities.TaskEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TasksViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NaomiApp
    private val repository = app.knowledgeRepository

    val allTasks: StateFlow<List<TaskEntity>> = repository
        .getAllTasksFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleTask(taskId: Long, currentCompleted: Boolean) {
        viewModelScope.launch {
            repository.setTaskCompleted(taskId, !currentCompleted)
            // Being reminded to do something already ticked off is the fastest
            // way to teach someone to ignore an app's notifications.
            try {
                app.syncRemindersUseCase()
            } catch (e: Exception) {
                Log.e(TAG, "Could not update reminders after toggling $taskId", e)
            }
        }
    }

    private companion object {
        const val TAG = "TasksViewModel"
    }
}
