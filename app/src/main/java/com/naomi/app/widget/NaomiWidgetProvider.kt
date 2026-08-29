package com.naomi.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.naomi.app.NaomiApp
import com.naomi.app.R
import com.naomi.app.audio.AudioProcessingService
import com.naomi.app.data.database.entities.NoteEntity
import com.naomi.app.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NaomiWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_TAP = "com.naomi.app.widget.ACTION_WIDGET_TAP"
        const val ACTION_START_AMBIENT = "com.naomi.app.widget.ACTION_START_AMBIENT"
        const val EXTRA_NOTE_ID = "EXTRA_NOTE_ID"

        private const val PREFS = "naomi_widget"
        private const val KEY_STATE = "state"
        private const val KEY_TITLE = "title"
        private const val KEY_SUBTITLE = "subtitle"

        /** How long SUCCESS or ERROR lingers before the orb returns to rest. */
        private const val RESET_DELAY_MS = 3_500L

        private val providerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private val handler = Handler(Looper.getMainLooper())
        private var resetRunnable: Runnable? = null

        private fun prefs(context: Context) =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /**
         * The widget's state, persisted rather than held in a static.
         *
         * A companion `var` is scoped to the app's process, but the rendered
         * pixels outlive it. After a process kill the launcher still showed
         * "Listening" while this read IDLE, so the next tap took the wrong
         * branch and tried to start a capture that was already running.
         */
        private fun readState(context: Context): WidgetState {
            val name = prefs(context).getString(KEY_STATE, null) ?: return WidgetState.IDLE
            return runCatching { WidgetState.valueOf(name) }.getOrDefault(WidgetState.IDLE)
        }

        private fun readTitle(context: Context): String? =
            prefs(context).getString(KEY_TITLE, null)

        private fun readSubtitle(context: Context): String? =
            prefs(context).getString(KEY_SUBTITLE, null)

        fun updateWidgetState(
            context: Context,
            state: WidgetState,
            title: String? = null,
            subtitle: String? = null
        ) {
            prefs(context).edit()
                .putString(KEY_STATE, state.name)
                .putString(KEY_TITLE, title)
                .putString(KEY_SUBTITLE, subtitle)
                .apply()

            // Cancel any pending auto-reset
            resetRunnable?.let { handler.removeCallbacks(it) }

            if (state == WidgetState.SUCCESS || state == WidgetState.ERROR) {
                val appContext = context.applicationContext
                resetRunnable = Runnable { updateWidgetState(appContext, WidgetState.IDLE) }
                handler.postDelayed(resetRunnable!!, RESET_DELAY_MS)
            }

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, NaomiWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (allWidgetIds.isEmpty()) return

            providerScope.launch {
                // The recent-note list cannot change while a capture is in
                // flight, so re-reading it on every tick of the recording timer
                // was a database round-trip per second for an identical answer.
                val recentNotes =
                    if (state == WidgetState.LISTENING) cachedNotes(context)
                    else getRecentNotesSafely(context).also { cachedRecentNotes = it }

                for (widgetId in allWidgetIds) {
                    val views = buildAdaptiveRemoteViews(context, widgetId, appWidgetManager, recentNotes)
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            }
        }

        @Volatile
        private var cachedRecentNotes: List<NoteEntity>? = null

        private suspend fun cachedNotes(context: Context): List<NoteEntity> =
            cachedRecentNotes ?: getRecentNotesSafely(context).also { cachedRecentNotes = it }

        private suspend fun getRecentNotesSafely(context: Context): List<NoteEntity> = withContext(Dispatchers.IO) {
            try {
                com.naomi.app.data.database.NaomiDatabase.getInstance(context).noteDao().getRecentNotes(2)
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun buildAdaptiveRemoteViews(
            context: Context,
            widgetId: Int,
            appWidgetManager: AppWidgetManager,
            recentNotes: List<NoteEntity>
        ): RemoteViews {
            val smallViews = buildSmallRemoteViews(context, recentNotes)
            val mediumViews = buildMediumRemoteViews(context, recentNotes)
            val largeViews = buildLargeRemoteViews(context, recentNotes)

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RemoteViews(
                    mapOf(
                        SizeF(110f, 70f) to smallViews,
                        SizeF(210f, 70f) to mediumViews,
                        SizeF(210f, 180f) to largeViews
                    )
                )
            } else {
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 130)
                val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 90)

                when {
                    minHeight >= 160 -> largeViews
                    minWidth >= 200 -> mediumViews
                    else -> smallViews
                }
            }
        }

        private fun getOrbColor(context: Context, state: WidgetState): Int {
            return when (state) {
                WidgetState.IDLE -> ContextCompat.getColor(context, R.color.widget_orb_idle)
                WidgetState.LISTENING -> ContextCompat.getColor(context, R.color.naomi_listening_red)
                WidgetState.PROCESSING -> ContextCompat.getColor(context, R.color.naomi_amber)
                WidgetState.SUCCESS -> ContextCompat.getColor(context, R.color.naomi_success_green)
                WidgetState.ERROR -> ContextCompat.getColor(context, R.color.naomi_listening_red)
            }
        }

        private fun formatTimestamp(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            return when {
                diff < 60_000 -> "Just now"
                diff < 3600_000 -> "${diff / 60_000}m ago"
                else -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
            }
        }

        private fun buildSmallRemoteViews(context: Context, recentNotes: List<NoteEntity>): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.naomi_widget_small)
            val currentState = readState(context)
            val displayTitle = readTitle(context) ?: currentState.title
            val displaySubtitle = readSubtitle(context) ?: currentState.subtitle

            views.setTextViewText(R.id.widget_small_icon, currentState.symbol)
            views.setTextColor(R.id.widget_small_icon, getOrbColor(context, currentState))
            views.setTextViewText(R.id.widget_small_title, displayTitle)
            views.setTextViewText(R.id.widget_small_subtitle, displaySubtitle)
            views.setContentDescription(R.id.widget_small_root, currentState.contentDescription)

            val tapIntent = Intent(context, NaomiWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_TAP
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                101,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_small_root, pendingIntent)

            return views
        }

        private fun buildMediumRemoteViews(context: Context, recentNotes: List<NoteEntity>): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.naomi_widget_medium)
            val currentState = readState(context)
            val displayTitle = readTitle(context) ?: currentState.title
            val displaySubtitle = readSubtitle(context) ?: currentState.subtitle

            // Left Capture Zone
            views.setTextViewText(R.id.widget_medium_icon, currentState.symbol)
            views.setTextColor(R.id.widget_medium_icon, getOrbColor(context, currentState))
            views.setTextViewText(R.id.widget_medium_title, displayTitle)
            views.setTextViewText(R.id.widget_medium_subtitle, displaySubtitle)
            views.setContentDescription(R.id.widget_medium_capture_zone, currentState.contentDescription)

            val tapIntent = Intent(context, NaomiWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_TAP
            }
            val tapPendingIntent = PendingIntent.getBroadcast(
                context,
                102,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_medium_capture_zone, tapPendingIntent)

            // Right Note Section
            val latestNote = recentNotes.firstOrNull()
            if (latestNote != null) {
                views.setTextViewText(
                    R.id.widget_medium_note_label,
                    if (currentState == WidgetState.SUCCESS) "JUST REMEMBERED" else "LAST REMEMBERED"
                )
                views.setTextViewText(R.id.widget_medium_note_title, latestNote.title)
                views.setTextViewText(R.id.widget_medium_note_time, formatTimestamp(latestNote.createdAt))

                val noteIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_NOTE_ID, latestNote.id)
                }
                val notePendingIntent = PendingIntent.getActivity(
                    context,
                    latestNote.id.toInt(),
                    noteIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_medium_note_container, notePendingIntent)
            } else {
                views.setTextViewText(R.id.widget_medium_note_label, "MEMORY")
                views.setTextViewText(R.id.widget_medium_note_title, "No memories yet")
                views.setTextViewText(R.id.widget_medium_note_time, "Tap orb to begin")

                val appIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val appPendingIntent = PendingIntent.getActivity(
                    context,
                    103,
                    appIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_medium_note_container, appPendingIntent)
            }

            return views
        }

        private fun buildLargeRemoteViews(context: Context, recentNotes: List<NoteEntity>): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.naomi_widget_large)
            val currentState = readState(context)
            val displaySubtitle = readSubtitle(context) ?: currentState.subtitle

            // Center Hero Capture
            views.setTextViewText(R.id.widget_large_icon, currentState.symbol)
            views.setTextColor(R.id.widget_large_icon, getOrbColor(context, currentState))
            views.setTextViewText(R.id.widget_large_capture_label, displaySubtitle)
            views.setContentDescription(R.id.widget_large_capture_button, currentState.contentDescription)

            val tapIntent = Intent(context, NaomiWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_TAP
            }
            val tapPendingIntent = PendingIntent.getBroadcast(
                context,
                104,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_large_capture_button, tapPendingIntent)

            // Ambient Mode Shortcut
            val ambientIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_SCREEN", "ambient")
            }
            val ambientPendingIntent = PendingIntent.getActivity(
                context,
                105,
                ambientIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_large_btn_ambient, ambientPendingIntent)

            // Populate Recent Note 1
            if (recentNotes.isNotEmpty()) {
                val note1 = recentNotes[0]
                views.setViewVisibility(R.id.widget_large_note_1, View.VISIBLE)
                views.setTextViewText(R.id.widget_large_note_1_title, note1.title)
                views.setTextViewText(R.id.widget_large_note_1_time, formatTimestamp(note1.createdAt))

                val note1Intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_NOTE_ID, note1.id)
                }
                val note1PendingIntent = PendingIntent.getActivity(
                    context,
                    note1.id.toInt(),
                    note1Intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_large_note_1, note1PendingIntent)
            } else {
                views.setViewVisibility(R.id.widget_large_note_1, View.GONE)
            }

            // Populate Recent Note 2
            if (recentNotes.size > 1) {
                val note2 = recentNotes[1]
                views.setViewVisibility(R.id.widget_large_note_2, View.VISIBLE)
                views.setTextViewText(R.id.widget_large_note_2_title, note2.title)
                views.setTextViewText(R.id.widget_large_note_2_time, formatTimestamp(note2.createdAt))

                val note2Intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_NOTE_ID, note2.id)
                }
                val note2PendingIntent = PendingIntent.getActivity(
                    context,
                    note2.id.toInt(),
                    note2Intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_large_note_2, note2PendingIntent)
            } else {
                views.setViewVisibility(R.id.widget_large_note_2, View.GONE)
            }

            return views
        }
    }

    /**
     * A broadcast receiver is considered finished the moment its callback
     * returns, so launching a coroutine and returning left the database read
     * racing against the process being killed — the widget would sometimes
     * simply never redraw. `goAsync()` keeps the receiver alive until the work
     * completes.
     */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        providerScope.launch {
            try {
                val recentNotes = getRecentNotesSafely(context)
                cachedRecentNotes = recentNotes
                for (widgetId in appWidgetIds) {
                    val views = buildAdaptiveRemoteViews(context, widgetId, appWidgetManager, recentNotes)
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pending = goAsync()
        providerScope.launch {
            try {
                val recentNotes = getRecentNotesSafely(context)
                cachedRecentNotes = recentNotes
                val views = buildAdaptiveRemoteViews(context, appWidgetId, appWidgetManager, recentNotes)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_TAP) {
            when (readState(context)) {
                WidgetState.IDLE, WidgetState.ERROR -> {
                    // Open Quick Capture directly or start foreground capture
                    val appIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("TRIGGER_CAPTURE", true)
                    }
                    context.startActivity(appIntent)
                }
                WidgetState.LISTENING -> {
                    AudioProcessingService.stopCapture(context)
                }
                else -> {
                    val appIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    context.startActivity(appIntent)
                }
            }
        }
    }
}

