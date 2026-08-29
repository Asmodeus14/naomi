package com.naomi.app.data.database.dao

import androidx.room.*
import com.naomi.app.data.database.entities.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE noteId = :noteId ORDER BY id ASC")
    fun getTasksForNoteFlow(noteId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE noteId = :noteId ORDER BY id ASC")
    suspend fun getTasksForNote(noteId: Long): List<TaskEntity>

    /**
     * `dueAt IS NULL` leads the sort so undated tasks fall to the bottom.
     * Ordering by `dueAt` alone would float them to the top, because SQLite
     * sorts NULL below every real value.
     */
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY dueAt IS NULL, dueAt ASC, createdAt DESC")
    fun getPendingTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE topicId = :topicId ORDER BY isCompleted ASC, dueAt IS NULL, dueAt ASC")
    suspend fun getTasksForTopic(topicId: Long): List<TaskEntity>

    /** [query] must arrive with LIKE wildcards already escaped by the caller. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE title LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY isCompleted ASC, dueAt IS NULL, dueAt ASC
        LIMIT :limit
        """
    )
    suspend fun searchTasks(query: String, limit: Int = 50): List<TaskEntity>

    @Query("UPDATE tasks SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun setTaskCompleted(id: Long, isCompleted: Boolean)
}
