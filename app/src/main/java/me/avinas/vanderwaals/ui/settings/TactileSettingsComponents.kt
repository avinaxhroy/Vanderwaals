package me.avinas.vanderwaals.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.avinas.vanderwaals.ui.onboarding.bounceClick
import me.avinas.vanderwaals.ui.theme.*
import kotlin.math.roundToInt
import kotlin.random.Random

object RadicalPalette {
    val DarkCanvasBase = Color(0xFF07080B)
    val DarkCanvasMid = Color(0xFF0C0E14)
    val DarkCanvasDeep = Color(0xFF040507)

    val LightCanvasBase = Color(0xFFFFFFFF)
    val LightCanvasMid = Color(0xFFF7F8FA)
    val LightCanvasDeep = Color(0xFFEFF2F6)

    val DarkCardTop = Color(0xFFF0ECE5)
    val DarkCardBottom = Color(0xFFDFD8CD)
    val DarkCardBorderTop = Color(0xFFFAF8F5).copy(alpha = 0.95f)
    val DarkCardBorderBottom = Color(0xFFB8B0A2)
    val DarkCardTextPrimary = Color(0xFF1C1917)
    val DarkCardTextSecondary = Color(0xFF57534E)
    val DarkCardTextTertiary = Color(0xFF78716C)
    val DarkCardWell = Color(0xFFCCC5B9)

    val LightCardTop = Color(0xFF0D5E47)
    val LightCardBottom = Color(0xFF063B2C)
    val LightCardBorderTop = Color.White.copy(alpha = 0.40f)
    val LightCardBorderBottom = Color.Black.copy(alpha = 0.55f)
    val LightCardTextPrimary = Color(0xFFFFFFFF)
    val LightCardTextSecondary = Color(0xFFA7F3D0)
    val LightCardTextTertiary = Color(0xFF6EE7B7)
    val LightCardWell = Color(0xFF03261C)

    val SapphireBlue = Color(0xFF2563EB)
    val ElectricAzure = Color(0xFF0284C7)
    val CyberMagenta = Color(0xFFFF2A85)
    val CyberMagentaDark = Color(0xFFE11D48)
    val CyberMagentaDeep = Color(0xFF9F1239)
    val CyberMagentaLight = Color(0xFFFDA4AF)
    val EmeraldJade = CyberMagenta
    val ForestPine = CyberMagentaDark
    val SolarAmber = Color(0xFFF59E0B)
    val AmethystPurple = Color(0xFF7C3AED)
    val RadiantAmber = Color(0xFFD97706)
    val CoralRose = Color(0xFFFF2A85)
    val TealCyan = Color(0xFF0D9488)
    val PlatinumSilver = Color(0xFF64748B)
    val RubyRed = Color(0xFFDC2626)
    val RoyalIndigo = Color(0xFF4F46E5)
    val LimeAccent = Color(0xFFFF2A85)
}

@Composable
fun RadicalTactileBackdrop(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (isDark) {
                    Brush.verticalGradient(
                        colors = listOf(
                            RadicalPalette.DarkCanvasMid,
                            RadicalPalette.DarkCanvasBase,
                            RadicalPalette.DarkCanvasDeep
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            RadicalPalette.LightCanvasBase,
                            RadicalPalette.LightCanvasMid,
                            RadicalPalette.LightCanvasDeep
                        )
                    )
                }
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val random = Random(42)

            if (isDark) {
                for (i in 0 until 1800) {
                    val x = random.nextFloat() * w
                    val y = random.nextFloat() * h
                    val alpha = random.nextFloat() * 0.022f + 0.005f
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = 0.55f,
                        center = Offset(x, y)
                    )
                }

                for (y in 0..h.toInt() step 4) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.005f),
                        start = Offset(0f, y.toFloat()),
                        end = Offset(w, y.toFloat()),
                        strokeWidth = 0.5f
                    )
                }

                val ambientGlow = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF38BDF8).copy(alpha = 0.035f),
                        Color(0xFF1E293B).copy(alpha = 0.02f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, -h * 0.05f),
                    radius = maxOf(w, h) * 0.85f
                )
                drawRect(brush = ambientGlow)

                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, 0.5f),
                    end = Offset(w, 0.5f),
                    strokeWidth = 1f
                )
            } else {
                for (i in 0 until 1200) {
                    val x = random.nextFloat() * w
                    val y = random.nextFloat() * h
                    val alpha = random.nextFloat() * 0.010f + 0.003f
                    drawCircle(
                        color = Color(0xFF0F172A).copy(alpha = alpha),
                        radius = 0.5f,
                        center = Offset(x, y)
                    )
                }

                for (y in 0..h.toInt() step 4) {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.003f),
                        start = Offset(0f, y.toFloat()),
                        end = Offset(w, y.toFloat()),
                        strokeWidth = 0.5f
                    )
                }

                val ambientGlow = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.7f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, 0f),
                    radius = maxOf(w, h) * 0.75f
                )
                drawRect(brush = ambientGlow)
            }
        }
    }
}

@Composable
fun RadicalTactileCard(
    modifier: Modifier = Modifier,
    isDark: Boolean,
    cornerRadius: Dp = 18.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.985f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "cardScale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isPressed && onClick != null) 2.dp else (if (isDark) 8.dp else 6.dp),
        animationSpec = tween(120),
        label = "cardElevation"
    )

    val backgroundBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                RadicalPalette.DarkCardTop,
                RadicalPalette.DarkCardBottom
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                RadicalPalette.LightCardTop,
                RadicalPalette.LightCardBottom
            )
        )
    }

    val borderBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                RadicalPalette.DarkCardBorderTop,
                RadicalPalette.DarkCardBorderBottom
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                RadicalPalette.LightCardBorderTop,
                Color.White.copy(alpha = 0.12f),
                RadicalPalette.LightCardBorderBottom
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.45f) else Color(0xFF022C22).copy(alpha = 0.25f),
                spotColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color(0xFF022C22).copy(alpha = 0.18f)
            )
            .clip(shape)
            .background(backgroundBrush)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true),
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val startX = cornerRadius.toPx()
            val endX = size.width - cornerRadius.toPx()
            if (endX > startX) {
                val specularBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0f),
                        if (isDark) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.25f),
                        if (isDark) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0f)
                    ),
                    startX = startX,
                    endX = endX
                )
                drawLine(
                    brush = specularBrush,
                    start = Offset(startX, 1f),
                    end = Offset(endX, 1f),
                    strokeWidth = 1.2f
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun RadicalSectionHeader(
    title: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
        letterSpacing = 0.6.sp,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun RadicalIconBadge(
    icon: ImageVector,
    accentColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    backgroundBrush: Brush? = null,
    iconTint: Color? = null,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp
) {
    val shape = RoundedCornerShape(12.dp)

    val bgModifier = when {
        backgroundBrush != null -> Modifier.background(backgroundBrush)
        backgroundColor != null -> Modifier.background(backgroundColor)
        else -> Modifier.background(
            color = accentColor.copy(alpha = if (isDark) 0.16f else 0.12f)
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .then(bgModifier)
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = if (isDark) 0.35f else 0.22f),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint ?: accentColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun RadicalTactileSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = RadicalPalette.CyberMagenta
) {
    val haptic = LocalHapticFeedback.current

    val trackWidth = 52.dp
    val trackHeight = 30.dp
    val knobSize = 24.dp
    val knobPadding = 3.dp

    val maxOffset = trackWidth - knobSize - (knobPadding * 2)

    val knobOffset by animateDpAsState(
        targetValue = if (checked) maxOffset else 0.dp,
        animationSpec = tween(
            durationMillis = 160,
            easing = FastOutSlowInEasing
        ),
        label = "switchKnobOffset"
    )

    val trackShape = RoundedCornerShape(15.dp)
    val knobShape = CircleShape

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .shadow(
                elevation = if (checked) 2.dp else 0.dp,
                shape = trackShape,
                ambientColor = Color.Black.copy(alpha = 0.20f),
                spotColor = Color.Black.copy(alpha = 0.15f)
            )
            .clip(trackShape)
            .background(
                if (isDark) {
                    if (checked) {
                        Brush.horizontalGradient(
                            colors = listOf(
                                accentColor,
                                accentColor.copy(alpha = 0.85f)
                            )
                        )
                    } else {
                        SolidColor(RadicalPalette.DarkCardWell)
                    }
                } else {
                    if (checked) {
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF10B981),
                                Color(0xFF059669)
                            )
                        )
                    } else {
                        SolidColor(RadicalPalette.LightCardWell)
                    }
                }
            )
            .border(
                width = 1.dp,
                brush = if (isDark) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.20f),
                            Color.Black.copy(alpha = 0.40f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.90f),
                            Color(0xFFE2E8F0)
                        )
                    )
                },
                shape = trackShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(!checked)
                }
            )
            .padding(knobPadding),
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val h = size.height
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = if (isDark) 0.16f else 0.35f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = h * 0.45f
                )
            )
        }

        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(knobSize)
                .shadow(
                    elevation = 3.dp,
                    shape = knobShape,
                    ambientColor = Color.Black.copy(alpha = 0.25f),
                    spotColor = Color.Black.copy(alpha = 0.20f)
                )
                .clip(knobShape)
                .background(
                    if (isDark) {
                        Brush.verticalGradient(
                            colors = if (checked) {
                                listOf(Color.White, Color(0xFFE5DFD5))
                            } else {
                                listOf(Color(0xFFFAF8F5), Color(0xFFDCD6CC))
                            }
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(Color.White, Color(0xFFF1F5F9))
                        )
                    }
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White,
                            if (isDark) Color(0xFFB5ADA1) else Color(0xFFCBD5E1)
                        )
                    ),
                    shape = knobShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val kw = size.width
                drawLine(
                    color = Color.White,
                    start = Offset(kw * 0.25f, 1.5f),
                    end = Offset(kw * 0.75f, 1.5f),
                    strokeWidth = 1f
                )
            }

            val ledColor by animateColorAsState(
                targetValue = if (checked) accentColor else if (isDark) Color(0xFF9E9689) else Color(0xFF94A3B8),
                animationSpec = tween(200),
                label = "switchLedColor"
            )

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(ledColor)
                    .then(
                        if (checked) {
                            Modifier.shadow(
                                elevation = 2.dp,
                                shape = CircleShape,
                                ambientColor = Color.Black.copy(alpha = 0.25f),
                                spotColor = Color.Black.copy(alpha = 0.18f)
                            )
                        } else Modifier
                    )
            )
        }
    }
}

enum class FrequencyModeGroup(
    val title: String,
    val shortLabel: String,
    val icon: ImageVector,
    val description: String
) {
    UNLOCK("On Unlock", "Unlock", Icons.Default.LockOpen, "Cycles on every screen unlock"),
    HOURLY("Hourly", "Hourly", Icons.Default.Timelapse, "Changes throughout the day"),
    DAYS("Calendar", "Days", Icons.Default.CalendarToday, "Daily or multi-day cadence"),
    OFF("Paused", "Off", Icons.Default.PauseCircle, "Automatic changes are disabled")
}

data class HourlyOption(
    val interval: ChangeInterval,
    val label: String,
    val sublabel: String,
    val efficiency: String
)

data class DaysOption(
    val interval: ChangeInterval,
    val label: String,
    val sublabel: String
)

@Composable
fun RadicalFrequencyStudio(
    currentInterval: ChangeInterval,
    onIntervalSelected: (ChangeInterval) -> Unit,
    dailyTime: DailyTime?,
    onOpenFullTimePicker: () -> Unit,
    isDark: Boolean,
    accentColor: Color = RadicalPalette.EmeraldJade,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val activeGroup = remember(currentInterval) {
        when (currentInterval) {
            ChangeInterval.EVERY_UNLOCK -> FrequencyModeGroup.UNLOCK
            ChangeInterval.FIFTEEN_MINUTES,
            ChangeInterval.HOURLY,
            ChangeInterval.THREE_HOURS,
            ChangeInterval.SIX_HOURS,
            ChangeInterval.TWELVE_HOURS -> FrequencyModeGroup.HOURLY
            ChangeInterval.DAILY,
            ChangeInterval.THREE_DAYS,
            ChangeInterval.SEVEN_DAYS -> FrequencyModeGroup.DAYS
            ChangeInterval.NEVER -> FrequencyModeGroup.OFF
        }
    }

    val hourlyOptions = remember {
        listOf(
            HourlyOption(ChangeInterval.FIFTEEN_MINUTES, "15m", "96×/d", "Active"),
            HourlyOption(ChangeInterval.HOURLY, "1h", "24×/d", "Balanced"),
            HourlyOption(ChangeInterval.THREE_HOURS, "3h", "8×/d", "Optimal"),
            HourlyOption(ChangeInterval.SIX_HOURS, "6h", "4×/d", "Efficient"),
            HourlyOption(ChangeInterval.TWELVE_HOURS, "12h", "2×/d", "2× Daily")
        )
    }

    val daysOptions = remember {
        listOf(
            DaysOption(ChangeInterval.DAILY, "Daily", "Once daily"),
            DaysOption(ChangeInterval.THREE_DAYS, "3 Days", "Every 3d"),
            DaysOption(ChangeInterval.SEVEN_DAYS, "7 Days", "Weekly")
        )
    }

    val heroIcon = remember(currentInterval) {
        when (currentInterval) {
            ChangeInterval.EVERY_UNLOCK -> Icons.Default.LockOpen
            ChangeInterval.FIFTEEN_MINUTES -> Icons.Default.Timelapse
            ChangeInterval.HOURLY -> Icons.Default.HourglassBottom
            ChangeInterval.THREE_HOURS -> Icons.Default.Schedule
            ChangeInterval.SIX_HOURS -> Icons.Default.AccessTime
            ChangeInterval.TWELVE_HOURS -> Icons.Default.HistoryToggleOff
            ChangeInterval.DAILY -> Icons.Default.WbSunny
            ChangeInterval.THREE_DAYS -> Icons.Default.DateRange
            ChangeInterval.SEVEN_DAYS -> Icons.Default.CalendarToday
            ChangeInterval.NEVER -> Icons.Default.PauseCircle
        }
    }

    val heroTitle = remember(currentInterval) {
        when (currentInterval) {
            ChangeInterval.EVERY_UNLOCK -> "On Screen Unlock"
            ChangeInterval.FIFTEEN_MINUTES -> "Every 15 Minutes"
            ChangeInterval.HOURLY -> "Every 1 Hour"
            ChangeInterval.THREE_HOURS -> "Every 3 Hours"
            ChangeInterval.SIX_HOURS -> "Every 6 Hours"
            ChangeInterval.TWELVE_HOURS -> "Every 12 Hours"
            ChangeInterval.DAILY -> "Daily Scheduled"
            ChangeInterval.THREE_DAYS -> "Every 3 Days"
            ChangeInterval.SEVEN_DAYS -> "Every 7 Days (Weekly)"
            ChangeInterval.NEVER -> "Auto-Change Paused"
        }
    }

    val time = dailyTime ?: DailyTime(8, 0)
    val timeOfDayTag = remember(time.hour) {
        when (time.hour) {
            in 5..8 -> "🌅 Sunrise"
            in 9..11 -> "☀️ Morning"
            in 12..16 -> "🌤️ Afternoon"
            in 17..20 -> "🌆 Sunset"
            else -> "🌙 Night"
        }
    }
    val formattedDailyTime = remember(time) {
        String.format("%02d:%02d", time.hour, time.minute)
    }

    val heroSubtitle = remember(currentInterval, formattedDailyTime, timeOfDayTag) {
        when (currentInterval) {
            ChangeInterval.EVERY_UNLOCK -> "Dynamic rotation · 1-min battery guard"
            ChangeInterval.FIFTEEN_MINUTES -> "96 rotations/day · Precision alarm"
            ChangeInterval.HOURLY -> "24 rotations/day · Top of every hour"
            ChangeInterval.THREE_HOURS -> "8 rotations/day · Balanced power"
            ChangeInterval.SIX_HOURS -> "4 rotations/day · Optimal battery"
            ChangeInterval.TWELVE_HOURS -> "2 rotations/day · Morning & evening"
            ChangeInterval.DAILY -> "Set for $formattedDailyTime ($timeOfDayTag)"
            ChangeInterval.THREE_DAYS -> "1 rotation every 3 days"
            ChangeInterval.SEVEN_DAYS -> "1 rotation per week"
            ChangeInterval.NEVER -> "Manual changes only · Standby"
        }
    }

    val heroBadge = remember(currentInterval) {
        when (currentInterval) {
            ChangeInterval.EVERY_UNLOCK -> "DYNAMIC"
            ChangeInterval.FIFTEEN_MINUTES,
            ChangeInterval.HOURLY,
            ChangeInterval.THREE_HOURS,
            ChangeInterval.SIX_HOURS,
            ChangeInterval.TWELVE_HOURS -> "EXACT ALARM"
            ChangeInterval.DAILY -> "DAILY"
            ChangeInterval.THREE_DAYS,
            ChangeInterval.SEVEN_DAYS -> "CALENDAR"
            ChangeInterval.NEVER -> "PAUSED"
        }
    }

    val heroDescription = remember(currentInterval) {
        when (currentInterval) {
            ChangeInterval.EVERY_UNLOCK -> "Wallpapers cycle each time you unlock your device, with a 1-minute cooldown to save battery."
            ChangeInterval.FIFTEEN_MINUTES -> "Wallpapers rotate automatically every 15 minutes in the background."
            ChangeInterval.HOURLY -> "Wallpapers update at the top of every hour (24 times daily)."
            ChangeInterval.THREE_HOURS -> "Wallpapers update every 3 hours (8 times daily)."
            ChangeInterval.SIX_HOURS -> "Wallpapers update every 6 hours (4 times daily)."
            ChangeInterval.TWELVE_HOURS -> "Wallpapers update twice daily, in the morning and evening."
            ChangeInterval.DAILY -> "Wallpapers change once a day at your scheduled time."
            ChangeInterval.THREE_DAYS -> "Wallpapers change once every 3 days."
            ChangeInterval.SEVEN_DAYS -> "Wallpapers change once a week on a 7-day cycle."
            ChangeInterval.NEVER -> "Automatic wallpaper changes are paused."
        }
    }

    val trayShape = RoundedCornerShape(13.dp)
    val segmentShape = RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                RadicalIconBadge(
                    icon = heroIcon,
                    accentColor = if (currentInterval == ChangeInterval.NEVER) RadicalPalette.RubyRed else accentColor,
                    isDark = isDark,
                    size = 42.dp,
                    iconSize = 22.dp
                )
                Column {
                    Text(
                        text = heroTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
                    )
                    Text(
                        text = heroSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isDark) {
                            (if (currentInterval == ChangeInterval.NEVER) RadicalPalette.RubyRed else accentColor).copy(alpha = 0.15f)
                        } else Color.White.copy(alpha = 0.15f)
                    )
                    .border(
                        1.dp,
                        if (isDark) {
                            (if (currentInterval == ChangeInterval.NEVER) RadicalPalette.RubyRed else accentColor).copy(alpha = 0.35f)
                        } else Color.White.copy(alpha = 0.25f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = heroBadge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    color = if (isDark) {
                        if (currentInterval == ChangeInterval.NEVER) RadicalPalette.RubyRed else accentColor
                    } else Color.White
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(trayShape)
                .background(if (isDark) RadicalPalette.DarkCardWell else RadicalPalette.LightCardWell)
                .border(
                    width = 1.dp,
                    brush = if (isDark) {
                        Brush.verticalGradient(listOf(Color(0xFF9E9689), Color(0xFFEBE6DC)))
                    } else {
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.White.copy(alpha = 0.20f)))
                    },
                    shape = trayShape
                )
                .padding(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FrequencyModeGroup.entries.forEach { group ->
                    val isGroupSelected = activeGroup == group
                    val segmentElevation by animateDpAsState(
                        targetValue = if (isGroupSelected) 3.dp else 0.dp,
                        animationSpec = tween(160),
                        label = "groupElev_${group.name}"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .shadow(
                                elevation = segmentElevation,
                                shape = segmentShape,
                                ambientColor = Color.Black.copy(alpha = 0.25f),
                                spotColor = Color.Black.copy(alpha = 0.18f)
                            )
                            .clip(segmentShape)
                            .background(
                                if (isGroupSelected) {
                                    if (isDark) {
                                        if (group == FrequencyModeGroup.OFF) {
                                            Brush.verticalGradient(listOf(RadicalPalette.RubyRed, Color(0xFF991B1B)))
                                        } else {
                                            Brush.verticalGradient(listOf(RadicalPalette.CyberMagenta, RadicalPalette.CyberMagentaDark))
                                        }
                                    } else {
                                        Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F5F9)))
                                    }
                                } else {
                                    SolidColor(Color.Transparent)
                                }
                            )
                            .then(
                                if (isGroupSelected) {
                                    Modifier.border(
                                        width = 1.dp,
                                        brush = Brush.verticalGradient(
                                            if (isDark) {
                                                listOf(Color.White.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.25f))
                                            } else {
                                                listOf(Color.White, Color(0xFFCBD5E1))
                                            }
                                        ),
                                        shape = segmentShape
                                    )
                                } else Modifier
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                                onClick = {
                                    if (activeGroup != group) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        when (group) {
                                            FrequencyModeGroup.UNLOCK -> onIntervalSelected(ChangeInterval.EVERY_UNLOCK)
                                            FrequencyModeGroup.HOURLY -> onIntervalSelected(ChangeInterval.HOURLY)
                                            FrequencyModeGroup.DAYS -> onIntervalSelected(ChangeInterval.DAILY)
                                            FrequencyModeGroup.OFF -> onIntervalSelected(ChangeInterval.NEVER)
                                        }
                                    }
                                }
                            )
                            .padding(vertical = 10.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = group.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isGroupSelected) {
                                    if (isDark) Color.White else (if (group == FrequencyModeGroup.OFF) RadicalPalette.RubyRed else Color(0xFF064E3B))
                                } else {
                                    if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                                }
                            )
                            Text(
                                text = group.shortLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isGroupSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isGroupSelected) {
                                    if (isDark) Color.White else (if (group == FrequencyModeGroup.OFF) RadicalPalette.RubyRed else Color(0xFF064E3B))
                                } else {
                                    if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                                },
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = activeGroup == FrequencyModeGroup.HOURLY,
            enter = expandVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(140)),
            exit = shrinkVertically(animationSpec = tween(140, easing = FastOutLinearInEasing)) + fadeOut(animationSpec = tween(100))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hourly frequency:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                    )
                    Text(
                        text = hourlyOptions.firstOrNull { it.interval == currentInterval }?.efficiency ?: "Precision Alarm",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) accentColor else Color.White.copy(alpha = 0.85f)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(trayShape)
                        .background(if (isDark) RadicalPalette.DarkCardWell.copy(alpha = 0.6f) else RadicalPalette.LightCardWell.copy(alpha = 0.6f))
                        .border(
                            1.dp,
                            if (isDark) Color(0xFFB5ADA1).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.15f),
                            trayShape
                        )
                        .padding(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        hourlyOptions.forEach { opt ->
                            val isSelected = currentInterval == opt.interval
                            val elev by animateDpAsState(
                                targetValue = if (isSelected) 3.dp else 0.dp,
                                animationSpec = tween(140),
                                label = "hourlyElev_${opt.label}"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .shadow(elevation = elev, shape = segmentShape)
                                    .clip(segmentShape)
                                    .background(
                                        if (isSelected) {
                                            if (isDark) Brush.verticalGradient(listOf(Color.White, Color(0xFFE5DFD5)))
                                            else Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F5F9)))
                                        } else SolidColor(Color.Transparent)
                                    )
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(
                                                1.dp,
                                                if (isDark) Color(0xFFB5ADA1) else Color(0xFFCBD5E1),
                                                segmentShape
                                            )
                                        } else Modifier
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = true),
                                        onClick = {
                                            if (!isSelected) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onIntervalSelected(opt.interval)
                                            }
                                        }
                                    )
                                    .padding(vertical = 7.dp, horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Text(
                                        text = opt.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) {
                                            if (isDark) RadicalPalette.DarkCardTextPrimary else Color(0xFF064E3B)
                                        } else {
                                            if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                                        },
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = opt.sublabel,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) {
                                            if (isDark) RadicalPalette.EmeraldJade else Color(0xFF047857)
                                        } else {
                                            if (isDark) RadicalPalette.DarkCardTextTertiary else RadicalPalette.LightCardTextTertiary
                                        },
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = activeGroup == FrequencyModeGroup.DAYS,
            enter = expandVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(140)),
            exit = shrinkVertically(animationSpec = tween(140, easing = FastOutLinearInEasing)) + fadeOut(animationSpec = tween(100))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Cadence duration:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(trayShape)
                        .background(if (isDark) RadicalPalette.DarkCardWell.copy(alpha = 0.6f) else RadicalPalette.LightCardWell.copy(alpha = 0.6f))
                        .border(
                            1.dp,
                            if (isDark) Color(0xFFB5ADA1).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.15f),
                            trayShape
                        )
                        .padding(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        daysOptions.forEach { opt ->
                            val isSelected = currentInterval == opt.interval
                            val elev by animateDpAsState(
                                targetValue = if (isSelected) 3.dp else 0.dp,
                                animationSpec = tween(140),
                                label = "daysElev_${opt.label}"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .shadow(elevation = elev, shape = segmentShape)
                                    .clip(segmentShape)
                                    .background(
                                        if (isSelected) {
                                            if (isDark) Brush.verticalGradient(listOf(Color.White, Color(0xFFE5DFD5)))
                                            else Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F5F9)))
                                        } else SolidColor(Color.Transparent)
                                    )
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(
                                                1.dp,
                                                if (isDark) Color(0xFFB5ADA1) else Color(0xFFCBD5E1),
                                                segmentShape
                                            )
                                        } else Modifier
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = true),
                                        onClick = {
                                            if (!isSelected) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onIntervalSelected(opt.interval)
                                            }
                                        }
                                    )
                                    .padding(vertical = 7.dp, horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Text(
                                        text = opt.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) {
                                            if (isDark) RadicalPalette.DarkCardTextPrimary else Color(0xFF064E3B)
                                        } else {
                                            if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                                        },
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = opt.sublabel,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) {
                                            if (isDark) RadicalPalette.EmeraldJade else Color(0xFF047857)
                                        } else {
                                            if (isDark) RadicalPalette.DarkCardTextTertiary else RadicalPalette.LightCardTextTertiary
                                        },
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = currentInterval == ChangeInterval.DAILY,
            enter = expandVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(140)),
            exit = shrinkVertically(animationSpec = tween(140, easing = FastOutLinearInEasing)) + fadeOut(animationSpec = tween(100))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isDark) RadicalPalette.DarkCardWell.copy(alpha = 0.5f) else RadicalPalette.LightCardWell.copy(alpha = 0.5f)
                    )
                    .border(
                        1.dp,
                        if (isDark) Color(0xFFB5ADA1) else Color.White.copy(alpha = 0.15f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = onOpenFullTimePicker
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "Daily Change Time",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Scheduled daily (tap to adjust)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .shadow(2.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isDark) Brush.verticalGradient(listOf(Color.White, Color(0xFFE5DFD5))) else Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F5F9)))
                        )
                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = formattedDailyTime,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) RadicalPalette.DarkCardTextPrimary else Color(0xFF064E3B),
                            maxLines = 1
                        )
                        Text(
                            text = "· $timeOfDayTag",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) Color(0xFF064E3B) else Color(0xFF047857),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (currentInterval == ChangeInterval.NEVER) RadicalPalette.RubyRed else accentColor)
                    .shadow(2.dp, CircleShape)
            )
            Text(
                text = heroDescription,
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * Backwards compatible alias forwarding to RadicalFrequencyStudio
 */
@Composable
fun MinimalSlidingFrequencyControl(
    currentInterval: ChangeInterval,
    onIntervalSelected: (ChangeInterval) -> Unit,
    dailyTime: DailyTime?,
    onOpenFullTimePicker: () -> Unit,
    isDark: Boolean,
    accentColor: Color = RadicalPalette.EmeraldJade,
    modifier: Modifier = Modifier
) {
    RadicalFrequencyStudio(
        currentInterval = currentInterval,
        onIntervalSelected = onIntervalSelected,
        dailyTime = dailyTime,
        onOpenFullTimePicker = onOpenFullTimePicker,
        isDark = isDark,
        accentColor = accentColor,
        modifier = modifier
    )
}

@Composable
private fun VolumeRockerButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isDark: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(12.dp)

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = tween(100),
        label = "rockerScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isPressed || !enabled) 1.dp else 4.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.18f)
            )
            .clip(shape)
            .background(
                if (isDark) {
                    if (enabled) {
                        Brush.verticalGradient(listOf(Color.White, Color(0xFFE5DFD5)))
                    } else {
                        SolidColor(RadicalPalette.DarkCardWell.copy(alpha = 0.6f))
                    }
                } else {
                    if (enabled) {
                        Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F5F9)))
                    } else {
                        SolidColor(RadicalPalette.LightCardWell.copy(alpha = 0.6f))
                    }
                }
            )
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White,
                        if (isDark) Color(0xFFB5ADA1) else Color(0xFFCBD5E1)
                    )
                ),
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) (if (isDark) RadicalPalette.DarkCardTextPrimary else Color(0xFF064E3B)) else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = if (enabled) (if (isDark) RadicalPalette.DarkCardTextPrimary else Color(0xFF064E3B)) else Color.Gray
            )
        }
    }
}

@Composable
private fun MechanicalDrumCylinder(
    value: Int,
    label: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    isDark: Boolean,
    accentColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp, 22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isDark) Color(0xFF10141D) else Color.White)
                .border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    onClick = onIncrement
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.KeyboardArrowUp, null, tint = accentColor, modifier = Modifier.size(16.dp))
        }

        Box(
            modifier = Modifier
                .size(width = 68.dp, height = 62.dp)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF070A0F),
                            Color(0xFF131924),
                            Color(0xFF1E2738),
                            Color(0xFF131924),
                            Color(0xFF070A0F)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val midY = size.height * 0.5f
                drawLine(
                    color = Color.Black.copy(alpha = 0.85f),
                    start = Offset(0f, midY),
                    end = Offset(size.width, midY),
                    strokeWidth = 2f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(0f, midY + 1f),
                    end = Offset(size.width, midY + 1f),
                    strokeWidth = 0.8f
                )
            }

            Text(
                text = "%02d".format(value),
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFF8FAFC),
                letterSpacing = 1.sp
            )
        }

        Box(
            modifier = Modifier
                .size(34.dp, 22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isDark) Color(0xFF10141D) else Color.White)
                .border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    onClick = onDecrement
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.KeyboardArrowDown, null, tint = accentColor, modifier = Modifier.size(16.dp))
        }

        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun MechanicalAmPmRocker(
    isPm: Boolean,
    onToggle: (Boolean) -> Unit,
    isDark: Boolean,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .width(46.dp)
            .height(98.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDark) Color(0xFF0F141E) else Color(0xFFE2E8F0))
            .border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
            .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(if (!isPm) 3.dp else 0.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (!isPm) {
                        Brush.verticalGradient(listOf(accentColor, RadicalPalette.CyberMagentaDark))
                    } else {
                        SolidColor(Color.Transparent)
                    }
                )
                .clickable { onToggle(false) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "AM",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (!isPm) Color.White else (if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8))
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(if (isPm) 3.dp else 0.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isPm) {
                        Brush.verticalGradient(listOf(accentColor, RadicalPalette.CyberMagentaDark))
                    } else {
                        SolidColor(Color.Transparent)
                    }
                )
                .clickable { onToggle(true) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "PM",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPm) Color.White else (if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8))
            )
        }
    }
}

@Composable
fun RadicalSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = RadicalPalette.CyberMagenta
) {
    val haptic = LocalHapticFeedback.current
    val trayShape = RoundedCornerShape(14.dp)
    val segmentShape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(trayShape)
            .background(
                if (isDark) RadicalPalette.DarkCardWell else RadicalPalette.LightCardWell
            )
            .border(
                width = 1.dp,
                brush = if (isDark) {
                    Brush.verticalGradient(listOf(Color(0xFF9E9689), Color(0xFFEBE6DC)))
                } else {
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.White.copy(alpha = 0.20f)))
                },
                shape = trayShape
            )
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEachIndexed { index, title ->
                val isSelected = index == selectedIndex

                val segmentElevation by animateDpAsState(
                    targetValue = if (isSelected) 3.dp else 0.dp,
                    animationSpec = tween(180),
                    label = "segElev_$index"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(
                            elevation = segmentElevation,
                            shape = segmentShape,
                            ambientColor = Color.Black.copy(alpha = 0.25f),
                            spotColor = Color.Black.copy(alpha = 0.18f)
                        )
                        .clip(segmentShape)
                        .background(
                            if (isSelected) {
                                Brush.verticalGradient(
                                    listOf(
                                        RadicalPalette.CyberMagenta,
                                        RadicalPalette.CyberMagentaDark
                                    )
                                )
                            } else {
                                SolidColor(Color.Transparent)
                            }
                        )
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.40f), Color.Black.copy(alpha = 0.20f))
                                    ),
                                    shape = segmentShape
                                )
                            } else Modifier
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = {
                                if (!isSelected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onItemSelected(index)
                                }
                            }
                        )
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            Color.White
                        } else {
                            if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

data class ApplyToOption(
    val target: ApplyTo,
    val shortLabel: String,
    val title: String,
    val description: String,
    val icon: ImageVector
)

@Composable
fun RadicalApplyToSelector(
    selectedTarget: ApplyTo,
    onTargetSelected: (ApplyTo) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = RadicalPalette.TealCyan
) {
    val haptic = LocalHapticFeedback.current
    val options = remember {
        listOf(
            ApplyToOption(ApplyTo.LOCK_SCREEN, "Lock", "Lock Screen", "Applies wallpaper exclusively to the lock screen", Icons.Default.Lock),
            ApplyToOption(ApplyTo.HOME_SCREEN, "Home", "Home Screen", "Applies wallpaper exclusively to the home screen", Icons.Default.Home),
            ApplyToOption(ApplyTo.BOTH, "Both", "Both Screens", "Same synchronized wallpaper on both lock and home screens", Icons.Default.Smartphone),
            ApplyToOption(ApplyTo.BOTH_DIFFERENT, "Distinct", "Distinct / Both", "Different wallpapers independently curated for lock and home", Icons.Default.Splitscreen)
        )
    }

    val selectedOption = options.firstOrNull { it.target == selectedTarget } ?: options[2]
    val trayShape = RoundedCornerShape(14.dp)
    val segmentShape = RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(trayShape)
                .background(if (isDark) RadicalPalette.DarkCardWell else RadicalPalette.LightCardWell)
                .border(
                    width = 1.dp,
                    brush = if (isDark) {
                        Brush.verticalGradient(listOf(Color(0xFF9E9689), Color(0xFFEBE6DC)))
                    } else {
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.White.copy(alpha = 0.20f)))
                    },
                    shape = trayShape
                )
                .padding(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { option ->
                    val isSelected = option.target == selectedTarget
                    val segmentElevation by animateDpAsState(
                        targetValue = if (isSelected) 3.dp else 0.dp,
                        animationSpec = tween(180),
                        label = "applyElev_${option.shortLabel}"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .shadow(
                                elevation = segmentElevation,
                                shape = segmentShape,
                                ambientColor = Color.Black.copy(alpha = 0.25f),
                                spotColor = Color.Black.copy(alpha = 0.18f)
                            )
                            .clip(segmentShape)
                            .background(
                                if (isSelected) {
                                    Brush.verticalGradient(
                                        listOf(
                                            RadicalPalette.CyberMagenta,
                                            RadicalPalette.CyberMagentaDark
                                        )
                                    )
                                } else {
                                    SolidColor(Color.Transparent)
                                }
                            )
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 1.dp,
                                        brush = Brush.verticalGradient(
                                            listOf(Color.White.copy(alpha = 0.40f), Color.Black.copy(alpha = 0.20f))
                                        ),
                                        shape = segmentShape
                                    )
                                } else Modifier
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                                onClick = {
                                    if (!isSelected) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onTargetSelected(option.target)
                                    }
                                }
                            )
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) {
                                    Color.White
                                } else {
                                    if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                                }
                            )
                            Text(
                                text = option.shortLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    Color.White
                                } else {
                                    if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                                },
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor)
                    .shadow(2.dp, CircleShape, ambientColor = accentColor, spotColor = accentColor)
            )
            Text(
                text = selectedOption.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
fun RadicalTactileSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = RadicalPalette.CyberMagenta
) {
    val haptic = LocalHapticFeedback.current
    var sliderWidthPx by remember { mutableStateOf(1f) }
    val density = LocalDensity.current

    val thumbRadiusDp = 13.dp
    val thumbRadiusPx = with(density) { thumbRadiusDp.toPx() }
    val trackHeightDp = 8.dp

    val totalRange = valueRange.endInclusive - valueRange.start
    val fraction = ((value - valueRange.start) / totalRange).coerceIn(0f, 1f)
    val trackShape = RoundedCornerShape(trackHeightDp / 2)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .onSizeChanged { sliderWidthPx = it.width.toFloat() }
            .pointerInput(valueRange, steps) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val usableWidth = sliderWidthPx - (thumbRadiusPx * 2)
                        val touchX = (offset.x - thumbRadiusPx).coerceIn(0f, usableWidth)
                        val newFraction = touchX / usableWidth
                        val rawVal = valueRange.start + (newFraction * totalRange)
                        val stepCount = steps + 1
                        val snappedVal = if (steps > 0) {
                            val stepSize = totalRange / stepCount
                            (rawVal / stepSize).roundToInt() * stepSize
                        } else rawVal
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onValueChange(snappedVal.coerceIn(valueRange.start, valueRange.endInclusive))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val usableWidth = sliderWidthPx - (thumbRadiusPx * 2)
                        val touchX = (change.position.x - thumbRadiusPx).coerceIn(0f, usableWidth)
                        val newFraction = touchX / usableWidth
                        val rawVal = valueRange.start + (newFraction * totalRange)
                        val stepCount = steps + 1
                        val snappedVal = if (steps > 0) {
                            val stepSize = totalRange / stepCount
                            (rawVal / stepSize).roundToInt() * stepSize
                        } else rawVal
                        onValueChange(snappedVal.coerceIn(valueRange.start, valueRange.endInclusive))
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeightDp)
                .clip(trackShape)
                .background(
                    if (isDark) RadicalPalette.DarkCardWell else RadicalPalette.LightCardWell
                )
                .border(
                    width = 1.dp,
                    brush = if (isDark) {
                        Brush.verticalGradient(listOf(Color(0xFF9E9689), Color(0xFFEBE6DC)))
                    } else {
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.White.copy(alpha = 0.20f)))
                    },
                    shape = trackShape
                )
        ) {
            val fillWidthFraction = fraction.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fillWidthFraction)
                    .clip(trackShape)
                    .background(
                        if (isDark) {
                            Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.8f), accentColor))
                        } else {
                            Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.8f), Color.White))
                        }
                    )
            )
        }

        val usableWidthPx = (sliderWidthPx - (thumbRadiusPx * 2)).coerceAtLeast(0f)
        val thumbOffsetDp = with(density) { (fraction * usableWidthPx).toDp() }
        val thumbShape = CircleShape

        Box(
            modifier = Modifier
                .offset(x = thumbOffsetDp)
                .size(thumbRadiusDp * 2)
                .shadow(
                    elevation = 4.dp,
                    shape = thumbShape,
                    ambientColor = Color.Black.copy(alpha = 0.25f),
                    spotColor = Color.Black.copy(alpha = 0.20f)
                )
                .clip(thumbShape)
                .background(
                    if (isDark) {
                        Brush.verticalGradient(listOf(Color.White, Color(0xFFE5DFD5)))
                    } else {
                        Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F5F9)))
                    }
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White, if (isDark) Color(0xFFB5ADA1) else Color(0xFFCBD5E1))
                    ),
                    shape = thumbShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val r = size.width * 0.5f
                val center = Offset(r, r)

                drawCircle(
                    color = Color(0xFFCBD5E1),
                    radius = r - 3.5f,
                    center = center,
                    style = Stroke(width = 0.8f)
                )
            }

            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accentColor)
                    .shadow(3.dp, CircleShape, ambientColor = accentColor, spotColor = accentColor)
            )
        }
    }
}

enum class RadicalButtonVariant {
    Primary,
    Secondary,
    Danger
}

@Composable
fun RadicalTactileButton(
    text: String,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    variant: RadicalButtonVariant = RadicalButtonVariant.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.97f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "btnScale"
    )

    val (bgBrush, borderBrush, textColor) = when (variant) {
        RadicalButtonVariant.Primary -> if (isDark) {
            Triple(
                Brush.verticalGradient(listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))),
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color(0xFF1E3A8A))),
                Color.White
            )
        } else {
            Triple(
                Brush.verticalGradient(listOf(Color(0xFF047857), Color(0xFF065F46))),
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color(0xFF064E3B))),
                Color.White
            )
        }
        RadicalButtonVariant.Secondary -> if (isDark) {
            Triple(
                Brush.verticalGradient(listOf(Color(0xFFFAF8F5), Color(0xFFE2DDD5))),
                Brush.verticalGradient(listOf(Color.White, Color(0xFFB5ADA1))),
                RadicalPalette.DarkCardTextPrimary
            )
        } else {
            Triple(
                Brush.verticalGradient(listOf(Color(0xFF065F46), Color(0xFF044E3B))),
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color(0xFF02241A))),
                Color.White
            )
        }
        RadicalButtonVariant.Danger -> Triple(
            Brush.verticalGradient(
                listOf(Color(0xFFDC2626), Color(0xFF991B1B))
            ),
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.45f), Color(0xFF7F1D1D))
            ),
            Color.White
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isPressed || !enabled) 1.dp else 4.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.18f)
            )
            .clip(shape)
            .background(bgBrush)
            .border(width = 1.2.dp, brush = borderBrush, shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                enabled = enabled && !isLoading,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .padding(vertical = 14.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = textColor
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Processing…",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            } else {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = textColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

@Composable
fun RadicalNavigationRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    isDark: Boolean,
    leadingIcon: ImageVector,
    iconAccentColor: Color,
    trailingIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowForward,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadicalIconBadge(
            icon = leadingIcon,
            accentColor = iconAccentColor,
            isDark = isDark
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (isDark) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = if (isDark) RadicalPalette.DarkCardTextSecondary else Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun RadicalInfoRow(
    label: String,
    value: String,
    isDark: Boolean,
    leadingIcon: ImageVector,
    iconAccentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadicalIconBadge(
            icon = leadingIcon,
            accentColor = iconAccentColor,
            isDark = isDark
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun RadicalSourceToggle(
    sourceName: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Collections,
    iconAccentColor: Color = RadicalPalette.EmeraldJade,
    description: String? = null,
    depthLabel: String? = null,
    onDepthClick: (() -> Unit)? = null,
    accentColor: Color = RadicalPalette.EmeraldJade
) {
    val haptic = LocalHapticFeedback.current
    val boxShape = RoundedCornerShape(9.dp)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggle(!enabled)
                    }
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadicalIconBadge(
                    icon = icon,
                    accentColor = iconAccentColor,
                    isDark = isDark
                )

                Column {
                    Text(
                        text = sourceName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
                    )
                    Text(
                        text = description ?: if (enabled) "Enabled & active" else "Disabled",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) {
                            if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary
                        } else {
                            if (isDark) RadicalPalette.DarkCardTextTertiary else RadicalPalette.LightCardTextTertiary
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .shadow(
                        elevation = if (enabled) 3.dp else 0.dp,
                        shape = boxShape,
                        ambientColor = Color.Black.copy(alpha = 0.20f),
                        spotColor = Color.Black.copy(alpha = 0.15f)
                    )
                    .clip(boxShape)
                    .background(
                        if (enabled) {
                            if (isDark) {
                                Brush.verticalGradient(listOf(accentColor, Color(0xFF059669).copy(alpha = 0.8f)))
                            } else {
                                Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F5F9)))
                            }
                        } else {
                            if (isDark) SolidColor(RadicalPalette.DarkCardWell) else SolidColor(RadicalPalette.LightCardWell)
                        }
                    )
                    .border(
                        width = 1.dp,
                        brush = if (enabled) {
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.2f))
                            )
                        } else {
                            if (isDark) SolidColor(Color(0xFFB5ADA1)) else SolidColor(Color.White.copy(alpha = 0.25f))
                        },
                        shape = boxShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (enabled) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (isDark) Color.White else Color(0xFF064E3B),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (enabled && depthLabel != null && onDepthClick != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 74.dp, end = 18.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Depth:",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) RadicalPalette.DarkCardTextTertiary else RadicalPalette.LightCardTextTertiary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isDark) iconAccentColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f)
                            )
                            .border(
                                1.dp,
                                if (isDark) iconAccentColor.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.25f),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDepthClick()
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = depthLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) iconAccentColor else Color.White
                            )
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Change depth",
                                tint = if (isDark) iconAccentColor else Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RadicalProgressMeter(
    progress: Float,
    label: String,
    sublabel: String? = null,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = RadicalPalette.SapphireBlue,
    isLoading: Boolean = true
) {
    val trackShape = RoundedCornerShape(4.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = if (isDark) accentColor else Color.White
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
                )
            }

            sublabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) accentColor else Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(trackShape)
                .background(
                    if (isDark) RadicalPalette.DarkCardWell else RadicalPalette.LightCardWell
                )
        ) {
            val validProgress = progress.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(validProgress)
                    .clip(trackShape)
                    .background(
                        if (isDark) {
                            Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.8f), accentColor))
                        } else {
                            Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.8f), Color.White))
                        }
                    )
            )
        }
    }
}

@Composable
fun RadicalDivider(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                if (isDark) RadicalPalette.DarkCardWell else Color.White.copy(alpha = 0.15f)
            )
    )
}

@Composable
fun RadicalBatteryNoticePod(
    onConfigureClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.30f),
                spotColor = Color.Black.copy(alpha = 0.20f)
            )
            .clip(shape)
            .background(
                if (isDark) {
                    Brush.verticalGradient(listOf(Color(0xFF241419), Color(0xFF190C11)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF2B0A12), Color(0xFF1A050A)))
                }
            )
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        RadicalPalette.RubyRed.copy(alpha = 0.75f),
                        RadicalPalette.RubyRed.copy(alpha = 0.20f)
                    )
                ),
                shape = shape
            )
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(RadicalPalette.RubyRed)
                            .shadow(2.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.2f))
                    )
                    Text(
                        text = "BATTERY OPTIMIZATION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = RadicalPalette.RubyRed,
                        letterSpacing = 1.4.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(RadicalPalette.RubyRed.copy(alpha = 0.20f))
                        .border(1.dp, RadicalPalette.RubyRed.copy(alpha = 0.50f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "RESTRICTED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(3.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(RadicalPalette.RubyRed.copy(alpha = 0.35f), RadicalPalette.RubyRed.copy(alpha = 0.15f))
                            )
                        )
                        .border(1.dp, RadicalPalette.RubyRed.copy(alpha = 0.60f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint = RadicalPalette.RubyRed,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Background Auto-Rotation Restricted",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 22.sp
                    )
                    Text(
                        text = "Android may delay scheduled wallpaper updates when battery optimization is on. Allow background activity to ensure wallpapers update on schedule.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFECDD3),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            RadicalTactileButton(
                text = "Configure Battery Exemption",
                icon = Icons.Default.BatteryChargingFull,
                onClick = onConfigureClick,
                isDark = isDark,
                variant = RadicalButtonVariant.Danger
            )
        }
    }
}

@Composable
fun RadicalNoticeCard(
    title: String,
    message: String,
    icon: ImageVector = Icons.Default.Warning,
    accentColor: Color = RadicalPalette.RadiantAmber,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    actionButton: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 1.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.05f)
            )
            .clip(shape)
            .background(
                if (isDark) {
                    accentColor.copy(alpha = 0.12f)
                } else {
                    accentColor.copy(alpha = 0.08f)
                }
            )
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = if (isDark) 0.35f else 0.25f),
                shape = shape
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                RadicalIconBadge(
                    icon = icon,
                    accentColor = accentColor,
                    isDark = isDark,
                    size = 36.dp,
                    iconSize = 18.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) accentColor else if (accentColor == RadicalPalette.RadiantAmber) Color(0xFFB45309) else accentColor
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            actionButton?.let {
                it()
            }
        }
    }
}

@Composable
fun RadicalAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit,
    isDark: Boolean,
    confirmColor: Color = RadicalPalette.CyberMagenta
) {
    val dialogShape = RoundedCornerShape(22.dp)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = if (isDark) Color(0xFF131722) else Color.White,
        shape = dialogShape,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isDark) TextPrimaryDark else TextPrimaryLight
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) TextSecondaryDark else TextSecondaryLight,
                lineHeight = 22.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = confirmColor,
                    contentColor = Color.White
                )
            ) {
                Text(confirmText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    dismissText,
                    color = if (isDark) TextSecondaryDark else TextSecondaryLight,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}

data class TimePreset(
    val label: String,
    val hour: Int,
    val minute: Int,
    val icon: ImageVector
)

@Composable
fun RadicalTimePickerDialog(
    onDismissRequest: () -> Unit,
    initialHour: Int,
    initialMinute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDark: Boolean
) {
    val dialogShape = RoundedCornerShape(24.dp)
    val haptic = LocalHapticFeedback.current

    val presets = remember {
        listOf(
            TimePreset("07:00 Sunrise", 7, 0, Icons.Default.WbSunny),
            TimePreset("09:00 Morning", 9, 0, Icons.Default.WorkOutline),
            TimePreset("18:00 Sunset", 18, 0, Icons.Default.WbTwilight),
            TimePreset("22:00 Night", 22, 0, Icons.Default.NightlightRound)
        )
    }

    val isPm = initialHour >= 12
    val display12Hour = when {
        initialHour == 0 -> 12
        initialHour > 12 -> initialHour - 12
        else -> initialHour
    }
    val amPmString = if (isPm) "PM" else "AM"
    val formatted12H = String.format("%02d:%02d %s", display12Hour, initialMinute, amPmString)
    val formatted24H = String.format("%02d:%02d", initialHour, initialMinute)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = if (isDark) RadicalPalette.DarkCanvasMid else Color.White,
        shape = dialogShape,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Daily Change Schedule",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextPrimaryDark else TextPrimaryLight
                )
                Text(
                    text = "Wallpapers will change automatically at this time",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) TextSecondaryDark else TextSecondaryLight,
                    modifier = Modifier.padding(top = 4.dp),
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        val isPresetActive = initialHour == preset.hour && initialMinute == preset.minute
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isPresetActive) {
                                        if (isDark) RadicalPalette.EmeraldJade.copy(alpha = 0.25f) else RadicalPalette.EmeraldJade.copy(alpha = 0.15f)
                                    } else {
                                        if (isDark) Color(0xFF1E2433) else Color(0xFFF1F5F9)
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (isPresetActive) RadicalPalette.EmeraldJade else (if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onHourChange(preset.hour)
                                        onMinuteChange(preset.minute)
                                    }
                                )
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = preset.icon,
                                    contentDescription = null,
                                    tint = if (isPresetActive) RadicalPalette.EmeraldJade else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "%02d:%02d".format(preset.hour, preset.minute),
                                    fontSize = 11.sp,
                                    fontWeight = if (isPresetActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isPresetActive) RadicalPalette.EmeraldJade else (if (isDark) TextPrimaryDark else TextPrimaryLight)
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isDark) RadicalPalette.DarkCanvasBase else Color(0xFFFAF6EE)
                        )
                        .border(
                            1.dp,
                            if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(vertical = 16.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp, 28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0xFF1E2433) else Color.White)
                                    .border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onHourChange((initialHour + 1) % 24)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, "Increment Hour", tint = RadicalPalette.EmeraldJade, modifier = Modifier.size(20.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .size(width = 68.dp, height = 56.dp)
                                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(10.dp))
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = if (isDark) {
                                                listOf(Color(0xFF070A0F), Color(0xFF131924), Color(0xFF1E2738), Color(0xFF131924), Color(0xFF070A0F))
                                            } else {
                                                listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF020617))
                                            }
                                        )
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    val midY = size.height * 0.5f
                                    drawLine(
                                        color = Color.Black.copy(alpha = 0.85f),
                                        start = Offset(0f, midY),
                                        end = Offset(size.width, midY),
                                        strokeWidth = 2f
                                    )
                                }
                                Text(
                                    text = "%02d".format(initialHour),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(40.dp, 28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0xFF1E2433) else Color.White)
                                    .border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onHourChange(if (initialHour == 0) 23 else initialHour - 1)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, "Decrement Hour", tint = RadicalPalette.EmeraldJade, modifier = Modifier.size(20.dp))
                            }

                            Text(
                                text = "HOUR",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                letterSpacing = 1.sp
                            )
                        }

                        Text(
                            text = ":",
                            fontSize = 32.sp,
                            color = RadicalPalette.EmeraldJade,
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
                            fontWeight = FontWeight.Bold
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp, 28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0xFF1E2433) else Color.White)
                                    .border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onMinuteChange((initialMinute + 15) % 60)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, "Increment Minute", tint = RadicalPalette.EmeraldJade, modifier = Modifier.size(20.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .size(width = 68.dp, height = 56.dp)
                                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(10.dp))
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = if (isDark) {
                                                listOf(Color(0xFF070A0F), Color(0xFF131924), Color(0xFF1E2738), Color(0xFF131924), Color(0xFF070A0F))
                                            } else {
                                                listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF020617))
                                            }
                                        )
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    val midY = size.height * 0.5f
                                    drawLine(
                                        color = Color.Black.copy(alpha = 0.85f),
                                        start = Offset(0f, midY),
                                        end = Offset(size.width, midY),
                                        strokeWidth = 2f
                                    )
                                }
                                Text(
                                    text = "%02d".format(initialMinute),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(40.dp, 28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0xFF1E2433) else Color.White)
                                    .border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onMinuteChange(if (initialMinute == 0) 45 else initialMinute - 15)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, "Decrement Minute", tint = RadicalPalette.EmeraldJade, modifier = Modifier.size(20.dp))
                            }

                            Text(
                                text = "MINUTE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isDark) RadicalPalette.EmeraldJade.copy(alpha = 0.15f) else Color(0xFFECFDF5)
                        )
                        .border(
                            1.dp,
                            RadicalPalette.EmeraldJade.copy(alpha = 0.35f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$formatted12H ($formatted24H hrs)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = RadicalPalette.EmeraldJade
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RadicalPalette.EmeraldJade,
                    contentColor = Color.White
                )
            ) {
                Text("Set Daily Schedule", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "Cancel",
                    color = if (isDark) TextSecondaryDark else TextSecondaryLight,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}

@Composable
fun RadicalRadioRow(
    title: String,
    subtitle: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDark: Boolean,
    accentColor: Color = RadicalPalette.SapphireBlue,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = accentColor.copy(alpha = 0.2f)),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) accentColor else (if (isDark) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.15f))
                )
                .border(
                    width = 1.2.dp,
                    color = if (isSelected) accentColor else (if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (isDark) RadicalPalette.DarkCardTextPrimary else RadicalPalette.LightCardTextPrimary
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) accentColor else (if (isDark) RadicalPalette.DarkCardTextSecondary else RadicalPalette.LightCardTextSecondary),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) RadicalPalette.DarkCardTextTertiary else RadicalPalette.LightCardTextSecondary,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
fun RadicalRadioCard(
    title: String,
    subtitle: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDark: Boolean,
    accentColor: Color = RadicalPalette.SapphireBlue,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isSelected) 4.dp else 0.dp,
                shape = cardShape,
                ambientColor = Color.Black.copy(alpha = 0.15f),
                spotColor = Color.Black.copy(alpha = 0.10f)
            )
            .clip(cardShape)
            .background(
                if (isSelected) {
                    accentColor.copy(alpha = 0.15f)
                } else {
                    if (isDark) Color(0xFF1E2433) else Color.White
                }
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accentColor else (if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                shape = cardShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = accentColor.copy(alpha = 0.2f)),
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) accentColor else (if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color.White else (if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1)),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextPrimaryDark else TextPrimaryLight
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 1.dp)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) TextSecondaryDark else TextSecondaryLight,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
fun RadicalBingTypeDialog(
    onDismissRequest: () -> Unit,
    selectedType: String,
    onTypeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    isDark: Boolean
) {
    val dialogShape = RoundedCornerShape(22.dp)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = if (isDark) RadicalPalette.DarkCanvasMid else Color.White,
        shape = dialogShape,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Choose Bing Collection",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextPrimaryDark else TextPrimaryLight
                )
                Text(
                    text = "Select wallpaper catalog depth",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) TextSecondaryDark else TextSecondaryLight,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                RadicalRadioCard(
                    title = "Recent Hits",
                    subtitle = "Last 3 years · ~1000 wallpapers",
                    description = "Faster download, curated recent highlights",
                    isSelected = selectedType == "lite",
                    onClick = { onTypeChange("lite") },
                    isDark = isDark,
                    accentColor = RadicalPalette.SapphireBlue
                )

                RadicalRadioCard(
                    title = "Global Archive",
                    subtitle = "2009–present · ~5400 wallpapers",
                    description = "Complete collection, full photography archive",
                    isSelected = selectedType == "full",
                    onClick = { onTypeChange("full") },
                    isDark = isDark,
                    accentColor = RadicalPalette.ElectricAzure
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RadicalPalette.SapphireBlue,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Select & Download", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "Cancel",
                    color = if (isDark) TextSecondaryDark else TextSecondaryLight,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}

@Composable
fun RadicalWatermarkBadge(
    isDark: Boolean,
    version: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(RadicalPalette.EmeraldJade)
            )
            Text(
                text = "VANDERWAALS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155)
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(RadicalPalette.SapphireBlue)
            )
        }

        Text(
            text = "100% On-Device AI · Private by Design · v$version",
            style = MaterialTheme.typography.labelSmall,
            color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
            letterSpacing = 0.5.sp
        )
    }
}
