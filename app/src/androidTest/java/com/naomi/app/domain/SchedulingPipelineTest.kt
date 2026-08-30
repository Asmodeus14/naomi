package com.naomi.app.domain

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.naomi.app.ai.speech.Transcript
import com.naomi.app.calendar.CalendarHandoff
import com.naomi.app.data.database.NaomiDatabase
import com.naomi.app.data.database.entities.TaskEntity
import com.naomi.app.data.repository.KnowledgeRepositoryImpl
import com.naomi.app.domain.intelligence.LocalHeuristicProvider
import com.naomi.app.domain.model.ProcessingStage
import com.naomi.app.domain.usecases.ProcessThoughtUseCase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/**
 * Saying something out loud, all the way to a row that an alarm or a calendar
 * can act on.
 *
 * The unit tests prove the parser and the classifier in isolation. This proves
 * they reach the database with the right shape — and in particular that the two
 * outcomes which leave the app, an alarm and a calendar screen, only happen for
 * the sentences that earned them.
 */
@RunWith(AndroidJUnit4::class)
class SchedulingPipelineTest {

    private lateinit var db: NaomiDatabase
    private lateinit var repository: KnowledgeRepositoryImpl
    private lateinit var processThought: ProcessThoughtUseCase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NaomiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = KnowledgeRepositoryImpl(db)
        processThought = ProcessThoughtUseCase(repository, listOf(LocalHeuristicProvider()))
    }

    @After
    fun tearDown() = db.close()

    private fun capture(text: String): List<TaskEntity> = runBlocking {
        val done = processThought(Transcript.of(text)).toList()
            .filterIsInstance<ProcessingStage.Done>()
            .single()
        done.notes.flatMap { repository.getTasksForNote(it.id) }
    }

    @Test
    fun a_spoken_clock_time_produces_a_reminder_that_can_be_exact() {
        val tasks = capture("remind me tomorrow at 6 PM to call Rahul")
        val reminder = tasks.firstOrNull { it.kind == TaskEntity.KIND_REMINDER }
        assertNotNull("no reminder was created: ${tasks.map { it.kind to it.title }}", reminder)
        assertTrue("a stated clock time was not recorded as exact", reminder!!.hasExactTime)
        assertEquals(18, hourOf(reminder.dueAt!!))
    }

    /**
     * "By Friday" is a deadline, not an appointment. The 09:00 is Naomi's, so
     * the alarm stays inexact — which is what it always was, and is why this
     * feature could be added without asking every user for a new permission.
     */
    @Test
    fun a_date_with_no_spoken_hour_stays_inexact() {
        val tasks = capture("I need to file the taxes by Friday")
        val task = tasks.single()
        assertEquals(TaskEntity.KIND_TASK, task.kind)
        assertFalse("an inferred 09:00 was recorded as exact", task.hasExactTime)
    }

    @Test
    fun an_occasion_at_a_stated_time_becomes_a_calendar_event() {
        val tasks = capture("meeting tomorrow at 6 PM with the team")
        val event = tasks.single()
        assertEquals(TaskEntity.KIND_EVENT, event.kind)
        assertNotNull(event.dueAt)
        assertNotNull("an event with no end time cannot go in a calendar", event.endAt)
        assertEquals("an hour is the default when nobody says", 60 * 60 * 1000L, event.endAt!! - event.dueAt!!)
        assertNull("nothing has been offered yet", event.calendarAddedAt)

        // The handoff needs no permission and is a plain ACTION_INSERT.
        val intent = CalendarHandoff.intentFor(event)
        assertNotNull(intent)
        assertEquals(android.content.Intent.ACTION_INSERT, intent!!.action)
    }

    @Test
    fun a_stated_duration_sets_the_end_time() {
        val event = capture("standup tomorrow at 9 AM for 30 minutes").single()
        assertEquals(30 * 60 * 1000L, event.endAt!! - event.dueAt!!)
    }

    /**
     * The two refusals that matter most, on the real pipeline. Both sentences
     * contain an occasion and a clock time, and neither may reach a calendar or
     * an alarm.
     */
    @Test
    fun a_habitual_statement_creates_nothing_to_act_on() {
        val tasks = capture("the standup meeting is usually at 6 PM")
        assertTrue(
            "a description of a routine became something actionable: ${tasks.map { it.kind }}",
            tasks.none { it.kind == TaskEntity.KIND_EVENT || it.kind == TaskEntity.KIND_REMINDER }
        )
    }

    @Test
    fun a_hedged_time_creates_nothing_to_act_on() {
        val tasks = capture("Rahul is coming over tomorrow around 6")
        assertTrue(
            "a hedge became something actionable: ${tasks.map { it.kind }}",
            tasks.none { it.kind == TaskEntity.KIND_EVENT || it.kind == TaskEntity.KIND_REMINDER }
        )
    }

    /**
     * A shared article can have the exact shape of an appointment — "the
     * hearing is tomorrow at 10 AM" — without anybody having agreed to
     * anything. Naomi will not open a calendar screen or set an alarm because
     * of a page somebody read.
     */
    @Test
    fun a_shared_page_cannot_put_things_in_the_calendar() = runBlocking {
        val done = processThought(
            Transcript.of("The hearing is tomorrow at 10 AM at the county court"),
            com.naomi.app.data.database.entities.MemoryEntryEntity.SOURCE_SHARED
        ).toList().filterIsInstance<ProcessingStage.Done>().single()

        val tasks = done.notes.flatMap { repository.getTasksForNote(it.id) }
        assertTrue(
            "a shared page produced something actionable: ${tasks.map { it.kind }}",
            tasks.none { it.kind == TaskEntity.KIND_EVENT || it.kind == TaskEntity.KIND_REMINDER }
        )
        assertTrue(
            "a shared page earned an exact alarm",
            tasks.none { it.hasExactTime }
        )
    }

    /** The hour is not Naomi's to invent. */
    @Test
    fun an_occasion_with_no_time_is_never_scheduled() {
        val tasks = capture("meeting tomorrow")
        assertTrue(
            "an event was created at an hour nobody said: ${tasks.map { it.kind }}",
            tasks.none { it.kind == TaskEntity.KIND_EVENT }
        )
    }

    private fun hourOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
}
