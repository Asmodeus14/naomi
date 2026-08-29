package com.naomi.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.naomi.app.NaomiApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Puts the reminders back after a reboot.
 *
 * Android drops every pending alarm when the device restarts, so without this a
 * user who said "remind me on Friday" on Tuesday and restarted their phone on
 * Wednesday would simply never be reminded — silently, with the task still
 * sitting in the app looking as though it were handled.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val app = context.applicationContext as? NaomiApp ?: return

        // The work outlives onReceive, so the process has to be held open for
        // it. Launching and returning would let Android kill us mid-query.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.syncRemindersUseCase()
            } catch (e: Exception) {
                Log.e(TAG, "Could not restore reminders after boot", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
