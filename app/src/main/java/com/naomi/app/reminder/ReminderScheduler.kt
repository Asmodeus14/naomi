package com.naomi.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.naomi.app.data.database.entities.TaskEntity

/**
 * Puts a task's deadline on the system clock.
 *
 * ## Why there are two kinds of alarm here
 *
 * For most of Naomi's life every alarm was **inexact**. `setAndAllowWhileIdle`
 * fires through Doze without `SCHEDULE_EXACT_ALARM`, and the system gives it a
 * window of up to an hour — a reminder for 09:00 can arrive at 10:00. That was
 * the right trade, because a deadline came from someone saying "tomorrow" and
 * the 09:00 was chosen by [com.naomi.app.ai.intelligence.TemporalParser], not by
 * the user. Being punctual to the minute about a time nobody specified is false
 * precision, and asking for a restricted permission to achieve it would take
 * something real in exchange for nothing.
 *
 * The old comment here said this would change the day Naomi could parse a spoken
 * clock time. It now can, so it has. When the user said "6 PM", an hour of slop
 * is a broken promise rather than a reasonable approximation, and the permission
 * buys something they actually asked for.
 *
 * The distinction is carried by [TaskEntity.hasExactTime] rather than inferred
 * here, because reminders are re-scheduled from the database after a reboot —
 * long after the sentence that produced them is gone.
 *
 * ## When the permission is refused
 *
 * Apps targeting SDK 34+ are denied `SCHEDULE_EXACT_ALARM` by default. Falling
 * back to an inexact alarm has to be silent and correct: a late reminder is a
 * disappointment, and a crash or a dropped one is a broken product.
 *
 * The task id is the request code, so scheduling the same task twice replaces
 * its alarm rather than stacking a second one.
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * Whether the system will currently honour an exact alarm.
     *
     * Checked at every schedule rather than cached: the user can revoke this in
     * Settings while the app is running, and a stale `true` means silently
     * throwing [SecurityException] on something they were promised.
     */
    fun canScheduleExact(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            // Before Android 12 there was no permission to be denied.
            true
        }
    }

    /** Whether this task is one the user would notice arriving an hour late. */
    fun wantsExact(task: TaskEntity): Boolean = task.hasExactTime && task.dueAt != null

    fun schedule(task: TaskEntity) {
        val dueAt = task.dueAt ?: return
        val manager = alarmManager ?: return
        val pending = pendingIntent(task, flags = PendingIntent.FLAG_UPDATE_CURRENT)

        val exact = wantsExact(task) && canScheduleExact()
        try {
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pending)
            }
        } catch (e: SecurityException) {
            // The permission was revoked between the check above and this call.
            // A late reminder is far better than none, so drop to inexact rather
            // than letting the task lose its alarm entirely.
            Log.w(TAG, "Exact alarms were refused; falling back to an inexact one", e)
            try {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pending)
            } catch (fallback: Exception) {
                Log.w(TAG, "Could not schedule a reminder for task ${task.id}", fallback)
            }
        } catch (e: Exception) {
            // Nothing the user can do about it and nothing worth interrupting
            // them for; the task itself is still saved and still visible.
            Log.w(TAG, "Could not schedule a reminder for task ${task.id}", e)
        }
    }

    fun cancel(taskId: Long) {
        val manager = alarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        manager.cancel(pending)
        pending.cancel()
    }

    private fun pendingIntent(task: TaskEntity, flags: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TASK_ID, task.id)
            putExtra(ReminderReceiver.EXTRA_TASK_TITLE, task.title)
            putExtra(ReminderReceiver.EXTRA_NOTE_ID, task.noteId)
        }
        return PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            // Immutable because nothing outside this app has any business
            // rewriting which task a reminder points at.
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val TAG = "ReminderScheduler"
    }
}
