package me.avinas.vanderwaals.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import me.avinas.vanderwaals.data.dao.CategoryPreferenceDao
import me.avinas.vanderwaals.data.entity.CategoryPreference
import javax.inject.Inject

/**
 * Implementation of CategoryPreferenceRepository for category-level tracking.
 * 
 * Manages category preferences using Room database with reactive Flow updates.
 * All database operations run on IO dispatcher via Room's suspend functions.
 */
class CategoryPreferenceRepositoryImpl @Inject constructor(
    private val categoryPreferenceDao: CategoryPreferenceDao
) : CategoryPreferenceRepository {
    
    override fun getAllCategoryPreferences(): Flow<List<CategoryPreference>> {
        return categoryPreferenceDao.getAllFlow()
    }
    
    override fun getCategoryPreference(category: String): Flow<CategoryPreference?> {
        return categoryPreferenceDao.getByCategoryFlow(category)
    }
    
    override suspend fun getByCategory(category: String): CategoryPreference? {
        return categoryPreferenceDao.getByCategory(category)
    }
    
    override suspend fun getCategoriesByPreference(): List<String> {
        return categoryPreferenceDao.getAllFlow()
            .firstOrNull()
            ?.sortedByDescending { it.calculateScore() }
            ?.map { it.category }
            ?: emptyList()
    }
    
    override suspend fun getUnderutilizedCategories(minTimeSinceShown: Long): List<String> {
        val now = System.currentTimeMillis()
        val cutoffTime = now - minTimeSinceShown
        
        return categoryPreferenceDao.getAllFlow()
            .firstOrNull()
            ?.filter { it.lastShown < cutoffTime }
            ?.map { it.category }
            ?: emptyList()
    }
    
    override suspend fun recordView(category: String) {
        val existing = categoryPreferenceDao.getByCategory(category)
        val updated = if (existing != null) {
            existing.copy(
                views = existing.views + 1,
                lastShown = System.currentTimeMillis()
            )
        } else {
            CategoryPreference(
                category = category,
                views = 1,
                lastShown = System.currentTimeMillis()
            )
        }
        categoryPreferenceDao.insert(updated)
    }
    
    override suspend fun recordLike(category: String) {
        val existing = categoryPreferenceDao.getByCategory(category)
        val updated = if (existing != null) {
            existing.copy(
                likes = existing.likes + 1,
                views = existing.views + 1,
                lastShown = System.currentTimeMillis()
            )
        } else {
            CategoryPreference(
                category = category,
                likes = 1,
                views = 1,
                lastShown = System.currentTimeMillis()
            )
        }
        categoryPreferenceDao.insert(updated)
    }
    
    override suspend fun recordDislike(category: String) {
        val existing = categoryPreferenceDao.getByCategory(category)
        val updated = if (existing != null) {
            existing.copy(
                dislikes = existing.dislikes + 1,
                views = existing.views + 1,
                lastShown = System.currentTimeMillis()
            )
        } else {
            CategoryPreference(
                category = category,
                dislikes = 1,
                views = 1,
                lastShown = System.currentTimeMillis()
            )
        }
        categoryPreferenceDao.insert(updated)
    }
    
    override suspend fun getCategoryScore(category: String): Double {
        val pref = categoryPreferenceDao.getByCategory(category)
        return pref?.calculateScore()?.toDouble() ?: 0.0
    }
    
    override suspend fun clearAllPreferences() {
        categoryPreferenceDao.deleteAll()
    }
}
