package com.naomi.app.data.database.dao

import androidx.room.*
import com.naomi.app.data.database.entities.EntityRefEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntityRefDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entityRef: EntityRefEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entityRefs: List<EntityRefEntity>)

    @Query("SELECT * FROM entity_refs WHERE noteId = :noteId")
    fun getEntitiesForNoteFlow(noteId: Long): Flow<List<EntityRefEntity>>

    @Query("SELECT * FROM entity_refs WHERE noteId = :noteId")
    suspend fun getEntitiesForNote(noteId: Long): List<EntityRefEntity>

    /** [query] must arrive with LIKE wildcards already escaped by the caller. */
    @Query("SELECT * FROM entity_refs WHERE name LIKE '%' || :query || '%' ESCAPE '\' LIMIT :limit")
    suspend fun searchEntities(query: String, limit: Int = 30): List<EntityRefEntity>
}
