package com.naomi.app.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.naomi.app.data.database.entities.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How a task's deadline should read and whether it has already passed.
 */
data class DueLabel(
    val text: String,
    val isOverdue: Boolean
)

/**
 * Which section a task belongs in.
 *
 * Ordering is the display order, so the enum's own `compareTo` is enough to
 * sort sections without a separate lookup table.
 */
enum class DueBucket(val heading: String) {
    OVERDUE("OVERDUE"),
    TODAY("TODAY"),
    TOMORROW("TOMORROW"),
    UPCOMING("UPCOMING"),
    NO_DATE("NO DATE")
}

private val monthDay: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

/**
 * Buckets a task by its *resolved* instant.
 *
 * The previous implementation substring-matched the spoken phrase
 * (`deadline.contains("today")`), which meant a task said yesterday still sat
 * under "Today" and any phrasing the list did not anticipate fell through to
 * "Upcoming". Comparing dates is both correct and phrasing-independent.
 */
fun bucketOf(task: TaskEntity, today: LocalDate = LocalDate.now()): DueBucket {
    val due = task.dueAt?.let { dueDate(it) } ?: return DueBucket.NO_DATE
    return when {
        due.isBefore(today) -> DueBucket.OVERDUE
        due == today -> DueBucket.TODAY
        due == today.plusDays(1) -> DueBucket.TOMORROW
        else -> DueBucket.UPCOMING
    }
}

/**
 * Formats a deadline for display, pairing the speaker's own wording with the
 * date it resolved to.
 *
 * Showing the phrase alone would keep reading "Tomorrow" indefinitely; showing
 * the date alone would lose how the thought was actually spoken. Once the
 * phrase stops being self-explanatory — anything past tomorrow — the date is
 * appended.
 */
@Composable
fun rememberDueLabel(deadline: String?, dueAt: Long?): DueLabel? =
    remember(deadline, dueAt) { dueLabel(deadline, dueAt) }

fun dueLabel(
    deadline: String?,
    dueAt: Long?,
    today: LocalDate = LocalDate.now()
): DueLabel? {
    val due = dueAt?.let { dueDate(it) }

    if (due == null) {
        val phrase = deadline?.takeIf { it.isNotBlank() } ?: return null
        return DueLabel("Due $phrase", isOverdue = false)
    }

    val relative = when {
        due.isBefore(today) -> "Overdue · ${monthDay.format(due)}"
        due == today -> "Due today"
        due == today.plusDays(1) -> "Due tomorrow"
        else -> "Due ${monthDay.format(due)}"
    }

    return DueLabel(relative, isOverdue = due.isBefore(today))
}

private fun dueDate(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
