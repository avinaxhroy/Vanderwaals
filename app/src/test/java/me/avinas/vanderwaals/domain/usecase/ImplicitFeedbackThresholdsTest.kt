package me.avinas.vanderwaals.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the interval-relative implicit-feedback thresholds — the fix
 * for fast-rotation users never earning implicit likes while quick
 * "Change Now" browsing injected dislikes.
 */
class ImplicitFeedbackThresholdsTest {

    @Test
    fun `hourly rotation can earn an implicit like`() {
        // 130 minutes kept on an hourly rotation ≥ 2× expected (120 min) → LIKE.
        val expected = ProcessImplicitFeedbackUseCase.expectedIntervalMs("hourly")
        val kept130Min = 130 * 60_000L
        assertTrue(kept130Min >= expected * 2)
    }

    @Test
    fun `quick manual browse on hourly is a dislike`() {
        val expected = ProcessImplicitFeedbackUseCase.expectedIntervalMs("hourly")
        val removedAfter3Min = 3 * 60_000L
        assertTrue(removedAfter3Min < expected * 0.25)
    }

    @Test
    fun `neutral band exists for normal rotation cadence`() {
        val expected = ProcessImplicitFeedbackUseCase.expectedIntervalMs("hourly")
        val removedAfter50Min = 50 * 60_000L
        assertTrue(removedAfter50Min >= expected * 0.25 && removedAfter50Min < expected * 2)
    }

    @Test
    fun `all intervals map to sane durations`() {
        val expectedIntervals = mapOf(
            "15min" to 15f,
            "unlock" to 45f,
            "hourly" to 60f,
            "3hours" to 180f,
            "6hours" to 360f,
            "12hours" to 720f,
            "daily" to 1440f,
            "3days" to 4320f,
            "7days" to 10080f,
            "unknown" to 1440f
        )
        expectedIntervals.forEach { (interval, minutes) ->
            assertEquals("$interval minutes", minutes * 60_000f, ProcessImplicitFeedbackUseCase.expectedIntervalMs(interval).toFloat(), 0.1f)
        }
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
