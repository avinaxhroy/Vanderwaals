package me.avinas.vanderwaals.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import me.avinas.vanderwaals.data.dao.ColorPreferenceDao
import me.avinas.vanderwaals.data.entity.ColorPreference
import javax.inject.Inject

/**
 * Color preferences are fallback personalization when categories are missing,
 * using RGB Euclidean distance and weighted at 10% vs category boost at 15%.
 */
class ColorPreferenceRepositoryImpl @Inject constructor(
    private val colorPreferenceDao: ColorPreferenceDao
) : ColorPreferenceRepository {
    
    override fun getAllColorPreferences(): Flow<List<ColorPreference>> {
        return colorPreferenceDao.getAllFlow()
    }
    
    override fun getColorPreference(colorHex: String): Flow<ColorPreference?> {
        return colorPreferenceDao.getByColorFlow(colorHex)
    }
    
    override suspend fun getColorsByPreference(): List<String> {
        return colorPreferenceDao.getAllFlow()
            .firstOrNull()
            ?.sortedByDescending { it.calculateScore() }
            ?.map { it.colorHex }
            ?: emptyList()
    }
    
    override suspend fun getLikedColors(): List<String> {
        return colorPreferenceDao.getLikedColors().map { it.colorHex }
    }
    
    override suspend fun getDislikedColors(): List<String> {
        return colorPreferenceDao.getDislikedColors().map { it.colorHex }
    }
    
    override suspend fun getUnderutilizedColors(minTimeSinceShown: Long): List<String> {
        val now = System.currentTimeMillis()
        val cutoffTime = now - minTimeSinceShown
        
        return colorPreferenceDao.getAllFlow()
            .firstOrNull()
            ?.filter { it.lastShown < cutoffTime }
            ?.map { it.colorHex }
            ?: emptyList()
    }
    
    override suspend fun recordView(colorHex: String) {
        colorPreferenceDao.incrementViews(colorHex)
    }
    
    override suspend fun recordViews(colors: List<String>) {
        colors.forEach { colorHex ->
            colorPreferenceDao.incrementViews(colorHex)
        }
    }
    
    override suspend fun recordLike(colorHex: String) {
        colorPreferenceDao.incrementLikes(colorHex)
    }
    
    override suspend fun recordLikes(colors: List<String>) {
        colors.forEach { colorHex ->
            colorPreferenceDao.incrementLikes(colorHex)
        }
    }
    
    override suspend fun recordDislike(colorHex: String) {
        colorPreferenceDao.incrementDislikes(colorHex)
    }
    
    override suspend fun recordDislikes(colors: List<String>) {
        colors.forEach { colorHex ->
            colorPreferenceDao.incrementDislikes(colorHex)
        }
    }
    
    override suspend fun getColorScore(colorHex: String): Double {
        val pref = colorPreferenceDao.getByColor(colorHex)
        return pref?.calculateScore()?.toDouble() ?: 0.0
    }
    
    override suspend fun getAverageColorScore(colors: List<String>): Double {
        if (colors.isEmpty()) return 0.0
        
        val scores = colors.map { getColorScore(it) }
        return scores.average()
    }
    
    override suspend fun resetAllColorPreferences() {
        colorPreferenceDao.deleteAll()
    }
    
    override suspend fun getColorCount(): Int {
        return colorPreferenceDao.getCount()
    }
}
