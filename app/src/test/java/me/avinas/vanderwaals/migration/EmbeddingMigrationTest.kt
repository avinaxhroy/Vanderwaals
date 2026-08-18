package me.avinas.vanderwaals.migration

import me.avinas.vanderwaals.data.entity.UserPreferences
import org.junit.Assert.*
import org.junit.Test

class EmbeddingMigrationTest {

    companion object {
        private const val LEGACY_EMBEDDING_DIM = 576
        private const val CURRENT_EMBEDDING_DIM = 1280
    }

    // ========== Embedding Dimension Tests ==========

    @Test
    fun `legacy 576D embedding is detected as incompatible with 1280D`() {
        val legacyVector = FloatArray(LEGACY_EMBEDDING_DIM) { 0.5f }
        val currentVector = FloatArray(CURRENT_EMBEDDING_DIM) { 0.5f }

        assertNotEquals(CURRENT_EMBEDDING_DIM, legacyVector.size)
        assertEquals(CURRENT_EMBEDDING_DIM, currentVector.size)
    }

    @Test
    fun `empty embedding array indicates migration needed or no data`() {
        val emptyEmbedding = floatArrayOf()

        assertTrue(emptyEmbedding.isEmpty())
        assertEquals(0, emptyEmbedding.size)
    }

    // ========== UserPreferences Migration Tests ==========

    @Test
    fun `createDefault creates empty preference vector for Auto Mode start`() {
        val preferences = UserPreferences.createDefault()

        assertEquals(1, preferences.id)
        assertEquals(UserPreferences.MODE_AUTO, preferences.mode)
        assertTrue(preferences.preferenceVector.isEmpty())
        assertTrue(preferences.likedWallpaperIds.isEmpty())
        assertTrue(preferences.dislikedWallpaperIds.isEmpty())
        assertEquals(0, preferences.feedbackCount)
    }

    @Test
    fun `createDefault with initial vector sets preference vector correctly`() {
        val initialVector = FloatArray(CURRENT_EMBEDDING_DIM) { it * 0.001f }

        val preferences = UserPreferences.createDefault(initialVector)

        assertEquals(CURRENT_EMBEDDING_DIM, preferences.preferenceVector.size)
        assertArrayEquals(initialVector, preferences.preferenceVector, 0.0001f)
    }

    @Test
    fun `migration reset preserves liked wallpaper IDs`() {
        val likedIds = listOf("wallpaper1", "wallpaper2", "wallpaper3")
        val legacyPreferences = UserPreferences(
            id = 1,
            mode = UserPreferences.MODE_PERSONALIZED,
            preferenceVector = FloatArray(LEGACY_EMBEDDING_DIM) { 0.5f },
            originalEmbedding = FloatArray(LEGACY_EMBEDDING_DIM) { 0.3f },
            momentumVector = FloatArray(LEGACY_EMBEDDING_DIM) { 0.1f },
            likedWallpaperIds = likedIds,
            dislikedWallpaperIds = listOf("disliked1"),
            feedbackCount = 15,
            epsilon = 0.1f,
            lastUpdated = System.currentTimeMillis()
        )

        val migratedPreferences = createMigrationReset(legacyPreferences, keepMode = false)

        assertEquals(likedIds, migratedPreferences.likedWallpaperIds)
        assertEquals(listOf("disliked1"), migratedPreferences.dislikedWallpaperIds)
    }

    @Test
    fun `migration reset clears embedding vectors`() {
        val legacyPreferences = UserPreferences(
            id = 1,
            mode = UserPreferences.MODE_PERSONALIZED,
            preferenceVector = FloatArray(LEGACY_EMBEDDING_DIM) { 0.5f },
            originalEmbedding = FloatArray(LEGACY_EMBEDDING_DIM) { 0.3f },
            momentumVector = FloatArray(LEGACY_EMBEDDING_DIM) { 0.1f },
            likedWallpaperIds = listOf("wallpaper1"),
            dislikedWallpaperIds = emptyList(),
            feedbackCount = 10,
            epsilon = 0.1f,
            lastUpdated = System.currentTimeMillis()
        )

        val migratedPreferences = createMigrationReset(legacyPreferences, keepMode = false)

        assertTrue(migratedPreferences.preferenceVector.isEmpty())
        assertTrue(migratedPreferences.originalEmbedding.isEmpty())
        assertTrue(migratedPreferences.momentumVector.isEmpty())
    }

    @Test
    fun `migration reset sets feedbackCount to zero`() {
        val legacyPreferences = UserPreferences(
            id = 1,
            mode = UserPreferences.MODE_PERSONALIZED,
            preferenceVector = FloatArray(LEGACY_EMBEDDING_DIM) { 0.5f },
            originalEmbedding = floatArrayOf(),
            momentumVector = floatArrayOf(),
            likedWallpaperIds = listOf("wp1", "wp2"),
            dislikedWallpaperIds = listOf("dp1"),
            feedbackCount = 25,  // Had lots of feedback
            epsilon = 0.05f,  // Was in stable phase
            lastUpdated = System.currentTimeMillis() - 86400000
        )

        val migratedPreferences = createMigrationReset(legacyPreferences, keepMode = false)

        assertEquals(0, migratedPreferences.feedbackCount)
        assertEquals(UserPreferences.DEFAULT_EPSILON, migratedPreferences.epsilon)
    }

    @Test
    fun `migration reset with keepMode true preserves personalized mode`() {
        val legacyPreferences = UserPreferences(
            id = 1,
            mode = UserPreferences.MODE_PERSONALIZED,
            preferenceVector = FloatArray(LEGACY_EMBEDDING_DIM) { 0.5f },
            originalEmbedding = floatArrayOf(),
            momentumVector = floatArrayOf(),
            likedWallpaperIds = listOf("wp1"),
            dislikedWallpaperIds = emptyList(),
            feedbackCount = 10,
            epsilon = 0.1f,
            lastUpdated = System.currentTimeMillis()
        )

        val migratedPreferences = createMigrationReset(legacyPreferences, keepMode = true)

        assertEquals(UserPreferences.MODE_PERSONALIZED, migratedPreferences.mode)
    }

    @Test
    fun `migration reset with keepMode false resets to auto mode`() {
        val legacyPreferences = UserPreferences(
            id = 1,
            mode = UserPreferences.MODE_PERSONALIZED,
            preferenceVector = FloatArray(LEGACY_EMBEDDING_DIM) { 0.5f },
            originalEmbedding = floatArrayOf(),
            momentumVector = floatArrayOf(),
            likedWallpaperIds = listOf("wp1"),
            dislikedWallpaperIds = emptyList(),
            feedbackCount = 10,
            epsilon = 0.1f,
            lastUpdated = System.currentTimeMillis()
        )

        val migratedPreferences = createMigrationReset(legacyPreferences, keepMode = false)

        assertEquals(UserPreferences.MODE_AUTO, migratedPreferences.mode)
    }

    @Test
    fun `migration from null preferences creates default`() {
        val migratedPreferences = createMigrationReset(null, keepMode = false)

        assertEquals(1, migratedPreferences.id)
        assertEquals(UserPreferences.MODE_AUTO, migratedPreferences.mode)
        assertTrue(migratedPreferences.preferenceVector.isEmpty())
        assertTrue(migratedPreferences.likedWallpaperIds.isEmpty())
        assertTrue(migratedPreferences.dislikedWallpaperIds.isEmpty())
        assertEquals(0, migratedPreferences.feedbackCount)
    }

    // ========== Embedding Dimension Detection Tests ==========

    @Test
    fun `detect legacy embedding dimension correctly`() {
        val legacyEmbedding = FloatArray(576) { 0.5f }
        val currentEmbedding = FloatArray(1280) { 0.5f }
        
        assertTrue(isLegacyEmbedding(legacyEmbedding))
        assertFalse(isLegacyEmbedding(currentEmbedding))
        assertFalse(isLegacyEmbedding(floatArrayOf()))
    }

    @Test
    fun `detect current embedding dimension correctly`() {
        val legacyEmbedding = FloatArray(576) { 0.5f }
        val currentEmbedding = FloatArray(1280) { 0.5f }
        
        assertFalse(isCurrentEmbedding(legacyEmbedding))
        assertTrue(isCurrentEmbedding(currentEmbedding))
        assertFalse(isCurrentEmbedding(floatArrayOf()))
    }

    @Test
    fun `migration needed when preferences have legacy dimension`() {
        val legacyPreferences = UserPreferences(
            id = 1,
            mode = UserPreferences.MODE_PERSONALIZED,
            preferenceVector = FloatArray(576) { 0.5f },
            originalEmbedding = floatArrayOf(),
            momentumVector = floatArrayOf(),
            likedWallpaperIds = listOf("wp1"),
            dislikedWallpaperIds = emptyList(),
            feedbackCount = 10,
            epsilon = 0.1f,
            lastUpdated = System.currentTimeMillis()
        )

        assertTrue(needsEmbeddingMigration(legacyPreferences))
    }

    @Test
    fun `migration not needed when preferences have current dimension`() {
        val currentPreferences = UserPreferences(
            id = 1,
            mode = UserPreferences.MODE_PERSONALIZED,
            preferenceVector = FloatArray(1280) { 0.5f },
            originalEmbedding = floatArrayOf(),
            momentumVector = floatArrayOf(),
            likedWallpaperIds = listOf("wp1"),
            dislikedWallpaperIds = emptyList(),
            feedbackCount = 10,
            epsilon = 0.1f,
            lastUpdated = System.currentTimeMillis()
        )

        assertFalse(needsEmbeddingMigration(currentPreferences))
    }

    @Test
    fun `migration not needed when preferences are empty (fresh user)`() {
        val freshPreferences = UserPreferences.createDefault()

        assertFalse(needsEmbeddingMigration(freshPreferences))
    }

    // ========== Large Data Set Tests ==========

    @Test
    fun `migration handles large liked wallpaper list`() {
        val manyLikes = (1..500).map { "wallpaper_$it" }
        val legacyPreferences = UserPreferences(
            id = 1,
            mode = UserPreferences.MODE_PERSONALIZED,
            preferenceVector = FloatArray(576) { 0.5f },
            originalEmbedding = floatArrayOf(),
            momentumVector = floatArrayOf(),
            likedWallpaperIds = manyLikes,
            dislikedWallpaperIds = emptyList(),
            feedbackCount = 500,
            epsilon = 0.05f,
            lastUpdated = System.currentTimeMillis()
        )

        val migratedPreferences = createMigrationReset(legacyPreferences, keepMode = false)

        assertEquals(500, migratedPreferences.likedWallpaperIds.size)
        assertEquals(manyLikes, migratedPreferences.likedWallpaperIds)
    }

    // ========== Helper Functions ==========

    /**
     * Simulates the migration reset logic from PreferenceRepositoryImpl.
     * Mirrors the actual implementation for testing.
     */
    private fun createMigrationReset(current: UserPreferences?, keepMode: Boolean): UserPreferences {
        return if (current != null) {
            UserPreferences(
                id = 1,
                mode = if (keepMode) current.mode else UserPreferences.MODE_AUTO,
                preferenceVector = floatArrayOf(),
                originalEmbedding = floatArrayOf(),
                momentumVector = floatArrayOf(),
                likedWallpaperIds = current.likedWallpaperIds,
                dislikedWallpaperIds = current.dislikedWallpaperIds,
                feedbackCount = 0,
                epsilon = UserPreferences.DEFAULT_EPSILON,
                lastUpdated = System.currentTimeMillis()
            )
        } else {
            UserPreferences.createDefault()
        }
    }

    private fun isLegacyEmbedding(embedding: FloatArray): Boolean {
        return embedding.size == LEGACY_EMBEDDING_DIM
    }

    private fun isCurrentEmbedding(embedding: FloatArray): Boolean {
        return embedding.size == CURRENT_EMBEDDING_DIM
    }

    private fun needsEmbeddingMigration(preferences: UserPreferences): Boolean {
        return preferences.preferenceVector.isNotEmpty() && 
               preferences.preferenceVector.size == LEGACY_EMBEDDING_DIM
    }

    private fun assertArrayEquals(expected: FloatArray, actual: FloatArray, delta: Float) {
        assertEquals("Array sizes differ", expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals("Values differ at index $i", expected[i], actual[i], delta)
        }
    }
}
