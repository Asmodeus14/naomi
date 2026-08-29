package com.naomi.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.naomi.app.R
import com.naomi.app.presentation.MainActivity

/**
 * Shows the one notification Naomi ever posts on its own.
 *
 * §18 asks for notifications to be sparing, so this is the whole of it: a task
 * the user themselves asked to be reminded about, at the time they said, once.
 * There are no digests, streaks, re-engagement nudges or "you haven't opened
 * Naomi in a while" messages, and there is no code path that could add one.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TASK_TITLE)?.takeIf { it.isNotBlank() } ?: return
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (taskId == -1L) return

        // Posting without the runtime permission throws on API 33+. The task is
        // still in the app either way; a silently dropped notification is a
        // worse outcome than a crash only in that nobody notices, so it is
        // checked rather than caught.
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        ensureChannel(context)

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (noteId != -1L) putExtra("EXTRA_NOTE_ID", noteId)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            // The body says where it came from, because a reminder that appears
            // with no explanation reads as the app having decided something on
            // the user's behalf. It did not; they said this out loud.
            .setContentText("You asked Naomi to remind you.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(taskId.toInt(), notification)
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"
        const val EXTRA_NOTE_ID = "note_id"

        const val CHANNEL_ID = "naomi_reminders"

        /**
         * DEFAULT rather than HIGH: a reminder should arrive with a sound, not
         * take over the screen. The user can silence the channel in system
         * settings, which is where that control belongs.
         */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Deadlines you mentioned out loud."
                }
            )
        }
    }
}
