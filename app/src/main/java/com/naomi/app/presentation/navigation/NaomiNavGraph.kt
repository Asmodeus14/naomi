package com.naomi.app.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naomi.app.NaomiApp
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.naomi.app.presentation.ambient.AmbientScreen
import com.naomi.app.presentation.ambient.AmbientViewModel
import com.naomi.app.presentation.ask.AskScreen
import com.naomi.app.presentation.ask.AskViewModel
import com.naomi.app.presentation.hierarchy.TopicDetailScreen
import com.naomi.app.presentation.hierarchy.TopicHierarchyScreen
import com.naomi.app.presentation.hierarchy.TopicHierarchyViewModel
import com.naomi.app.presentation.home.HomeScreen
import com.naomi.app.presentation.home.HomeViewModel
import com.naomi.app.presentation.note.NoteDetailScreen
import com.naomi.app.presentation.note.NoteDetailViewModel
import com.naomi.app.presentation.onboarding.OnboardingScreen
import com.naomi.app.presentation.search.SearchScreen
import com.naomi.app.presentation.search.SearchViewModel
import com.naomi.app.presentation.settings.SettingsScreen
import com.naomi.app.presentation.settings.SettingsViewModel
import com.naomi.app.presentation.theme.NaomiMotion

@Composable
fun NaomiNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Home.route,
    homeViewModel: HomeViewModel
) {
    // Every screen change was a hard cut, which reads as abrupt in an app whose
    // whole posture is calm. The movement is deliberately small — an 8% slide
    // under a fade — so it registers as continuity rather than as animation.
    val slide = 32
    val spec = tween<Float>(NaomiMotion.STANDARD, easing = NaomiMotion.easing)
    val slideSpec = tween<IntOffset>(NaomiMotion.STANDARD, easing = NaomiMotion.easing)

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(spec) + slideInHorizontally(slideSpec) { slide }
        },
        exitTransition = {
            fadeOut(spec) + slideOutHorizontally(slideSpec) { -slide / 2 }
        },
        popEnterTransition = {
            fadeIn(spec) + slideInHorizontally(slideSpec) { -slide / 2 }
        },
        popExitTransition = {
            fadeOut(spec) + slideOutHorizontally(slideSpec) { slide }
        }
    ) {
        composable(Screen.Onboarding.route) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            OnboardingScreen(
                onFinish = {
                    // Recording completion is what makes this a one-time screen.
                    // Without it, onboarding reappeared on every launch.
                    scope.launch {
                        (context.applicationContext as NaomiApp)
                            .settingsRepository.setOnboardingCompleted(true)
                    }
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToTasks = { navController.navigate(Screen.Tasks.route) },
                onNavigateToTopics = { navController.navigate(Screen.TopicHierarchy.route) },
                onNavigateToAmbient = { navController.navigate(Screen.Ambient.route) },
                onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                onNavigateToAsk = { navController.navigate(Screen.Ask.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToNoteDetail = { noteId -> navController.navigate(Screen.NoteDetail.createRoute(noteId)) }
            )
        }

        composable(Screen.Tasks.route) {
            val tasksViewModel: com.naomi.app.presentation.tasks.TasksViewModel = viewModel()
            com.naomi.app.presentation.tasks.TasksScreen(
                viewModel = tasksViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToNoteDetail = { noteId -> navController.navigate(Screen.NoteDetail.createRoute(noteId)) }
            )
        }

        composable(Screen.TopicHierarchy.route) {
            val hierarchyViewModel: TopicHierarchyViewModel = viewModel()
            TopicHierarchyScreen(
                viewModel = hierarchyViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTopicDetail = { topicId -> navController.navigate(Screen.TopicDetail.createRoute(topicId)) },
                onNavigateToNoteDetail = { noteId -> navController.navigate(Screen.NoteDetail.createRoute(noteId)) }
            )
        }

        composable(
            route = Screen.TopicDetail.route,
            arguments = listOf(navArgument("topicId") { type = NavType.LongType })
        ) { backStackEntry ->
            val topicId = backStackEntry.arguments?.getLong("topicId") ?: 0L
            val hierarchyViewModel: TopicHierarchyViewModel = viewModel()
            TopicDetailScreen(
                topicId = topicId,
                viewModel = hierarchyViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSubtopic = { subId -> navController.navigate(Screen.TopicDetail.createRoute(subId)) },
                onNavigateToNoteDetail = { noteId -> navController.navigate(Screen.NoteDetail.createRoute(noteId)) }
            )
        }

        composable(
            route = Screen.NoteDetail.route,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType })
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getLong("noteId") ?: 0L
            val noteViewModel: NoteDetailViewModel = viewModel()
            NoteDetailScreen(
                noteId = noteId,
                viewModel = noteViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTopic = { topicId -> navController.navigate(Screen.TopicDetail.createRoute(topicId)) }
            )
        }

        composable(Screen.Ambient.route) {
            val ambientViewModel: AmbientViewModel = viewModel()
            AmbientScreen(
                viewModel = ambientViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Search.route) {
            val searchViewModel: SearchViewModel = viewModel()
            SearchScreen(
                viewModel = searchViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToNoteDetail = { noteId -> navController.navigate(Screen.NoteDetail.createRoute(noteId)) },
                onNavigateToTopic = { topicId -> navController.navigate(Screen.TopicDetail.createRoute(topicId)) }
            )
        }

        composable(Screen.Ask.route) {
            val askViewModel: AskViewModel = viewModel()
            AskScreen(
                viewModel = askViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToNoteDetail = { noteId -> navController.navigate(Screen.NoteDetail.createRoute(noteId)) }
            )
        }

        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
