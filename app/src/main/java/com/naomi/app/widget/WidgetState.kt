package com.naomi.app.widget

/**
 * The five things the widget's orb can be saying.
 *
 * These mirror the in-app orb exactly, glyph for glyph, so the widget reads as
 * the same object on the home screen as it does inside the app.
 */
enum class WidgetState(
    val symbol: String,
    val title: String,
    val subtitle: String,
    val contentDescription: String
) {
    IDLE("●", "Naomi", "Tap to speak", "Naomi. Tap to start recording."),
    LISTENING("◉", "Listening", "00:00", "Naomi is listening. Tap to stop recording."),
    PROCESSING("✦", "Remembering…", "Organising", "Naomi is remembering."),
    SUCCESS("✓", "Remembered", "Filed away", "Memory remembered."),
    ERROR("!", "Couldn't remember", "Tap to retry", "Capture failed. Tap to retry.")
}

