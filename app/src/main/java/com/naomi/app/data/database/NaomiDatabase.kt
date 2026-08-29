package com.naomi.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.naomi.app.data.database.dao.*
import com.naomi.app.data.database.entities.*

@Database(
    entities = [
        TopicEntity::class,
        NoteEntity::class,
        MemoryEntryEntity::class,
        TaskEntity::class,
        EntityRefEntity::class,
        TopicRelationshipEntity::class,
        RecordingEntity::class,
        SettingEntity::class
    ],
    version = 3,
    // Schemas are checked in so migrations can be tested against them, and so a
    // reviewer can see exactly what changed between versions.
    exportSchema = true
)
abstract class NaomiDatabase : RoomDatabase() {

    abstract fun topicDao(): TopicDao
    abstract fun noteDao(): NoteDao
    abstract fun memoryEntryDao(): MemoryEntryDao
    abstract fun taskDao(): TaskDao
    abstract fun entityRefDao(): EntityRefDao
    abstract fun topicRelationshipDao(): TopicRelationshipDao
    abstract fun recordingDao(): RecordingDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var INSTANCE: NaomiDatabase? = null

        /**
         * v1 -> v2.
         *
         * Adds `tasks.dueAt` so deadlines are real instants rather than the
         * literal word the speaker used, and adds the foreign keys and indices
         * that `notes.subtopicId`, `tasks.topicId` and `entity_refs.topicId`
         * were missing — those columns pointed at topics with nothing enforcing
         * that the topic still existed.
         *
         * SQLite cannot add a foreign key to an existing table, so the three
         * tables are rebuilt. Rows that reference a topic which no longer exists
         * are repaired first, because the new constraints would otherwise reject
         * data that the old schema happily allowed.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // --- repair dangling references left by the unconstrained schema ---
                db.execSQL("UPDATE notes SET subtopicId = NULL WHERE subtopicId IS NOT NULL AND subtopicId NOT IN (SELECT id FROM topics)")
                db.execSQL("DELETE FROM notes WHERE topicId NOT IN (SELECT id FROM topics)")
                db.execSQL("DELETE FROM tasks WHERE noteId NOT IN (SELECT id FROM notes) OR topicId NOT IN (SELECT id FROM topics)")
                db.execSQL("DELETE FROM entity_refs WHERE noteId NOT IN (SELECT id FROM notes) OR topicId NOT IN (SELECT id FROM topics)")

                // --- notes: add FK + index on subtopicId ---
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notes_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `topicId` INTEGER NOT NULL,
                        `subtopicId` INTEGER,
                        `title` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `rawTranscript` TEXT NOT NULL,
                        `cleanTranscript` TEXT NOT NULL,
                        `idea` TEXT,
                        `decision` TEXT,
                        `isCorrection` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`topicId`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`subtopicId`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `notes_new` (
                        id, topicId, subtopicId, title, summary, rawTranscript,
                        cleanTranscript, idea, decision, isCorrection, createdAt, updatedAt
                    )
                    SELECT id, topicId, subtopicId, title, summary, rawTranscript,
                           cleanTranscript, idea, decision, isCorrection, createdAt, updatedAt
                    FROM `notes`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `notes`")
                db.execSQL("ALTER TABLE `notes_new` RENAME TO `notes`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_topicId` ON `notes` (`topicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_subtopicId` ON `notes` (`subtopicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_createdAt` ON `notes` (`createdAt`)")

                // --- tasks: add dueAt, add FK + index on topicId, drop isCompleted index ---
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tasks_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `noteId` INTEGER NOT NULL,
                        `topicId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `deadline` TEXT,
                        `dueAt` INTEGER,
                        `isCompleted` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`topicId`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                // dueAt is left NULL for pre-existing rows: the old `deadline`
                // text was relative to the day it was spoken, so there is no
                // honest way to resolve it now. The phrase is preserved.
                db.execSQL(
                    """
                    INSERT INTO `tasks_new` (
                        id, noteId, topicId, title, deadline, dueAt, isCompleted, createdAt
                    )
                    SELECT id, noteId, topicId, title, deadline, NULL, isCompleted, createdAt
                    FROM `tasks`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `tasks`")
                db.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_noteId` ON `tasks` (`noteId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_topicId` ON `tasks` (`topicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_dueAt` ON `tasks` (`dueAt`)")

                // --- entity_refs: add FK + index on topicId ---
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `entity_refs_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `noteId` INTEGER NOT NULL,
                        `topicId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `detail` TEXT,
                        FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`topicId`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `entity_refs_new` (id, noteId, topicId, name, type, detail)
                    SELECT id, noteId, topicId, name, type, detail FROM `entity_refs`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `entity_refs`")
                db.execSQL("ALTER TABLE `entity_refs_new` RENAME TO `entity_refs`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entity_refs_noteId` ON `entity_refs` (`noteId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entity_refs_topicId` ON `entity_refs` (`topicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entity_refs_name` ON `entity_refs` (`name`)")
            }
        }

        /**
         * v2 -> v3.
         *
         * Introduces `memory_entries`, which turns a note from a single
         * utterance into a subject with a history.
         *
         * Every existing note is backfilled with one entry reconstructed from
         * what it already holds, so a user who upgrades sees their memories with
         * an accurate first history line rather than an empty timeline. The
         * entry's timestamp is the note's own `createdAt`, not now — the
         * history has to be true.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memory_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `noteId` INTEGER NOT NULL,
                        `summary` TEXT NOT NULL,
                        `transcript` TEXT NOT NULL,
                        `idea` TEXT,
                        `decision` TEXT,
                        `source` TEXT NOT NULL,
                        `sourceUrl` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_entries_noteId` ON `memory_entries` (`noteId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_entries_createdAt` ON `memory_entries` (`createdAt`)")

                db.execSQL(
                    """
                    INSERT INTO `memory_entries` (
                        noteId, summary, transcript, idea, decision, source, sourceUrl, createdAt
                    )
                    SELECT id, summary, cleanTranscript, idea, decision, 'SPOKEN', NULL, createdAt
                    FROM `notes`
                    """.trimIndent()
                )
            }
        }

        /**
         * Turns on cascade enforcement. The `ON DELETE` rules declared on these
         * entities are inert unless SQLite is told to honour them, and the
         * pragma resets on every connection, so it belongs in `onOpen`.
         */
        private val enforceForeignKeys = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }

        fun getInstance(context: Context): NaomiDatabase {
            INSTANCE?.let { return it }
            return synchronized(this) {
                INSTANCE?.let { return it }
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NaomiDatabase::class.java,
                    "naomi_knowledge.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(enforceForeignKeys)
                    // Deliberately no fallbackToDestructiveMigration. In an app
                    // whose whole promise is remembering things, a failed upgrade
                    // must surface as a crash we fix — never as a silently
                    // emptied database.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
