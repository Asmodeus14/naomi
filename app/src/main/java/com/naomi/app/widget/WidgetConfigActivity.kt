package com.naomi.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naomi.app.NaomiApp
import com.naomi.app.data.database.entities.NoteEntity
import com.naomi.app.domain.model.AppThemeMode
import com.naomi.app.presentation.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Chooses how one widget looks.
 *
 * Launched by the *launcher*, not by Naomi, when a widget is placed or
 * reconfigured. Two rules the platform imposes and that are easy to get wrong:
 *
 * 1. The result must be `RESULT_CANCELED` from the moment this starts. If the
 *    user backs out, the host takes that as "do not place the widget" — and if
 *    the default were OK, backing out would leave a widget the user never
 *    confirmed.
 * 2. The result intent must carry the widget id back, and the widget has to be
 *    updated by hand afterwards, because no `APPWIDGET_UPDATE` is broadcast for
 *    a widget that has a configuration activity.
 *
 * The screen itself is preview-first: a real widget rendered at the top,
 * changing as the controls below it move. Settings you cannot see the effect of
 * are settings you have to guess at, and this is a screen about appearance.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Before anything else: backing out must not place a widget.
        setResult(Activity.RESULT_CANCELED, resultIntent())

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // No valid id means this was started by something other than a host.
        // There is nothing to configure, so there is nothing to show.
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val initial = WidgetConfigStore.load(this, widgetId)

        setContent {
            val themeMode by (application as NaomiApp).settingsRepository
                .getThemeModeFlow()
                .collectAsState(initial = AppThemeMode.SYSTEM)

            NaomiTheme(themeMode = themeMode) {
                var config by remember { mutableStateOf(initial) }
                var recent by remember { mutableStateOf<List<NoteEntity>>(emptyList()) }

                LaunchedEffect(Unit) {
                    recent = loadRecentTitles()
                }

                WidgetConfigScreen(
                    config = config,
                    recent = recent,
                    onChange = { config = it },
                    onCancel = { finish() },
                    onDone = { commit(config) }
                )
            }
        }
    }

    /** A couple of real titles, so the preview shows the user's own memories. */
    private suspend fun loadRecentTitles(): List<NoteEntity> = withContext(Dispatchers.IO) {
        runCatching {
            (application as NaomiApp).knowledgeRepository.getRecentNotes(2)
        }.getOrDefault(emptyList())
    }

    private fun commit(config: WidgetConfig) {
        WidgetConfigStore.save(this, widgetId, config)

        // A widget with a configuration activity never receives the initial
        // APPWIDGET_UPDATE broadcast, so without this the launcher would show
        // the raw initialLayout until something else happened to refresh it.
        val manager = AppWidgetManager.getInstance(this)
        runCatching {
            manager.updateAppWidget(
                widgetId,
                NaomiWidgetProvider.buildAdaptiveRemoteViews(this, widgetId, manager, emptyList())
            )
        }
        // ...and then a real update, which fetches the notes off the main thread.
        NaomiWidgetProvider.refresh(this)

        setResult(Activity.RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}

// ---- screen ----

@Composable
private fun WidgetConfigScreen(
    config: WidgetConfig,
    recent: List<NoteEntity>,
    onChange: (WidgetConfig) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = NaomiSpacing.lg, vertical = NaomiSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(NaomiSpacing.sm)
            ) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                    shape = NaomiShapes.pill
                ) {
                    Text("Add widget", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NaomiSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NaomiSpacing.lg)
        ) {
            Spacer(Modifier.height(NaomiSpacing.sm))

            // The preview leads. Everything below it is a way of changing this.
            WidgetPreview(config = config, recent = recent)

            Choice(
                label = "Appearance",
                options = listOf(
                    WidgetTheme.SYSTEM to "System",
                    WidgetTheme.LIGHT to "Light",
                    WidgetTheme.DARK to "Dark"
                ),
                selected = config.theme,
                onSelect = { onChange(config.copy(theme = it)) }
            )

            OpacityControl(
                opacity = config.opacity,
                onChange = { onChange(config.copy(opacity = it)) }
            )

            Choice(
                label = "Content",
                options = listOf(
                    WidgetContent.MINIMAL to "Just the orb",
                    WidgetContent.RECENT to "Recent memory",
                    WidgetContent.TASK to "Next task"
                ),
                selected = config.content,
                onSelect = { onChange(config.copy(content = it)) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Show the orb",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Tapping the widget still records either way.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = config.showOrb,
                    onCheckedChange = { onChange(config.copy(showOrb = it)) }
                )
            }

            Spacer(Modifier.height(NaomiSpacing.md))
        }
    }
}

/**
 * A live rendering of the widget, in Compose rather than as a real `RemoteViews`.
 *
 * Inflating the actual widget would be more faithful, and would also mean the
 * preview could only ever be as correct as the layout is at that moment on that
 * device. This mirrors the same tokens the widget uses, which is close enough to
 * choose by and far more legible to change.
 *
 * The chequerboard behind it stands in for a wallpaper — opacity is meaningless
 * against a flat background, and the whole point of the control is what shows
 * through.
 */
@Composable
private fun WidgetPreview(config: WidgetConfig, recent: List<NoteEntity>) {
    val dark = when (config.theme) {
        WidgetTheme.SYSTEM -> isSystemInDarkThemeCompat()
        WidgetTheme.LIGHT -> false
        WidgetTheme.DARK -> true
    }

    val background = if (dark) Color(0xFF111318) else Color(0xFFFBF9FA)
    val primary = if (dark) Color(0xFFEDEDF0) else Color(0xFF1B1C1D)
    val orb = if (dark) Color(0xFFB8C8DA) else Color(0xFF4E5E6D)

    // The same collapse the real widget makes, so the preview does not promise
    // a typographic hierarchy the widget gives up in order to stay readable.
    val secondary = if (config.flattensText) {
        primary
    } else {
        if (dark) Color(0xFF9AA0AB) else Color(0xFF43474C)
    }

    // Matches what a launcher actually draws: hosts on Android 12+ impose
    // their own corner radius on every widget regardless of what it asks for.
    val radius: Dp = 26.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(NaomiShapes.large)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(168.dp)
                .height(168.dp)
                .clip(RoundedCornerShape(radius))
                // The one thing the opacity slider actually does. Text below is
                // drawn at full opacity on top, never faded with it.
                .background(background.copy(alpha = config.opacity.coerceIn(WidgetConfig.MIN_OPACITY, 1f)))
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (config.showOrb) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(orb.copy(alpha = 0.12f))
                        .border(1.dp, orb.copy(alpha = 0.35f), RoundedCornerShape(percent = 50)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(orb)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Text("Naomi", color = primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("Tap to speak", color = secondary, fontSize = 11.sp)

            if (config.content != WidgetContent.MINIMAL) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = when (config.content) {
                        WidgetContent.TASK -> "Next task"
                        else -> recent.firstOrNull()?.title ?: "No memories yet"
                    },
                    color = primary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun OpacityControl(opacity: Float, onChange: (Float) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Opacity",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${(opacity * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = opacity.coerceIn(WidgetConfig.MIN_OPACITY, 1f),
            onValueChange = onChange,
            // The slider cannot reach a value that would make the text hard to
            // read. Clamping the control is honest in a way that clamping the
            // result silently is not.
            valueRange = WidgetConfig.MIN_OPACITY..1f
        )
    }
}

@Composable
private fun <T> Choice(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(NaomiSpacing.sm)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(horizontalArrangement = Arrangement.spacedBy(NaomiSpacing.sm)) {
            for ((value, text) in options) {
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(NaomiShapes.pill)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onSelect(value) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun isSystemInDarkThemeCompat(): Boolean =
    androidx.compose.foundation.isSystemInDarkTheme()
