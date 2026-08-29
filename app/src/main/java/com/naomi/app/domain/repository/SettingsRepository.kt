package com.naomi.app.domain.repository

import com.naomi.app.domain.model.AppThemeMode
import com.naomi.app.domain.model.RetentionPolicy
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getRetentionPolicyFlow(): Flow<RetentionPolicy>
    suspend fun getRetentionPolicy(): RetentionPolicy
    suspend fun setRetentionPolicy(policy: RetentionPolicy)
    fun isOnboardingCompletedFlow(): Flow<Boolean>
    suspend fun isOnboardingCompleted(): Boolean
    suspend fun setOnboardingCompleted(completed: Boolean)
    fun getThemeModeFlow(): Flow<AppThemeMode>
    suspend fun getThemeMode(): AppThemeMode
    suspend fun setThemeMode(mode: AppThemeMode)
}
