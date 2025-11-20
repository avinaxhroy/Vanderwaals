package me.avinas.vanderwaals.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Vanderwaals Modern Shape System
 * 
 * Features:
 * - Consistent rounded corners throughout the app
 * - Modern, friendly, and premium aesthetic
 * - Optimized for Material 3 components
 * 
 * Shape Categories:
 * - Extra Small: Chips, small buttons (8dp)
 * - Small: Cards, inputs (12dp)
 * - Medium: Dialogs, bottom sheets (16dp)
 * - Large: Special surfaces (20dp)
 * - Extra Large: Full screen modals (28dp)
 */

val VanderwaalsShapes = Shapes(
    // Extra Small - Chips, small buttons, tags
    extraSmall = RoundedCornerShape(8.dp),
    
    // Small - Input fields, small cards
    small = RoundedCornerShape(12.dp),
    
    // Medium - Standard cards, list items
    medium = RoundedCornerShape(16.dp),
    
    // Large - Dialogs, bottom sheets, large cards
    large = RoundedCornerShape(20.dp),
    
    // Extra Large - Full screen surfaces, hero cards
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Additional custom shapes for special use cases
 */

// For image cards and album covers
val ImageCardShape = RoundedCornerShape(20.dp)

// For FABs and circular buttons
val CircularShape = RoundedCornerShape(50)

// For top-rounded bottom sheets
val BottomSheetShape = RoundedCornerShape(
    topStart = 28.dp,
    topEnd = 28.dp,
    bottomStart = 0.dp,
    bottomEnd = 0.dp
)

// For search bars and inputs
val SearchBarShape = RoundedCornerShape(24.dp)

// For pill-shaped buttons
val PillShape = RoundedCornerShape(50)

// For slightly rounded cards (subtle)
val SubtleRoundedShape = RoundedCornerShape(8.dp)

// For very rounded premium cards
val PremiumCardShape = RoundedCornerShape(24.dp)

// For dialog shapes
val DialogShape = RoundedCornerShape(28.dp)
