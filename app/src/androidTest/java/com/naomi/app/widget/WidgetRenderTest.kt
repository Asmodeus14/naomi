package com.naomi.app.widget

import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Draws the widget and looks at the pixels.
 *
 * Appearance settings are the kind of thing that compiles, runs, reports
 * success and renders nothing — `RemoteViews` swallows an action aimed at a
 * view id the layout does not contain, and a colour filter applied to the wrong
 * layer is invisible rather than wrong. The only way to know opacity actually
 * did something is to measure it.
 *
 * This also covers the requirement that low opacity must never make text
 * unreadable: the background fades and the foreground does not, and both halves
 * of that are asserted rather than assumed.
 */
@RunWith(AndroidJUnit4::class)
class WidgetRenderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = AppWidgetManager.getInstance(context)
    private val widgetId = 424_242

    @After
    fun tearDown() = WidgetConfigStore.clear(context, widgetId)

    private fun render(config: WidgetConfig): Bitmap {
        WidgetConfigStore.save(context, widgetId, config)
        val views = NaomiWidgetProvider.buildAdaptiveRemoteViews(context, widgetId, manager, emptyList())
        val view = views.apply(context, FrameLayout(context))

        val size = 400
        view.measure(
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, size, size)

        // Drawn over magenta, which appears nowhere in the palette — so any
        // magenta showing through is the widget's own transparency and not a
        // colour it chose.
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(BEHIND)
            view.draw(this)
        }
        return bitmap
    }

    /** Middle of the left edge, inside the widget but away from any text. */
    private fun backgroundPixel(bitmap: Bitmap): Int = bitmap.getPixel(bitmap.width / 12, bitmap.height / 2)

    @Test
    fun light_and_dark_overrides_actually_look_different() {
        val light = backgroundPixel(render(WidgetConfig(theme = WidgetTheme.LIGHT)))
        val dark = backgroundPixel(render(WidgetConfig(theme = WidgetTheme.DARK)))

        assertNotEquals("Light and Dark rendered identically", light, dark)
        assertTrue("the Light widget is not light: ${hex(light)}", luminance(light) > 0.7)
        assertTrue("the Dark widget is not dark: ${hex(dark)}", luminance(dark) < 0.3)
    }

    /**
     * The control the brief is most specific about. At the floor the wallpaper
     * must show through — otherwise the slider does nothing — and it must not
     * show through so much that the widget stops being a surface.
     */
    @Test
    fun opacity_lets_the_wallpaper_through_without_swallowing_the_widget() {
        val opaque = backgroundPixel(render(WidgetConfig(theme = WidgetTheme.LIGHT, opacity = 1f)))
        val faded = backgroundPixel(
            render(WidgetConfig(theme = WidgetTheme.LIGHT, opacity = WidgetConfig.MIN_OPACITY))
        )

        assertNotEquals("the opacity setting changed nothing", opaque, faded)

        // Magenta behind, near-white widget: at 35% the result must have moved
        // measurably toward the magenta and not all the way to it.
        assertTrue(
            "nothing of the wallpaper came through at the opacity floor: ${hex(faded)}",
            Color.blue(faded) - Color.green(faded) > 30
        )
        assertTrue(
            "the widget disappeared at the opacity floor: ${hex(faded)}",
            Color.green(faded) > 60
        )
    }

    /**
     * The half that is easy to get wrong. Fading the whole widget is what looks
     * tidy in a screenshot and what destroys contrast in use, so the text has to
     * come out at exactly the same colour whatever the background is doing.
     */
    @Test
    fun text_is_never_faded_with_the_background() {
        val opaque = render(WidgetConfig(theme = WidgetTheme.DARK, opacity = 1f))
        val faded = render(WidgetConfig(theme = WidgetTheme.DARK, opacity = WidgetConfig.MIN_OPACITY))

        val opaqueText = darkestPixel(opaque)
        val fadedText = darkestPixel(faded)

        // Dark theme: the lightest pixel is the text. It must be the same
        // brightness in both, within rounding.
        assertTrue(
            "text was faded along with the background: ${hex(opaqueText)} vs ${hex(fadedText)}",
            abs(luminance(opaqueText) - luminance(fadedText)) < 0.05
        )
    }

    /** Two widgets, two ids, two appearances that do not leak into each other. */
    @Test
    fun two_widgets_keep_their_own_settings() {
        val other = widgetId + 1
        try {
            WidgetConfigStore.save(context, widgetId, WidgetConfig(theme = WidgetTheme.DARK, opacity = 0.5f))
            WidgetConfigStore.save(context, other, WidgetConfig(theme = WidgetTheme.LIGHT, content = WidgetContent.MINIMAL))

            val a = WidgetConfigStore.load(context, widgetId)
            val b = WidgetConfigStore.load(context, other)

            assertEquals(WidgetTheme.DARK, a.theme)
            assertEquals(0.5f, a.opacity, 0.001f)
            assertEquals(WidgetContent.RECENT, a.content)

            assertEquals(WidgetTheme.LIGHT, b.theme)
            assertEquals(WidgetContent.MINIMAL, b.content)
        } finally {
            WidgetConfigStore.clear(context, other)
        }
    }

    /**
     * A removed widget must not leave its settings behind. Widget ids are
     * reused by the host, so a stale entry is a stranger's configuration
     * arriving on someone's new widget.
     */
    @Test
    fun removing_a_widget_forgets_its_appearance() {
        WidgetConfigStore.save(context, widgetId, WidgetConfig(theme = WidgetTheme.DARK, opacity = 0.4f))
        assertEquals(WidgetTheme.DARK, WidgetConfigStore.load(context, widgetId).theme)

        NaomiWidgetProvider().onDeleted(context, intArrayOf(widgetId))

        val afterRemoval = WidgetConfigStore.load(context, widgetId)
        assertEquals("a removed widget left its settings behind", WidgetConfig.DEFAULT, afterRemoval)
    }

    /**
     * The subtitle must not disappear into a translucent background.
     *
     * Found by rendering the configuration screen and looking at it: at 38%
     * opacity "Tap to speak" had all but vanished. The text was never faded —
     * the *background* had become a mid-tone as the wallpaper came through, and
     * Naomi's secondary text is itself a mid-tone chosen against a known
     * surface. Two mid-tones on top of each other is no contrast at all.
     */
    @Test
    fun the_text_hierarchy_collapses_before_it_becomes_illegible() {
        assertTrue(
            "text is still a three-level hierarchy at the opacity floor",
            WidgetConfig(opacity = WidgetConfig.MIN_OPACITY).flattensText
        )
        assertTrue(
            "the hierarchy was given up at full opacity, where it is safe",
            !WidgetConfig(opacity = 1f).flattensText
        )

        // Every drawn colour in the faded widget must clear the background it
        // sits on by a wide margin, which the three-tone hierarchy did not.
        val faded = render(
            WidgetConfig(theme = WidgetTheme.DARK, opacity = WidgetConfig.MIN_OPACITY)
        )
        val background = luminance(backgroundPixel(faded))
        val text = luminance(darkestPixel(faded))
        assertTrue(
            "the brightest text is not distinguishable from the background",
            text - background > 0.25
        )
    }

    /** Opacity below the floor is clamped rather than honoured. */
    @Test
    fun the_opacity_floor_cannot_be_stored_around() {
        WidgetConfigStore.save(context, widgetId, WidgetConfig(opacity = 0.02f))
        val alpha = WidgetConfigStore.load(context, widgetId).alpha
        assertEquals((WidgetConfig.MIN_OPACITY * 255).toInt(), alpha)
    }

    private fun darkestPixel(bitmap: Bitmap): Int {
        var best = bitmap.getPixel(0, 0)
        var bestLuminance = -1.0
        for (x in 0 until bitmap.width step 3) {
            for (y in 0 until bitmap.height step 3) {
                val pixel = bitmap.getPixel(x, y)
                val l = luminance(pixel)
                if (l > bestLuminance) {
                    bestLuminance = l
                    best = pixel
                }
            }
        }
        return best
    }

    private fun luminance(color: Int): Double =
        (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) / 255.0

    private fun hex(color: Int) = String.format("#%08X", color)

    private companion object {
        /** Magenta: not in Naomi's palette, so it can only be show-through. */
        const val BEHIND = 0xFFFF00FF.toInt()
    }
}
