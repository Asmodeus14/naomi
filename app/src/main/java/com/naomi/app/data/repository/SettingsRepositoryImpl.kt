package com.naomi.app.data.repository

import com.naomi.app.data.database.dao.SettingDao
import com.naomi.app.data.database.entities.SettingEntity
import com.naomi.app.domain.model.AppThemeMode
import com.naomi.app.domain.model.RetentionPolicy
import com.naomi.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(
    private val settingDao: SettingDao
) : SettingsRepository {

    private val KEY_RETENTION = "retention_policy"
    private val KEY_ONBOARDING = "onboarding_completed"
    private val KEY_THEME_MODE = "theme_mode"

    override fun getRetentionPolicyFlow(): Flow<RetentionPolicy> {
        return settingDao.getFlow(KEY_RETENTION).map { value ->
            RetentionPolicy.fromValue(value)
        }
    }

    override suspend fun getRetentionPolicy(): RetentionPolicy = withContext(Dispatchers.IO) {
        val value = settingDao.get(KEY_RETENTION)
        RetentionPolicy.fromValue(value)
    }

    override suspend fun setRetentionPolicy(policy: RetentionPolicy) = withContext(Dispatchers.IO) {
        settingDao.set(SettingEntity(KEY_RETENTION, policy.value))
    }

    override fun isOnboardingCompletedFlow(): Flow<Boolean> {
        return settingDao.getFlow(KEY_ONBOARDING).map { value ->
            value == "true"
        }
    }

    /**
     * Read once at launch to pick the start destination. A Flow cannot answer
     * this: the first emission arrives after composition, so Home would render
     * and then be replaced by onboarding.
     */
    override suspend fun isOnboardingCompleted(): Boolean = withContext(Dispatchers.IO) {
        settingDao.get(KEY_ONBOARDING) == "true"
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) = withContext(Dispatchers.IO) {
        settingDao.set(SettingEntity(KEY_ONBOARDING, completed.toString()))
    }

    override fun getThemeModeFlow(): Flow<AppThemeMode> {
        return settingDao.getFlow(KEY_THEME_MODE).map { value ->
            AppThemeMode.fromValue(value)
        }
    }

    override suspend fun getThemeMode(): AppThemeMode = withContext(Dispatchers.IO) {
        val value = settingDao.get(KEY_THEME_MODE)
        AppThemeMode.fromValue(value)
    }

    override suspend fun setThemeMode(mode: AppThemeMode) = withContext(Dispatchers.IO) {
        settingDao.set(SettingEntity(KEY_THEME_MODE, mode.value))
    }
}
