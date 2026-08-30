package com.naomi.app.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves an upgrade keeps the user's memories.
 *
 * Naomi has no `fallbackToDestructiveMigration`, which is the right call for an
 * app whose only promise is remembering — but it means a broken migration is a
 * crash on launch with no recovery, for someone whose data is already there.
 * These tests are the only thing standing between a schema change and that.
 *
 * They are instrumented rather than local because a migration is SQLite
 * behaviour, and the parts most likely to be wrong — column order, index names,
 * what a rebuilt table does to existing rows — are exactly the parts a fake
 * in-memory database would not reproduce.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private companion object {
        const val TEST_DB = "migration-test.db"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NaomiDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * The whole chain, on a database with real rows in it.
     *
     * Running the whole chain in one go rather than testing each step in
     * isolation is deliberate: users upgrade from whatever version they happen
     * to be on, and migrations that each pass alone can still fail in sequence.
     */
    @Test
    fun migrates_2_to_6_keeping_existing_memories() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO topics (id, name, normalizedName, parentId, depth, createdAt, updatedAt) " +
                    "VALUES (1, 'Nyx', 'nyx', NULL, 0, 1000, 1000)"
            )
            db.execSQL(
                "INSERT INTO notes (id, topicId, subtopicId, title, summary, rawTranscript, " +
                    "cleanTranscript, idea, decision, isCorrection, createdAt, updatedAt) " +
                    "VALUES (1, 1, NULL, 'Ring Buffer', 'a summary', 'raw words', " +
                    "'clean words', NULL, NULL, 0, 1000, 1000)"
            )
            db.execSQL(
                "INSERT INTO tasks (id, noteId, topicId, title, deadline, dueAt, isCompleted, createdAt) " +
                    "VALUES (1, 1, 1, 'Profile the fence waits', 'Friday', 2000, 0, 1000)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 6, true,
            NaomiDatabase.MIGRATION_2_3,
            NaomiDatabase.MIGRATION_3_4,
            NaomiDatabase.MIGRATION_4_5,
            NaomiDatabase.MIGRATION_5_6
        )

        // The note survived, with its words intact.
        db.query("SELECT title, rawTranscript FROM notes WHERE id = 1").use { c ->
            assertTrue("the existing note was lost in migration", c.moveToFirst())
            assertEquals("Ring Buffer", c.getString(0))
            assertEquals("raw words", c.getString(1))
        }

        // v2 -> v3 backfills one history entry per existing note, dated to the
        // note rather than to now, so an upgraded memory has a true timeline.
        // v4 -> v5 adds rawTranscript, which must be null on a backfilled row:
        // nothing was corrected before the feature existed.
        db.query("SELECT transcript, createdAt, rawTranscript FROM memory_entries WHERE noteId = 1").use { c ->
            assertTrue("no history entry was backfilled", c.moveToFirst())
            assertEquals("clean words", c.getString(0))
            assertEquals(1000L, c.getLong(1))
            assertTrue("an upgraded entry claims it was corrected", c.isNull(2))
        }

        // v3 -> v4 adds the vocabulary table, empty and usable.
        db.query("SELECT COUNT(*) FROM vocabulary").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }

        // v5 -> v6 splits tasks into three kinds. An existing row must land on
        // the behaviour it already had: a plain task whose hour Naomi inferred,
        // and therefore an inexact alarm rather than a newly exact one.
        db.query("SELECT kind, hasExactTime, endAt, calendarAddedAt FROM tasks WHERE id = 1").use { c ->
            assertTrue("the existing task was lost in migration", c.moveToFirst())
            assertEquals("TASK", c.getString(0))
            assertEquals(0, c.getInt(1))
            assertTrue(c.isNull(2))
            assertTrue(c.isNull(3))
        }
    }

    /**
     * `runMigrationsAndValidate` above already compares the result against the
     * schema Room generates, so this is really about the unique index: a
     * duplicate term must be rejected by the database rather than relied upon
     * to be filtered in Kotlin.
     */
    @Test
    fun vocabulary_normalized_is_unique_after_migration() {
        helper.createDatabase(TEST_DB, 3).close()
        val db = helper.runMigrationsAndValidate(
            TEST_DB, 6, true,
            NaomiDatabase.MIGRATION_3_4,
            NaomiDatabase.MIGRATION_4_5,
            NaomiDatabase.MIGRATION_5_6
        )

        db.execSQL(
            "INSERT INTO vocabulary (term, normalized, phoneticKey, kind, source, occurrences, lastSeenAt, isBlocked) " +
                "VALUES ('Nyx', 'nyx', 'NKS', 'PROJECT', 'TOPIC', 1, 1000, 0)"
        )

        val duplicate = runCatching {
            db.execSQL(
                "INSERT INTO vocabulary (term, normalized, phoneticKey, kind, source, occurrences, lastSeenAt, isBlocked) " +
                    "VALUES ('NYX', 'nyx', 'NKS', 'PROJECT', 'USER', 1, 2000, 0)"
            )
        }
        assertTrue("a duplicate term was allowed in", duplicate.isFailure)
    }

    /** A fresh install must land on the current schema without any migration. */
    @Test
    fun creates_version_6_from_scratch() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.query("SELECT COUNT(*) FROM vocabulary").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            db.query("SELECT rawTranscript FROM memory_entries LIMIT 0").use { c ->
                assertEquals(1, c.columnCount)
            }
        }
    }
}
