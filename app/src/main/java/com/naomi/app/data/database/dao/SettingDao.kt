package com.naomi.app.data.database.dao

import androidx.room.*
import com.naomi.app.data.database.entities.SettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: SettingEntity)

    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    fun getFlow(key: String): Flow<String?>
}
