package me.avinas.vanderwaals.algorithm

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Unit tests for [PreferenceUpdater].
 *
 * Tests the real production class (not a re-implementation):
 * - EMA with momentum (`updateWithPositiveFeedback` / `updateWithNegativeFeedback`)
 * - Velocity clipping (`MAX_VELOCITY_MAGNITUDE`)
 * - Confidence-weighted learning rate (`updateWithConfidenceWeighting`)
 * - Preference decay toward uniform (`applyPreferenceDecay`)
 * - Temporal decay with half-life (`applyTemporalDecay`)
 * - Zero-vector normalisation → uniform fallback (cold-start safety)
 * - Size-mismatch guard
 * - 1280-dimension performance
 */
class PreferenceUpdaterTest {

    private val updater = PreferenceUpdater()

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun magnitude(v: FloatArray): Float =
        sqrt(v.sumOf { (it * it).toDouble() }).toFloat()

    private fun isNormalized(v: FloatArray, tolerance: Float = 0.01f): Boolean =
        abs(magnitude(v) - 1.0f) < tolerance

    private fun cosine(v1: FloatArray, v2: FloatArray): Float {
        var dot = 0.0f
        var n1 = 0.0f
        var n2 = 0.0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            n1 += v1[i] * v1[i]
            n2 += v2[i] * v2[i]
        }
        val d = sqrt(n1 * n2)
        return if (d > 0f) dot / d else 0f
    }

    private fun uniformVector(n: Int): FloatArray =
        FloatArray(n) { 1f / sqrt(n.toFloat()) }

    // ── Positive feedback (likes) ─────────────────────────────────────────────

    @Test
    fun likePullsTowardTarget() {
        val current = floatArrayOf(1f, 0f, 0f)
        val target = floatArrayOf(0f, 1f, 0f)
        val (updated, _) = updater.updateWithPositiveFeedback(current, target, 0.5f)

        // new = current + 0.5*(target-current) = [0.5, 0.5, 0] → normalized [0.707, 0.707, 0]
        assertEquals(0.707f, updated[0], 0.01f)
        assertEquals(0.707f, updated[1], 0.01f)
        assertTrue(isNormalized(updated))
    }

    @Test
    fun likeProgressiveMovement() {
        var current = floatArrayOf(1f, 0f, 0f)
        val target = floatArrayOf(0f, 1f, 0f)
        repeat(5) {
            val (updated, _) = updater.updateWithPositiveFeedback(current, target, 0.2f)
            current = updated
        }
        assertTrue("Should move toward target", current[1] > 0.5f)
        assertTrue(isNormalized(current))
    }

    @Test
    fun likeZeroLearningRateKeepsVector() {
        val current = floatArrayOf(1f, 0f, 0f)
        val target = floatArrayOf(0f, 1f, 0f)
        val (updated, _) = updater.updateWithPositiveFeedback(current, target, 0f)
        // With lr=0, velocity=0, updated=current → normalised current
        assertArrayEquals(current, updated, 0.001f)
    }

    @Test
    fun likeFullLearningRateMatchesTarget() {
        val current = floatArrayOf(1f, 0f, 0f)
        val target = floatArrayOf(0f, 1f, 0f)
        val (updated, _) = updater.updateWithPositiveFeedback(current, target, 1f)
        // With lr=1: velocity = 0.3*0 + 1*(target-current) = [-1,1,0]
        // updated = current + velocity = [0, 1, 0] → normalized = [0, 1, 0]
        assertArrayEquals(target, updated, 0.001f)
    }

    // ── Negative feedback (dislikes) ──────────────────────────────────────────

    @Test
    fun dislikePushesAwayFromTarget() {
        val current = floatArrayOf(0.707f, 0.707f, 0f)
        val target = floatArrayOf(1f, 0f, 0f)
        val (updated, _) = updater.updateWithNegativeFeedback(current, target, 0.5f)

        assertTrue("X component should decrease", updated[0] < current[0])
        assertTrue(isNormalized(updated))
    }

    @Test
    fun dislikeProgressiveDistancing() {
        var current = floatArrayOf(0.6f, 0.6f, 0.53f)
        current = FloatArray(3) { i -> current[i] / magnitude(current) }
        val target = floatArrayOf(1f, 0f, 0f)
        val initialSim = cosine(current, target)

        repeat(5) {
            val (updated, _) = updater.updateWithNegativeFeedback(current, target, 0.15f)
            current = updated
        }
        assertTrue("Similarity should decrease", cosine(current, target) < initialSim)
    }

    // ── Momentum ──────────────────────────────────────────────────────────────

    @Test
    fun momentumAccumulatesVelocityAcrossUpdates() {
        val current = floatArrayOf(1f, 0f, 0f)
        val target = floatArrayOf(0f, 1f, 0f)
        val lr = 0.3f

        // First update: no prior momentum
        val (v1, velocity1) = updater.updateWithPositiveFeedback(current, target, lr)
        // Second update: carry velocity forward
        val (v2, velocity2) = updater.updateWithPositiveFeedback(v1, target, lr, velocity1)

        // Velocity magnitude should be larger with momentum than without
        val magWithMomentum = magnitude(velocity2)
        val (_, velocityNoMomentum) = updater.updateWithPositiveFeedback(v1, target, lr)
        val magWithoutMomentum = magnitude(velocityNoMomentum)

        assertTrue(
            "Momentum should accumulate velocity ($magWithMomentum > $magWithoutMomentum)",
            magWithMomentum > magWithoutMomentum
        )
    }

    @Test
    fun velocityClippingLimitsMagnitude() {
        val size = 10
        val current = FloatArray(size) { 0f }
        val target = FloatArray(size) { 1000f } // Extreme target → huge velocity
        val (_, velocity) = updater.updateWithPositiveFeedback(current, target, 1f)

        val mag = magnitude(velocity)
        assertTrue(
            "Velocity magnitude $mag should be <= ${0.5f + 0.01f}",
            mag <= 0.5f + 0.01f
        )
    }

    // ── Confidence weighting ──────────────────────────────────────────────────

    @Test
    fun confidenceWeightingReducesEffectiveRate() {
        val current = floatArrayOf(1f, 0f, 0f)
        val target = floatArrayOf(0f, 1f, 0f)

        val (noConfidence, _) =
            updater.updateWithConfidenceWeighting(current, target, 0.5f, 0f, isPositive = true)
        val (fullConfidence, _) =
            updater.updateWithConfidenceWeighting(current, target, 0.5f, 1f, isPositive = true)

        // High confidence → lower effective rate → less movement toward target
        assertTrue(
            "Full confidence should move less than zero confidence",
            fullConfidence[1] < noConfidence[1]
        )
    }

    @Test
    fun confidenceWeightingAtOneUsesFortyPercent() {
        val current = floatArrayOf(1f, 0f, 0f)
        val target = floatArrayOf(0f, 1f, 0f)

        val (confident, _) =
            updater.updateWithConfidenceWeighting(current, target, 0.5f, 1f, isPositive = true)
        val (direct, _) =
            updater.updateWithPositiveFeedback(current, target, 0.5f * 0.4f)

        assertArrayEquals(direct, confident, 0.001f)
    }

    // ── Decay ─────────────────────────────────────────────────────────────────

    @Test
    fun preferenceDecayBlendsTowardUniform() {
        val current = floatArrayOf(1f, 0f, 0f)
        val decayed = updater.applyPreferenceDecay(current, 0.5f)
        val uniform = uniformVector(3)

        // 50% blend toward uniform then normalised. The decayed vector should
        // be between the original and uniform (closer to original since blend
        // is only 0.5), and component [0] should be the largest.
        assertTrue("Decayed[0] should be > uniform[0]", decayed[0] > uniform[0])
        assertTrue("Decayed[1] should be > 0", decayed[1] > 0.01f)
        assertTrue("Decayed[0] should be < original 1.0", decayed[0] < 0.99f)
        assertTrue(isNormalized(decayed))
    }

    @Test
    fun preferenceDecayFullResetsToUniform() {
        val current = floatArrayOf(1f, 0f, 0f)
        val decayed = updater.applyPreferenceDecay(current, 1f)
        val uniform = uniformVector(3)

        assertArrayEquals(uniform, decayed, 0.01f)
    }

    @Test
    fun preferenceDecayZeroIsNoOp() {
        val current = floatArrayOf(1f, 0f, 0f)
        val decayed = updater.applyPreferenceDecay(current, 0f)
        assertArrayEquals(current, decayed, 0.0001f)
    }

    @Test
    fun temporalDecayAtZeroDaysIsNoOp() {
        val current = floatArrayOf(1f, 0f, 0f)
        val decayed = updater.applyTemporalDecay(current, 0.0)
        assertArrayEquals(current, decayed, 0.0001f)
    }

    @Test
    fun temporalDecayAtHalfLifeIsFiftyPercentBlend() {
        val current = floatArrayOf(1f, 0f, 0f)
        val uniform = uniformVector(3)
        val decayed = updater.applyTemporalDecay(current, 30.0, 30.0)

        // At half-life: decayFactor=0.5, blend=0.5 toward uniform, then normalised.
        // decayed[0] should be between uniform[0] and 1.0, and decayed[1] > 0.
        assertTrue("Decayed[0] should be > uniform[0]", decayed[0] > uniform[0])
        assertTrue("Decayed[0] should be < 1.0", decayed[0] < 0.99f)
        assertTrue("Decayed[1] should be > 0", decayed[1] > 0.01f)
        assertTrue(isNormalized(decayed))
    }

    @Test
    fun temporalDecayFarFutureApproachesUniform() {
        val current = floatArrayOf(1f, 0f, 0f)
        val decayed = updater.applyTemporalDecay(current, 365.0, 30.0)
        val uniform = uniformVector(3)

        // After ~12 half-lives, should be very close to uniform
        assertArrayEquals(uniform, decayed, 0.01f)
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun sizeMismatchReturnsCurrentVector() {
        val current = floatArrayOf(1f, 0f, 0f)
        val target = floatArrayOf(0f, 1f) // Wrong size
        val (updated, velocity) = updater.updateWithPositiveFeedback(current, target, 0.5f)

        assertArrayEquals(current, updated, 0.0001f)
        assertArrayEquals(FloatArray(3), velocity, 0.0001f)
    }

    @Test
    fun zeroVectorNormalizationReturnsUniform() {
        // The production normalizeVector returns a uniform vector on zero
        // magnitude (M3 fix). We can verify this indirectly: start from a
        // zero preference and a non-zero target with lr that makes the
        // updated vector zero, or more directly via decay on a zero vector.
        val zero = FloatArray(3) { 0f }
        val decayed = updater.applyPreferenceDecay(zero, 1f)
        val uniform = uniformVector(3)

        // decayed = (1-1)*zero + 1*uniform = uniform
        assertArrayEquals(uniform, decayed, 0.01f)
        assertTrue(isNormalized(decayed))
    }

    @Test
    fun largeDimensionalityNormalization() {
        val vec = FloatArray(1280) { i -> (i % 10).toFloat() / 10f }
        val target = FloatArray(1280) { i -> ((i + 5) % 10).toFloat() / 10f }
        val (updated, _) = updater.updateWithPositiveFeedback(vec, target, 0.1f)

        assertEquals(1280, updated.size)
        assertTrue(isNormalized(updated))
    }

    @Test
    fun performance1280Dimensions1000Iterations() {
        var current = FloatArray(1280) { it.toFloat() / 1280f }
        current = FloatArray(1280) { i -> current[i] / magnitude(current) }
        val target = FloatArray(1280) { (it + 100).toFloat() / 1280f }
        var velocity: FloatArray? = null

        val start = System.nanoTime()
        repeat(1000) {
            val (updated, vel) = updater.updateWithPositiveFeedback(current, target, 0.1f, velocity)
            current = updated
            velocity = vel
        }
        val durationMs = (System.nanoTime() - start) / 1_000_000

        assertTrue("1000 iterations should complete in < 500ms, took ${durationMs}ms", durationMs < 500)
        assertTrue(isNormalized(current))
    }
}
