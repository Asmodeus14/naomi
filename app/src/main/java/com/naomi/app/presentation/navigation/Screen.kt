package com.naomi.app.presentation.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Tasks : Screen("tasks")
    object TopicHierarchy : Screen("topics")
    object TopicDetail : Screen("topic/{topicId}") {
        fun createRoute(topicId: Long) = "topic/$topicId"
    }
    object NoteDetail : Screen("note/{noteId}") {
        fun createRoute(noteId: Long) = "note/$noteId"
    }
    object Ambient : Screen("ambient")
    object Search : Screen("search")
    object Settings : Screen("settings")
}
