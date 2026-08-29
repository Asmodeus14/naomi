package com.naomi.app.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner radii, one per role.
 *
 * There is a single answer for "how round is a card", which is the point — the
 * same element rendered at six different radii across six screens reads as
 * carelessness even when nobody can say why.
 */
object NaomiShapes {
    /** 4dp — tags, chips, inputs. */
    val small: Shape = RoundedCornerShape(4.dp)

    /** 8dp — the default. Cards, buttons, list rows. */
    val medium: Shape = RoundedCornerShape(8.dp)

    /** 12dp — sheets, dialogs, large containers. */
    val large: Shape = RoundedCornerShape(12.dp)

    /** Fully round — the orb, avatars, the nav capsule. */
    val pill: Shape = RoundedCornerShape(percent = 50)

    /** Bottom sheets: rounded at the top only. */
    val sheet: Shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

    /** Hairline border width used on every bordered surface. */
    val hairline: Dp = 1.dp
}
