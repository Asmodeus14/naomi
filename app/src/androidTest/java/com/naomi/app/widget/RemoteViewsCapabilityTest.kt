package com.naomi.app.widget

import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.naomi.app.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the platform will actually do what the widget configuration screen
 * promises.
 *
 * Per-widget opacity, tint and corner radius are implemented by reaching through
 * [RemoteViews.setInt] to methods on `ImageView`. That only works if those
 * methods carry `@RemotableViewMethod`, and there is no way to check from an
 * IDE — the annotation is on framework source that ships nowhere in the SDK.
 * The failure mode if one is missing is an `ActionException` thrown inside the
 * *launcher's* process at inflate time, which shows up as a blank grey box on
 * someone's home screen and nothing at all in this app's logs.
 *
 * `RemoteViews.apply` reproduces exactly that inflation, so this is the same
 * question the launcher asks, answered before shipping rather than after.
 */
@RunWith(AndroidJUnit4::class)
class RemoteViewsCapabilityTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val parent = FrameLayout(context)

    /**
     * The three calls the configuration screen depends on. If any of them is
     * not remotable this test fails here, and the honest response is to drop
     * that control rather than ship a setting that renders nothing.
     *
     * That is not hypothetical: the corner-radius control was dropped for
     * exactly that reason, though for a different cause — these calls all work,
     * but Android 12+ launchers clip a widget to their own radius, so the
     * setting was invisible on a real home screen.
     */
    @Test
    fun the_background_layer_accepts_alpha_a_tint_and_a_drawable_swap() {
        for (layout in LAYOUTS) {
            val views = RemoteViews(context.packageName, layout)
            views.setInt(R.id.widget_bg, "setImageAlpha", 128)
            views.setInt(R.id.widget_bg, "setColorFilter", 0xFF112233.toInt())
            views.setImageViewResource(R.id.widget_bg, R.drawable.widget_bg_plain)

            assertNotNull(
                "layout $layout could not be inflated with per-widget styling applied",
                views.apply(context, parent)
            )
        }
    }

    /**
     * Every layout must survive plain inflation too.
     *
     * This has bitten before: a `<Row>` element in a RemoteViews layout threw
     * inside the launcher process, and a `View` used as a spacer did the same —
     * neither is on the allow-list, and neither fails at build time.
     */
    @Test
    fun every_layout_inflates_in_a_remote_process() {
        for (layout in LAYOUTS) {
            assertNotNull(
                "layout $layout is not a valid RemoteViews layout",
                RemoteViews(context.packageName, layout).apply(context, parent)
            )
        }
    }

    private companion object {
        val LAYOUTS = intArrayOf(
            R.layout.naomi_widget_small,
            R.layout.naomi_widget_medium,
            R.layout.naomi_widget_large
        )
    }
}
