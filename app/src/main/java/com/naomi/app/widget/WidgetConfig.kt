package com.naomi.app.widget

import android.content.Context
import androidx.annotation.DrawableRes
import com.naomi.app.R

/** How the widget picks its colours. */
enum class WidgetTheme { SYSTEM, LIGHT, DARK }

/** What the widget shows, on sizes that have room for a choice. */
enum class WidgetContent { MINIMAL, RECENT, TASK }

/**
 * One widget instance's appearance.
 *
 * @param opacity 0.35 to 1.0 — see [MIN_OPACITY].
 */
data class WidgetConfig(
    val theme: WidgetTheme = WidgetTheme.SYSTEM,
    val opacity: Float = 1f,
    val showOrb: Boolean = true,
    val content: WidgetContent = WidgetContent.RECENT
) {
    /** 0-255, as `ImageView.setImageAlpha` wants it. */
    val alpha: Int get() = (opacity.coerceIn(MIN_OPACITY, 1f) * 255).toInt()

    /**
     * Whether the text hierarchy has to collapse to a single high-contrast
     * colour.
     *
     * Found by rendering it: at 38% opacity the subtitle had all but
     * disappeared. Not because the text was faded — it never is — but because a
     * translucent background *becomes* a mid-tone as the wallpaper comes
     * through, and Naomi's secondary text is itself a mid-tone chosen against a
     * known surface. Two mid-tones on top of each other is no contrast at all.
     *
     * So below this point the three-level hierarchy is given up and everything
     * is drawn in the primary colour. Losing a little typographic nuance is a
     * far smaller cost than a subtitle nobody can read.
     */
    val flattensText: Boolean get() = opacity < TEXT_FLATTEN_BELOW

    /**
     * There was a corner-radius control here, and it was removed after watching
     * it do nothing.
     *
     * Android 12+ launchers clip a widget to their own corner radius, so
     * "Square" and "Rounded" rendered as identical arcs on a real home screen —
     * verified by diffing screenshots of the two. A setting the user can change
     * and see no result from is worse than no setting: it teaches them the whole
     * screen is decorative.
     */
    @DrawableRes
    fun backgroundDrawable(): Int =
        if (theme == WidgetTheme.SYSTEM) R.drawable.widget_bg_system else R.drawable.widget_bg_plain

    companion object {
        /**
         * The floor on background opacity.
         *
         * Below roughly a third, the wallpaper shows through strongly enough
         * that fully opaque text still fails to read against a busy photo. The
         * brief is explicit that low alpha must not make text unreadable, and
         * the honest way to guarantee that is to stop the slider before it can
         * — rather than fading the text along with it, which is what looks
         * tidy and what actually breaks contrast.
         */
        const val MIN_OPACITY = 0.35f

        /**
         * Below this the background is translucent enough that a mid-tone
         * foreground stops being legible. See [flattensText].
         */
        const val TEXT_FLATTEN_BELOW = 0.75f

        val DEFAULT = WidgetConfig()
    }
}

/**
 * Where a widget's appearance lives.
 *
 * SharedPreferences rather than Room: this is host-lifecycle data — created by
 * the launcher, keyed by an id the launcher owns, and read inside a broadcast
 * receiver that must answer quickly and cannot wait on a database. It is not
 * the user's knowledge, and putting it in the same store as their memories
 * would mean an export or a backup carried their widget opacity.
 *
 * Keys are namespaced by widget id so two widgets can look completely different.
 */
object WidgetConfigStore {

    private const val PREFS = "naomi_widget"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(widgetId: Int, name: String) = "w${widgetId}_$name"

    fun load(context: Context, widgetId: Int): WidgetConfig {
        val p = prefs(context)
        return WidgetConfig(
            theme = p.getString(key(widgetId, "theme"), null).toEnum(WidgetTheme.SYSTEM),
            opacity = p.getFloat(key(widgetId, "opacity"), 1f),
            showOrb = p.getBoolean(key(widgetId, "orb"), true),
            content = p.getString(key(widgetId, "content"), null).toEnum(WidgetContent.RECENT)
        )
    }

    fun save(context: Context, widgetId: Int, config: WidgetConfig) {
        prefs(context).edit()
            .putString(key(widgetId, "theme"), config.theme.name)
            .putFloat(key(widgetId, "opacity"), config.opacity)
            .putBoolean(key(widgetId, "orb"), config.showOrb)
            .putString(key(widgetId, "content"), config.content.name)
            .apply()
    }

    /**
     * Forgets a removed widget.
     *
     * Without this every widget the user ever placed leaves its keys behind
     * forever, and an id the host later reuses inherits a stranger's settings.
     */
    fun clear(context: Context, widgetId: Int) {
        prefs(context).edit()
            .remove(key(widgetId, "theme"))
            .remove(key(widgetId, "opacity"))
            .remove(key(widgetId, "orb"))
            .remove(key(widgetId, "content"))
            .apply()
    }

    /** A stored name that no longer matches an enum constant is not worth crashing over. */
    private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
        this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
