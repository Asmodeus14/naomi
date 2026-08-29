package com.naomi.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.naomi.app.data.database.entities.TaskEntity

/**
 * Puts a task's deadline on the system clock.
 *
 * Alarms are deliberately **inexact**. `setAndAllowWhileIdle` fires through Doze
 * without `SCHEDULE_EXACT_ALARM`, which the app therefore does not request.
 *
 * Measured on device, the system gives these a one-hour window: a reminder for
 * 09:00 can arrive any time up to 10:00. That is a real cost and it is worth
 * being clear about — but the alternative is false precision. A deadline here
 * comes from someone saying "tomorrow", and the 09:00 was chosen by
 * TemporalParser, not by the user. Requesting a restricted permission to be
 * punctual to the minute about a time nobody specified would take something
 * from the user in exchange for nothing, and would need a settings screen that
 * §42 says should not exist.
 *
 * If Naomi ever parses a spoken clock time — "the meeting at 3pm" — that is the
 * point at which exact alarms would start being worth their cost.
 *
 * The task id is the request code, so scheduling the same task twice replaces
 * its alarm rather than stacking a second one.
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun schedule(task: TaskEntity) {
        val dueAt = task.dueAt ?: return
        val manager = alarmManager ?: return

        try {
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                dueAt,
                pendingIntent(task, flags = PendingIntent.FLAG_UPDATE_CURRENT)
            )
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
