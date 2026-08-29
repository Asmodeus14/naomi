package com.naomi.app.presentation.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naomi.app.domain.model.AppThemeMode
import com.naomi.app.domain.model.RetentionPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naomi.app.presentation.components.NaomiSectionHeader
import com.naomi.app.presentation.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val retentionPolicy by viewModel.retentionPolicy.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val nanoStatus by viewModel.nanoStatus.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadSettings()
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    fun exportTree() {
        coroutineScope.launch {
            val md = viewModel.getMarkdownExport()
            // The ViewModel surfaces its own failure message; opening a share
            // sheet with an empty body on top of it would just confuse.
            if (md.isBlank()) return@launch
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Naomi Knowledge Base")
                putExtra(Intent.EXTRA_TEXT, md)
            }
            context.startActivity(Intent.createChooser(intent, "Export Knowledge Tree"))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Appearance & Theme Section
            item {
                Spacer(modifier = Modifier.height(4.dp))
                NaomiSectionHeader(title = "THEME PREVIEW")
                Spacer(modifier = Modifier.height(8.dp))

                // Interactive Theme Preview Card matching Stitch
                val previewDark = when (themeMode) {
                    AppThemeMode.DARK -> true
                    AppThemeMode.LIGHT -> false
                    AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (previewDark) StitchDarkBackground else StitchLightBackground,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (previewDark) StitchDarkOutlineVariant else StitchLightOutlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (previewDark) StitchDarkPrimary else StitchLightPrimary)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Naomi",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (previewDark) StitchDarkOnBackground else StitchLightOnBackground
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Theme Mode Selectors
                Column {
                    AppThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setThemeMode(mode) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Visual Indicator circle
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (mode) {
                                            AppThemeMode.LIGHT -> StitchLightBackground
                                            AppThemeMode.DARK -> StitchDarkBackground
                                            AppThemeMode.SYSTEM -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
            }

            // Privacy Center Section
            item {
                NaomiSectionHeader(title = "PRIVACY CENTER")
                Spacer(modifier = Modifier.height(8.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = LocalNaomiAccents.current.success,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Your memories stay on this device.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Every row here is a fact the code can back up. The
                    // audio row used to read "Not stored permanently" while
                    // the retention selector below offered "Keep forever" —
                    // a claim the app's own settings contradicted. It now
                    // reports whatever the user actually chose.
                    PrivacyRow(
                        label = "Network access",
                        value = "None — no INTERNET permission"
                    )
                    PrivacyRow(label = "Transcription", value = "On this device")
                    PrivacyRow(
                        label = "Understanding",
                        value = when (nanoStatus) {
                            is NanoStatus.Active -> "Gemini Nano, on this device"
                            else -> "Built-in, on this device"
                        }
                    )
                    PrivacyRow(label = "Audio", value = retentionPolicy.label)
                    PrivacyRow(label = "Analytics", value = "None")
                }
            }

            // On-device understanding
            item {
                NaomiSectionHeader(title = "UNDERSTANDING")
                Spacer(modifier = Modifier.height(8.dp))

                Column {
                    Text(
                        text = when (nanoStatus) {
                            is NanoStatus.Checking -> "Checking this device…"
                            is NanoStatus.Active -> "Gemini Nano"
                            is NanoStatus.Downloading -> "Preparing Gemini Nano…"
                            is NanoStatus.Downloadable -> "Gemini Nano available"
                            is NanoStatus.Unsupported -> "Built-in understanding"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (nanoStatus) {
                            is NanoStatus.Checking ->
                                "Seeing whether this device can run Gemini Nano."
                            is NanoStatus.Active ->
                                "Titles and topics come from Gemini Nano, run by Android's AICore service on this device. Naomi never sends your words anywhere."
                            is NanoStatus.Downloading ->
                                "Android is fetching the model. This happens once."
                            is NanoStatus.Downloadable ->
                                "This device can run Gemini Nano. Android will fetch the model once, then everything stays local."
                            is NanoStatus.Unsupported ->
                                "Gemini Nano isn't available here, so Naomi uses its built-in keyphrase engine. It runs entirely on this device and needs no model."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    if (nanoStatus is NanoStatus.Downloadable) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { viewModel.downloadNano() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Enable Gemini Nano")
                        }
                    }
                }
            }

            // Home Screen Widget
            item {
                NaomiSectionHeader(title = "HOME SCREEN WIDGET")
                Spacer(modifier = Modifier.height(8.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Quick Capture Widget",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap. Speak. Naomi remembers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
                            val myProvider = android.content.ComponentName(context, com.naomi.app.widget.NaomiWidgetProvider::class.java)
                            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                                appWidgetManager.requestPinAppWidget(myProvider, null, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Add to Home Screen", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Audio Retention Policy
            item {
                NaomiSectionHeader(title = "AUDIO RETENTION")
                Spacer(modifier = Modifier.height(8.dp))

                Column {
                    RetentionPolicy.entries.forEach { policy ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setRetentionPolicy(policy) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = retentionPolicy == policy,
                                onClick = { viewModel.setRetentionPolicy(policy) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = policy.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Storage Breakdown Dashboard
            item {
                NaomiSectionHeader(title = "STORAGE")
                Spacer(modifier = Modifier.height(8.dp))

                val stats = storageStats
                // Everything Naomi stores is text until audio is retained, so a
                // fresh install reads "0.0 MB" on every row unless small sizes
                // step down to KB.
                val formatMb = { bytes: Long ->
                    when {
                        bytes < 1024L -> "$bytes B"
                        bytes < 1024L * 1024L ->
                            String.format(java.util.Locale.ROOT, "%.0f KB", bytes / 1024.0)
                        else ->
                            String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
                    }
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Memories & topics", style = MaterialTheme.typography.bodyMedium)
                        Text(text = formatMb(stats?.databaseBytes ?: 0L), style = NaomiMonoLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Stored audio", style = MaterialTheme.typography.bodyMedium)
                        Text(text = formatMb(stats?.audioBytes ?: 0L), style = NaomiMonoLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text(text = formatMb(stats?.totalBytes ?: 0L), style = NaomiMonoLabel, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Offered only when there is audio to delete; an always-on
                    // button that silently does nothing teaches the user to
                    // distrust the whole screen.
                    OutlinedButton(
                        onClick = { viewModel.cleanAudio() },
                        enabled = (stats?.audioBytes ?: 0L) > 0L,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.CleaningServices, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete stored audio")
                    }
                }
            }

            // Export Knowledge Base
            item {
                NaomiSectionHeader(title = "DATA & EXPORT")
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { exportTree() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Outlined.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Knowledge Tree (Markdown)", fontWeight = FontWeight.Medium)
                }
            }

            item {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun PrivacyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

