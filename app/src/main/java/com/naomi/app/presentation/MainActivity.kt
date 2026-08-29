package com.naomi.app.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.compose.rememberNavController
import com.naomi.app.NaomiApp
import com.naomi.app.presentation.home.HomeViewModel
import com.naomi.app.presentation.navigation.NaomiNavGraph
import com.naomi.app.presentation.navigation.Screen
import com.naomi.app.presentation.theme.NaomiTheme
import kotlinx.coroutines.runBlocking

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.naomi.app.domain.model.AppThemeMode

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()
    private var pendingNavigationRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleWidgetIntent(intent)

        val app = application as NaomiApp

        // Read once, synchronously, before the first frame. Deciding the start
        // destination from a Flow would render Home and then replace it, which
        // the user sees as a flash of the wrong screen.
        val onboardingDone = runBlocking { app.settingsRepository.isOnboardingCompleted() }

        setContent {
            val currentThemeMode by app.settingsRepository.getThemeModeFlow()
                .collectAsState(initial = AppThemeMode.SYSTEM)

            NaomiTheme(themeMode = currentThemeMode) {
                val navController = rememberNavController()

                LaunchedEffect(pendingNavigationRoute) {
                    pendingNavigationRoute?.let { route ->
                        navController.navigate(route)
                        pendingNavigationRoute = null
                    }
                }

                NaomiNavGraph(
                    navController = navController,
                    // Onboarding was a finished screen with working persistence
                    // that nothing ever navigated to.
                    startDestination = if (onboardingDone) {
                        Screen.Home.route
                    } else {
                        Screen.Onboarding.route
                    },
                    homeViewModel = homeViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra("TRIGGER_CAPTURE", false) ||
            intent.action == "com.naomi.app.ACTION_QUICK_CAPTURE"
        ) {
            homeViewModel.startCapture()
        }

        val noteId = intent.getLongExtra("EXTRA_NOTE_ID", -1L)
        if (noteId != -1L) {
            pendingNavigationRoute = Screen.NoteDetail.createRoute(noteId)
        }

        val targetScreen = intent.getStringExtra("EXTRA_SCREEN")
        if (targetScreen == "ambient") {
            pendingNavigationRoute = Screen.Ambient.route
        }
    }
}

