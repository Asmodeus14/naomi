package com.naomi.app.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.naomi.app.audio.AudioProcessingService
import com.naomi.app.presentation.MainActivity

/**
 * Handles a tap on the widget's orb.
 *
 * ## Why this is its own receiver
 *
 * `ACTION_WIDGET_TAP` used to live in [NaomiWidgetProvider]'s intent filter. An
 * `AppWidgetProvider` has to be `exported="true"` so the launcher can send it
 * widget broadcasts — which meant **any app on the device could start or stop a
 * recording** with a single broadcast, on an app whose only sensitive capability
 * is the microphone. Moving the action to a non-exported receiver addressed by
 * explicit component removes that entirely: the only sender that can reach here
 * is Naomi's own `PendingIntent`.
 *
 * The tap is still delivered — a `PendingIntent` runs with the *creating* app's
 * identity, so it reaches a non-exported receiver perfectly well.
 */
class WidgetTapReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WIDGET_TAP) return

        when (NaomiWidgetProvider.readState(context)) {
            WidgetState.LISTENING -> AudioProcessingService.stopCapture(context)
            WidgetState.IDLE, WidgetState.ERROR -> openApp(context, triggerCapture = true)
            else -> openApp(context, triggerCapture = false)
        }
    }

    /**
     * Opens Naomi, via a `PendingIntent` rather than `context.startActivity`.
     *
     * Android 10 blocks activity starts from the background, and a broadcast
     * receiver with the app fully backgrounded is exactly that case — the
     * direct call this replaced could silently do nothing, which for a widget
     * tap reads as the app being broken. Sending a `PendingIntent` carries the
     * launcher's own start privilege through.
     */
    private fun openApp(context: Context, triggerCapture: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (triggerCapture) putExtra("TRIGGER_CAPTURE", true)
        }
        val pending = PendingIntent.getActivity(
            context,
            if (triggerCapture) RC_CAPTURE else RC_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            pending.send()
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "Could not open Naomi from a widget tap", e)
        }
    }

    companion object {
        const val ACTION_WIDGET_TAP = "com.naomi.app.widget.ACTION_WIDGET_TAP"

        private const val TAG = "WidgetTap"
        private const val RC_CAPTURE = 201
        private const val RC_OPEN = 202
    }
}
