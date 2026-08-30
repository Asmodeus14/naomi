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
import com.naomi.app.R
import com.naomi.app.data.database.entities.NoteEntity
import com.naomi.app.data.database.entities.TaskEntity
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
        const val EXTRA_NOTE_ID = "EXTRA_NOTE_ID"

        private const val PREFS = "naomi_widget"
        private const val KEY_STATE = "state"
        private const val KEY_TITLE = "title"
        private const val KEY_SUBTITLE = "subtitle"

        /** How long SUCCESS or ERROR lingers before the orb returns to rest. */
        private const val RESET_DELAY_MS = 3_500L

        /**
         * Request codes for the PendingIntents this file creates.
         *
         * Note ids used to be used directly as request codes alongside the
         * literals 101-105, so a memory whose id happened to be 103 silently
         * replaced the "open the app" intent with a link to itself. Note
         * intents are now offset past any literal.
         */
        private const val RC_TAP_SMALL = 101
        private const val RC_TAP_MEDIUM = 102
        private const val RC_OPEN_APP = 103
        private const val RC_TAP_LARGE = 104
        private const val RC_AMBIENT = 105
        private const val RC_NOTE_BASE = 1_000_000

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
        internal fun readState(context: Context): WidgetState {
            val name = prefs(context).getString(KEY_STATE, null) ?: return WidgetState.IDLE
            return runCatching { WidgetState.valueOf(name) }.getOrDefault(WidgetState.IDLE)
        }

        private fun readTitle(context: Context): String? =
            prefs(context).getString(KEY_TITLE, null)

        private fun readSubtitle(context: Context): String? =
            prefs(context).getString(KEY_SUBTITLE, null)

        private fun widgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, NaomiWidgetProvider::class.java))

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
            val allWidgetIds = widgetIds(context)
            if (allWidgetIds.isEmpty()) return

            providerScope.launch {
                // The recent-note list cannot change while a capture is in
                // flight, so re-reading it on every tick of the recording timer
                // was a database round-trip per second for an identical answer.
                val recentNotes =
                    if (state == WidgetState.LISTENING) cachedNotes(context)
                    else getRecentNotesSafely(context).also { cachedRecentNotes = it }
                val nextTask = getNextTaskSafely(context)

                for (widgetId in allWidgetIds) {
                    val views =
                        buildAdaptiveRemoteViews(context, widgetId, appWidgetManager, recentNotes, nextTask)
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            }
        }

        /**
         * Redraws every placed widget with current data.
         *
         * A widget with a configuration activity never receives the initial
         * `APPWIDGET_UPDATE` broadcast, so the configuration screen has to ask
         * for the first real render itself.
         */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = widgetIds(context)
            if (ids.isEmpty()) return

            providerScope.launch {
                val recentNotes = getRecentNotesSafely(context).also { cachedRecentNotes = it }
                val nextTask = getNextTaskSafely(context)
                for (widgetId in ids) {
                    manager.updateAppWidget(
                        widgetId,
                        buildAdaptiveRemoteViews(context, widgetId, manager, recentNotes, nextTask)
                    )
                }
            }
        }

        /**
         * Updates only the ticking timer, once a second, while recording.
         *
         * A full update at this rate is a preferences write, an IPC to fetch
         * the widget ids, three complete layout builds, five `PendingIntent`
         * allocations and a full binder transaction carrying every view — all
         * so one line of text can go from 00:07 to 00:08.
         * `partiallyUpdateAppWidget` sends the one changed string and leaves
         * everything else, including the click handlers, exactly as it is.
         */
        fun updateElapsed(context: Context, elapsed: String) {
            prefs(context).edit().putString(KEY_SUBTITLE, elapsed).apply()

            val manager = AppWidgetManager.getInstance(context)
            for (widgetId in widgetIds(context)) {
                val views = RemoteViews(context.packageName, R.layout.naomi_widget_small)
                views.setTextViewText(R.id.widget_small_subtitle, elapsed)
                val medium = RemoteViews(context.packageName, R.layout.naomi_widget_medium)
                medium.setTextViewText(R.id.widget_medium_subtitle, elapsed)
                val large = RemoteViews(context.packageName, R.layout.naomi_widget_large)
                large.setTextViewText(R.id.widget_large_capture_label, elapsed)

                // Which layout is actually on screen is the host's business, so
                // all three ids are sent; a partial update silently ignores an
                // id the current layout does not contain.
                runCatching {
                    manager.partiallyUpdateAppWidget(widgetId, views)
                    manager.partiallyUpdateAppWidget(widgetId, medium)
                    manager.partiallyUpdateAppWidget(widgetId, large)
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

        /**
         * The next thing due, for a widget configured to show a task rather
         * than a memory.
         *
         * Read in the same pass as the notes so a widget's content choice costs
         * one extra query per refresh at most, not one per widget.
         */
        private suspend fun getNextTaskSafely(context: Context): TaskEntity? = withContext(Dispatchers.IO) {
            try {
                com.naomi.app.data.database.NaomiDatabase.getInstance(context)
                    .taskDao().getPendingTasks().firstOrNull { it.dueAt != null }
            } catch (e: Exception) {
                null
            }
        }

        fun buildAdaptiveRemoteViews(
            context: Context,
            widgetId: Int,
            appWidgetManager: AppWidgetManager,
            recentNotes: List<NoteEntity>,
            nextTask: TaskEntity? = null
        ): RemoteViews {
            val config = WidgetConfigStore.load(context, widgetId)

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RemoteViews(
                    mapOf(
                        SizeF(110f, 110f) to buildSmallRemoteViews(context, config),
                        SizeF(220f, 110f) to buildMediumRemoteViews(context, config, recentNotes, nextTask),
                        SizeF(220f, 220f) to buildLargeRemoteViews(context, config, recentNotes, nextTask)
                    )
                )
            } else {
                // Only the chosen one is built here. All three used to be
                // constructed unconditionally, so this branch did three times
                // the work and discarded two of the results.
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 130)
                val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 90)

                when {
                    minHeight >= 160 && minWidth >= 200 ->
                        buildLargeRemoteViews(context, config, recentNotes, nextTask)
                    minWidth >= 200 -> buildMediumRemoteViews(context, config, recentNotes, nextTask)
                    else -> buildSmallRemoteViews(context, config)
                }
            }
        }

        /**
         * Applies one widget's chosen appearance to its background layer.
         *
         * The asymmetry here is the whole trick. Under [WidgetTheme.SYSTEM] the
         * drawable holds a *colour reference*, which whichever process inflates
         * it resolves against its own configuration — so `values-night` applies
         * on its own and flipping the system theme redraws correctly. Under an
         * explicit override the colour is baked in, which is fine precisely
         * because an override is static by definition.
         *
         * That is also the fix for a real bug: colours used to be resolved in
         * *this* process and sent as integers, so a widget kept yesterday's
         * theme until something unrelated happened to update it.
         */
        private fun styleBackground(context: Context, views: RemoteViews, config: WidgetConfig) {
            views.setImageViewResource(R.id.widget_bg, config.backgroundDrawable())
            views.setInt(R.id.widget_bg, "setImageAlpha", config.alpha)

            if (config.theme != WidgetTheme.SYSTEM) {
                val background = if (config.theme == WidgetTheme.DARK) {
                    ContextCompat.getColor(context, R.color.widget_background_dark)
                } else {
                    ContextCompat.getColor(context, R.color.widget_background_light)
                }
                views.setInt(R.id.widget_bg, "setColorFilter", background)
            }
        }

        /**
         * Foreground colours for an explicit theme override.
         *
         * Deliberately not called under [WidgetTheme.SYSTEM]: leaving the
         * layout's own `@color` references alone is what lets the host resolve
         * them, and setting them here would reintroduce the staleness the
         * background fix just removed. Text is never faded — [WidgetConfig.MIN_OPACITY]
         * keeps the background readable instead, because dimming the text is
         * what actually destroys contrast.
         */
        private fun styleText(context: Context, views: RemoteViews, config: WidgetConfig, ids: TextIds) {
            if (config.theme == WidgetTheme.SYSTEM) {
                if (config.flattensText) flattenSystemText(views, ids)
                return
            }

            if (config.flattensText) {
                val primary = ContextCompat.getColor(
                    context,
                    if (config.theme == WidgetTheme.DARK) R.color.widget_text_primary_dark
                    else R.color.widget_text_primary_light
                )
                for (id in ids.all()) views.setTextColor(id, primary)
                return
            }

            val dark = config.theme == WidgetTheme.DARK
            val primary = ContextCompat.getColor(
                context,
                if (dark) R.color.widget_text_primary_dark else R.color.widget_text_primary_light
            )
            val secondary = ContextCompat.getColor(
                context,
                if (dark) R.color.widget_text_secondary_dark else R.color.widget_text_secondary_light
            )
            val tertiary = ContextCompat.getColor(
                context,
                if (dark) R.color.widget_text_tertiary_dark else R.color.widget_text_tertiary_light
            )

            for (id in ids.primary) views.setTextColor(id, primary)
            for (id in ids.secondary) views.setTextColor(id, secondary)
            for (id in ids.tertiary) views.setTextColor(id, tertiary)
        }

        /**
         * Raises the whole hierarchy to the primary colour under System theme,
         * without resolving a colour in this process.
         *
         * `setColorStateList` passes a colour *resource*, which the host
         * resolves against its own configuration — so this keeps the property
         * that makes System theme correct in the first place. It arrived in
         * API 31; below that the designed hierarchy stays, which is a real if
         * small gap and is preferable to sending a colour that goes stale the
         * moment the user flips their theme.
         */
        private fun flattenSystemText(views: RemoteViews, ids: TextIds) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            for (id in ids.secondary + ids.tertiary) {
                views.setColorStateList(id, "setTextColor", R.color.widget_text_primary)
            }
        }

        private class TextIds(
            val primary: IntArray = intArrayOf(),
            val secondary: IntArray = intArrayOf(),
            val tertiary: IntArray = intArrayOf()
        ) {
            fun all(): IntArray = primary + secondary + tertiary
        }

        /** The state's own accent, which means the same thing in both themes. */
        private fun getOrbColor(context: Context, state: WidgetState): Int {
            return when (state) {
                WidgetState.IDLE -> ContextCompat.getColor(context, R.color.widget_orb_idle)
                WidgetState.LISTENING -> ContextCompat.getColor(context, R.color.naomi_listening_red)
                WidgetState.PROCESSING -> ContextCompat.getColor(context, R.color.naomi_amber)
                WidgetState.SUCCESS -> ContextCompat.getColor(context, R.color.naomi_success_green)
                WidgetState.ERROR -> ContextCompat.getColor(context, R.color.naomi_listening_red)
            }
        }

        private fun orbDrawable(state: WidgetState): Int = when (state) {
            WidgetState.IDLE -> R.drawable.widget_orb_dot
            WidgetState.LISTENING -> R.drawable.widget_orb_ring
            WidgetState.PROCESSING -> R.drawable.widget_orb_sparkle
            WidgetState.SUCCESS -> R.drawable.widget_orb_check
            WidgetState.ERROR -> R.drawable.widget_orb_alert
        }

        private fun styleOrb(
            context: Context,
            views: RemoteViews,
            config: WidgetConfig,
            state: WidgetState,
            iconId: Int,
            containerId: Int
        ) {
            if (!config.showOrb) {
                views.setViewVisibility(containerId, View.GONE)
                return
            }
            views.setViewVisibility(containerId, View.VISIBLE)
            views.setImageViewResource(iconId, orbDrawable(state))
            views.setInt(iconId, "setColorFilter", getOrbColor(context, state))
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

        /**
         * The tap that starts or stops a capture.
         *
         * Addressed to [WidgetTapReceiver] by explicit component. The provider
         * itself has to stay exported so the launcher can send it widget
         * broadcasts, and it used to carry ACTION_WIDGET_TAP in the same
         * intent filter — which meant any app on the device could start or stop
         * a recording with a single broadcast.
         */
        private fun tapIntent(context: Context, requestCode: Int): PendingIntent {
            val intent = Intent(context, WidgetTapReceiver::class.java).apply {
                action = WidgetTapReceiver.ACTION_WIDGET_TAP
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openApp(context: Context, requestCode: Int, noteId: Long? = null): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (noteId != null) putExtra(EXTRA_NOTE_ID, noteId)
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** Offset past the literal request codes so a note id cannot collide with one. */
        private fun noteRequestCode(noteId: Long): Int = RC_NOTE_BASE + (noteId.toInt() and 0xFFFF)

        private fun buildSmallRemoteViews(context: Context, config: WidgetConfig): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.naomi_widget_small)
            val currentState = readState(context)

            styleBackground(context, views, config)
            styleText(
                context, views, config,
                TextIds(
                    primary = intArrayOf(R.id.widget_small_title),
                    secondary = intArrayOf(R.id.widget_small_subtitle)
                )
            )
            styleOrb(
                context, views, config, currentState,
                R.id.widget_small_icon, R.id.widget_small_orb_container
            )

            views.setTextViewText(R.id.widget_small_title, readTitle(context) ?: currentState.title)
            views.setTextViewText(R.id.widget_small_subtitle, readSubtitle(context) ?: currentState.subtitle)
            views.setContentDescription(R.id.widget_small_root, currentState.contentDescription)
            views.setOnClickPendingIntent(R.id.widget_small_root, tapIntent(context, RC_TAP_SMALL))

            return views
        }

        private fun buildMediumRemoteViews(
            context: Context,
            config: WidgetConfig,
            recentNotes: List<NoteEntity>,
            nextTask: TaskEntity?
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.naomi_widget_medium)
            val currentState = readState(context)

            styleBackground(context, views, config)
            styleText(
                context, views, config,
                TextIds(
                    primary = intArrayOf(R.id.widget_medium_title, R.id.widget_medium_note_title),
                    secondary = intArrayOf(R.id.widget_medium_subtitle, R.id.widget_medium_note_time),
                    tertiary = intArrayOf(R.id.widget_medium_note_label)
                )
            )
            styleOrb(
                context, views, config, currentState,
                R.id.widget_medium_icon, R.id.widget_medium_orb_container
            )

            views.setTextViewText(R.id.widget_medium_title, readTitle(context) ?: currentState.title)
            views.setTextViewText(R.id.widget_medium_subtitle, readSubtitle(context) ?: currentState.subtitle)
            views.setContentDescription(R.id.widget_medium_capture_zone, currentState.contentDescription)
            views.setOnClickPendingIntent(R.id.widget_medium_capture_zone, tapIntent(context, RC_TAP_MEDIUM))

            val task = nextTask.takeIf { config.content == WidgetContent.TASK }
            val latestNote = recentNotes.firstOrNull().takeIf { config.content == WidgetContent.RECENT }

            if (task != null) {
                views.setTextViewText(R.id.widget_medium_note_label, "NEXT UP")
                views.setTextViewText(R.id.widget_medium_note_title, task.title)
                views.setTextViewText(R.id.widget_medium_note_time, task.deadline.orEmpty())
                views.setOnClickPendingIntent(
                    R.id.widget_medium_note_container,
                    openApp(context, noteRequestCode(task.noteId), task.noteId)
                )
            } else if (latestNote != null) {
                views.setTextViewText(
                    R.id.widget_medium_note_label,
                    if (currentState == WidgetState.SUCCESS) "JUST REMEMBERED" else "LAST REMEMBERED"
                )
                views.setTextViewText(R.id.widget_medium_note_title, latestNote.title)
                views.setTextViewText(R.id.widget_medium_note_time, formatTimestamp(latestNote.createdAt))
                views.setOnClickPendingIntent(
                    R.id.widget_medium_note_container,
                    openApp(context, noteRequestCode(latestNote.id), latestNote.id)
                )
            } else {
                views.setTextViewText(R.id.widget_medium_note_label, "MEMORY")
                views.setTextViewText(
                    R.id.widget_medium_note_title,
                    when (config.content) {
                        WidgetContent.MINIMAL -> "Naomi"
                        WidgetContent.TASK -> "Nothing due"
                        WidgetContent.RECENT -> "No memories yet"
                    }
                )
                views.setTextViewText(R.id.widget_medium_note_time, "Tap orb to begin")
                views.setOnClickPendingIntent(
                    R.id.widget_medium_note_container,
                    openApp(context, RC_OPEN_APP)
                )
            }

            return views
        }

        private fun buildLargeRemoteViews(
            context: Context,
            config: WidgetConfig,
            recentNotes: List<NoteEntity>,
            nextTask: TaskEntity?
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.naomi_widget_large)
            val currentState = readState(context)

            styleBackground(context, views, config)
            styleText(
                context, views, config,
                TextIds(
                    primary = intArrayOf(
                        R.id.widget_large_header_title,
                        R.id.widget_large_prompt,
                        R.id.widget_large_capture_title,
                        R.id.widget_large_note_1_title,
                        R.id.widget_large_note_2_title
                    ),
                    secondary = intArrayOf(
                        R.id.widget_large_capture_label,
                        R.id.widget_large_note_1_bullet,
                        R.id.widget_large_note_1_time,
                        R.id.widget_large_note_2_bullet,
                        R.id.widget_large_note_2_time
                    ),
                    tertiary = intArrayOf(R.id.widget_large_recent_label)
                )
            )
            styleOrb(
                context, views, config, currentState,
                R.id.widget_large_icon, R.id.widget_large_orb_container
            )

            // The state title now has a line of its own, so "Remembered" and
            // "Couldn't remember" finally appear on the size with the most room
            // for them.
            views.setTextViewText(R.id.widget_large_capture_title, readTitle(context) ?: currentState.title)
            views.setTextViewText(R.id.widget_large_capture_label, readSubtitle(context) ?: currentState.subtitle)
            views.setContentDescription(R.id.widget_large_capture_button, currentState.contentDescription)
            views.setOnClickPendingIntent(R.id.widget_large_capture_button, tapIntent(context, RC_TAP_LARGE))

            val ambientIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_SCREEN", "ambient")
            }
            views.setOnClickPendingIntent(
                R.id.widget_large_btn_ambient,
                PendingIntent.getActivity(
                    context, RC_AMBIENT, ambientIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            val showTask = config.content == WidgetContent.TASK && nextTask != null
            val notes = if (config.content == WidgetContent.RECENT) recentNotes else emptyList()

            views.setTextViewText(
                R.id.widget_large_recent_label,
                if (config.content == WidgetContent.TASK) "NEXT UP" else "RECENTLY REMEMBERED"
            )
            views.setViewVisibility(
                R.id.widget_large_recent_label,
                if (notes.isEmpty() && !showTask) View.GONE else View.VISIBLE
            )

            if (showTask) {
                val due = nextTask!!
                views.setViewVisibility(R.id.widget_large_note_1, View.VISIBLE)
                views.setTextViewText(R.id.widget_large_note_1_title, due.title)
                views.setTextViewText(R.id.widget_large_note_1_time, due.deadline.orEmpty())
                views.setOnClickPendingIntent(
                    R.id.widget_large_note_1,
                    openApp(context, noteRequestCode(due.noteId), due.noteId)
                )
                views.setViewVisibility(R.id.widget_large_note_2, View.GONE)
                return views
            }
            bindLargeNote(context, views, notes.getOrNull(0), R.id.widget_large_note_1, R.id.widget_large_note_1_title, R.id.widget_large_note_1_time)
            bindLargeNote(context, views, notes.getOrNull(1), R.id.widget_large_note_2, R.id.widget_large_note_2_title, R.id.widget_large_note_2_time)

            return views
        }

        private fun bindLargeNote(
            context: Context,
            views: RemoteViews,
            note: NoteEntity?,
            rowId: Int,
            titleId: Int,
            timeId: Int
        ) {
            if (note == null) {
                views.setViewVisibility(rowId, View.GONE)
                return
            }
            views.setViewVisibility(rowId, View.VISIBLE)
            views.setTextViewText(titleId, note.title)
            views.setTextViewText(timeId, formatTimestamp(note.createdAt))
            views.setOnClickPendingIntent(rowId, openApp(context, noteRequestCode(note.id), note.id))
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
                val nextTask = getNextTaskSafely(context)
                for (widgetId in appWidgetIds) {
                    val views =
                        buildAdaptiveRemoteViews(context, widgetId, appWidgetManager, recentNotes, nextTask)
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
                val views = buildAdaptiveRemoteViews(
                    context, appWidgetId, appWidgetManager, recentNotes, getNextTaskSafely(context)
                )
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Forgets a removed widget's appearance.
     *
     * Without this every widget the user ever placed leaves its settings behind
     * forever, and a widget id the host later reuses inherits them.
     */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (widgetId in appWidgetIds) WidgetConfigStore.clear(context, widgetId)
    }
}
