package com.naomi.app.calendar

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.naomi.app.data.database.entities.TaskEntity

/**
 * Hands an event to whatever calendar app the user already uses.
 *
 * ## Why a handoff and not an insert
 *
 * `CalendarContract` would let Naomi write the event directly, and doing so
 * would cost `READ_CALENDAR` and `WRITE_CALENDAR`. Those two permissions are not
 * a fee for writing one row — they are read access to every appointment the user
 * has ever had, on an app whose entire claim is that it does not collect things
 * about you. Naomi's permission list stays at six, and the *user's* calendar app
 * does the writing, in its own UI, with the details pre-filled and a save button
 * they press themselves.
 *
 * There is a real cost to this: Naomi cannot read back whether the event was
 * saved, because the user may have cancelled the screen. [TaskEntity.calendarAddedAt]
 * therefore records that Naomi *offered*, which is the only thing it honestly
 * knows.
 *
 * ## Why the caller has to be in the foreground
 *
 * Android 10 blocks activity starts from the background. A widget capture or an
 * ambient session that tried to launch the calendar would produce nothing at
 * all, silently — so [isAvailable] exists for callers to check, and the
 * fallback is a one-tap row on the memory rather than a pretence that the event
 * was created.
 */
object CalendarHandoff {

    /**
     * The intent that opens the user's calendar on a new-event screen.
     *
     * `ACTION_INSERT` on [CalendarContract.Events.CONTENT_URI] is the documented
     * public handoff and needs no permission at all.
     */
    fun intentFor(task: TaskEntity, memorySummary: String? = null): Intent? {
        val begin = task.dueAt ?: return null
        val end = task.endAt ?: (begin + DEFAULT_DURATION_MS)

        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, task.title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            if (!memorySummary.isNullOrBlank()) {
                // Enough to recognise the event, not the whole transcript. This
                // text leaves Naomi for another app and possibly for a synced
                // account, so it carries the least that still makes sense.
                putExtra(CalendarContract.Events.DESCRIPTION, memorySummary.take(MAX_DESCRIPTION))
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Whether anything on this device can receive the handoff.
     *
     * A device with no calendar app is unusual but real — some ROMs ship
     * without one — and launching into nothing would throw
     * [android.content.ActivityNotFoundException] at the user.
     */
    fun isAvailable(context: Context, task: TaskEntity): Boolean {
        val intent = intentFor(task) ?: return false
        return intent.resolveActivity(context.packageManager) != null
    }

    /** An hour, which is what a calendar assumes when nobody says otherwise. */
    private const val DEFAULT_DURATION_MS = 60 * 60 * 1000L
    private const val MAX_DESCRIPTION = 500
}
