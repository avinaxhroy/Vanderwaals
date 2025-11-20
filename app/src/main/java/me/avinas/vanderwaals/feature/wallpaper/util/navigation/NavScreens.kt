package me.avinas.vanderwaals.feature.wallpaper.util.navigation

import kotlinx.serialization.Serializable

/**
 * Vanderwaals Navigation Screens
 * Minimal navigation for algorithm-driven wallpaper experience
 */

/**
 * Onboarding flow - First launch experience
 * Includes mode selection, personalization, and initial setup
 */
@Serializable
object Onboarding

/**
 * Main screen - Current wallpaper display
 * Full-screen wallpaper preview with Change Now button and minimal overlay
 */
@Serializable
object Main

/**
 * History screen - Wallpaper history with learning feedback
 * Chronological list with Like/Dislike buttons for preference learning
 */
@Serializable
object History

/**
 * Settings screen - App configuration
 * Mode, auto-change frequency, sources, storage management
 */
@Serializable
object Settings

/**
 * Notification permission screen
 */
@Serializable
object Notification

/**
 * Privacy policy screen
 */
@Serializable
object Privacy

/**
 * Analytics screen - Personalization insights and statistics
 */
@Serializable
object Analytics

