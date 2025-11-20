package me.avinas.vanderwaals.data.repository

import me.avinas.vanderwaals.data.dao.DownloadQueueDao
import me.avinas.vanderwaals.data.dao.UserPreferenceDao
import me.avinas.vanderwaals.data.dao.WallpaperHistoryDao

/**
 * Implementation of FeedbackRepository for feedback history and category preference tracking.
 */
class FeedbackRepositoryImpl(
    private val userPreferenceDao: UserPreferenceDao,
    private val wallpaperHistoryDao: WallpaperHistoryDao,
    private val downloadQueueDao: DownloadQueueDao
) : FeedbackRepository
