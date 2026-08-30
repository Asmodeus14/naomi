package com.naomi.app.domain.usecases

import android.util.Log
import com.naomi.app.domain.repository.KnowledgeRepository
import com.naomi.app.reminder.ReminderScheduler

/**
 * Brings the system's alarms in line with the tasks that actually exist.
 *
 * Written as a reconcile rather than as "schedule this one task" because alarms
 * are lost on reboot and can drift out of step with the database in half a dozen
 * ways — a task completed, a memory deleted, a deadline that has simply passed.
 * One idempotent pass that can be run after capture, on boot, or on launch is
 * far easier to keep correct than a scheduling call at every mutation site, and
 * a missed reminder is a broken promise the user only discovers too late.
 */
class SyncRemindersUseCase(
    private val repository: KnowledgeRepository,
    private val scheduler: ReminderScheduler
) {

    /**
     * @param scheduled how many reminders are now waiting to fire
     * @param wantsExactPermission whether something is waiting that the user
     *        gave a real clock time for, which the system will currently deliver
     *        only approximately. Reported rather than acted on: the moment of
     *        asking for a permission belongs to the UI, not to a use case.
     */
    data class Result(val scheduled: Int, val wantsExactPermission: Boolean)

    suspend operator fun invoke(): Result {
        val tasks = try {
            repository.getPendingTasks()
        } catch (e: Exception) {
            Log.e(TAG, "Could not read tasks; leaving existing alarms alone", e)
            return Result(0, false)
        }

        val now = System.currentTimeMillis()
        val canBeExact = scheduler.canScheduleExact()
        var scheduled = 0
        var wantsExact = false

        for (task in tasks) {
            val dueAt = task.dueAt
            // A deadline already in the past is not a reminder, it is a
            // notification about something the user already knows. The task
            // stays; only the alarm goes.
            if (dueAt == null || dueAt <= now) {
                scheduler.cancel(task.id)
            } else {
                scheduler.schedule(task)
                scheduled++
                if (!canBeExact && scheduler.wantsExact(task)) wantsExact = true
            }
        }
        return Result(scheduled, wantsExact)
    }

    private companion object {
        const val TAG = "SyncReminders"
    }
}
