package me.avinas.vanderwaals.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Vanderwaals Modern Shape System
 * 
 * Premium, refined corner radii for a sophisticated aesthetic:
 * - Cards: 14dp - modern, slightly tighter than before
 * - Buttons: 10dp - crisp, professional
 * - Dialogs: 20dp - friendly, approachable
 * - Small elements: 8dp - subtle rounding
 */

val VanderwaalsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Additional custom shapes for special use cases
 */

val ImageCardShape = RoundedCornerShape(16.dp)

val CircularShape = androidx.compose.foundation.shape.CircleShape

val BottomSheetShape = RoundedCornerShape(
    topStart = 24.dp,
    topEnd = 24.dp,
    bottomStart = 0.dp,
    bottomEnd = 0.dp
)

val SearchBarShape = RoundedCornerShape(12.dp)

val PillShape = RoundedCornerShape(50)

val SubtleRoundedShape = RoundedCornerShape(8.dp)

val PremiumCardShape = RoundedCornerShape(14.dp)

val DialogShape = RoundedCornerShape(20.dp)
