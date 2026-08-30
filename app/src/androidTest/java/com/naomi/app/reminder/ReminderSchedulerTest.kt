package com.naomi.app.reminder

import android.app.PendingIntent
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.naomi.app.data.database.entities.TaskEntity
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A reminder must survive the permission being refused.
 *
 * Apps targeting SDK 34+ are denied `SCHEDULE_EXACT_ALARM` by default, so the
 * refused path is the *common* one, not the edge case. An hour of slop is a
 * disappointment; a dropped alarm or a `SecurityException` on the way to the
 * home screen is a broken product.
 *
 * This runs against the real `AlarmManager` rather than a fake, because what is
 * being tested is precisely how the platform responds to a permission this
 * process may or may not hold — which a fake would only ever tell us what we
 * already assumed.
 */
@RunWith(AndroidJUnit4::class)
class ReminderSchedulerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scheduler = ReminderScheduler(context)

    private val exactTask = TaskEntity(
        id = 90_001,
        noteId = 1,
        topicId = 1,
        title = "Call Rahul",
        dueAt = System.currentTimeMillis() + 60 * 60 * 1000,
        hasExactTime = true,
        kind = TaskEntity.KIND_REMINDER
    )

    private val inferredTask = exactTask.copy(
        id = 90_002,
        title = "File the taxes",
        hasExactTime = false,
        kind = TaskEntity.KIND_TASK
    )

    @After
    fun tearDown() {
        scheduler.cancel(exactTask.id)
        scheduler.cancel(inferredTask.id)
    }

    /**
     * The load-bearing one. Whatever the system has decided about exact alarms,
     * an alarm exists afterwards — [ReminderScheduler.schedule] catches the
     * `SecurityException` and reschedules inexactly rather than giving up.
     */
    @Test
    fun a_spoken_time_gets_an_alarm_whether_or_not_exact_alarms_are_allowed() {
        scheduler.schedule(exactTask)
        assertNotNull(
            "a reminder for a time the user actually said was not scheduled " +
                "(canScheduleExact=${scheduler.canScheduleExact()})",
            existingAlarmFor(exactTask.id)
        )
    }

    @Test
    fun an_inferred_time_still_gets_its_inexact_alarm() {
        scheduler.schedule(inferredTask)
        assertNotNull(existingAlarmFor(inferredTask.id))
    }

    /** Only a time the user spoke is worth the permission. */
    @Test
    fun only_a_spoken_time_asks_to_be_exact() {
        assert(scheduler.wantsExact(exactTask))
        assert(!scheduler.wantsExact(inferredTask))
        assert(!scheduler.wantsExact(exactTask.copy(dueAt = null)))
    }

    @Test
    fun cancelling_removes_the_alarm() {
        scheduler.schedule(exactTask)
        assertNotNull(existingAlarmFor(exactTask.id))
        scheduler.cancel(exactTask.id)
        assertNull("a cancelled reminder is still pending", existingAlarmFor(exactTask.id))
    }

    /**
     * `FLAG_NO_CREATE` returns null unless a matching PendingIntent already
     * exists, which is the only way from inside the app to ask whether an alarm
     * was really registered.
     */
    private fun existingAlarmFor(taskId: Long): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
}
