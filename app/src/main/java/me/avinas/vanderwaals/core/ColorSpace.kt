package me.avinas.vanderwaals.core

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Shared sRGB ↔ CIELab colour-space conversions and CIE76 ΔE distance.
 *
 * All algorithm components that need perceptual colour comparison should route
 * through this object so there is a single, correct implementation.
 *
 * Chain: sRGB [0–255] → linear light (IEC 61966-2-1) → CIEXYZ (D65) → CIELab.
 */
object ColorSpace {

    /** Converts a single sRGB channel [0–255] to linear light. */
    private fun lineariseChannel(channel: Int): Double {
        val v = channel / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    /**
     * Converts sRGB [0–255] to CIELab (D65 illuminant).
     * @return Triple(L, a, b) where L ∈ [0,100], a/b ∈ [−128,127]
     */
    fun rgbToLab(r: Int, g: Int, b: Int): Triple<Double, Double, Double> {
        val lr = lineariseChannel(r)
        val lg = lineariseChannel(g)
        val lb = lineariseChannel(b)
        // Linear RGB → CIEXYZ (D65 reference matrix)
        val x = (lr * 0.4124564 + lg * 0.3575761 + lb * 0.1804375) / 0.95047
        val y =  lr * 0.2126729 + lg * 0.7151522 + lb * 0.0721750   // D65 Yn = 1.0
        val z = (lr * 0.0193339 + lg * 0.1191920 + lb * 0.9503041) / 1.08883
        // CIEXYZ → CIELab
        fun f(t: Double) = if (t > 0.008856) t.pow(1.0 / 3.0) else 7.787 * t + 16.0 / 116.0
        return Triple(116.0 * f(y) - 16.0, 500.0 * (f(x) - f(y)), 200.0 * (f(y) - f(z)))
    }

    /**
     * CIE76 ΔE between two CIELab triples.
     * Max ΔE ≈ 100 (pure black ↔ pure white).
     */
    fun labDeltaE(lab1: Triple<Double, Double, Double>, lab2: Triple<Double, Double, Double>): Double {
        val dL = lab1.first - lab2.first
        val da = lab1.second - lab2.second
        val db = lab1.third - lab2.third
        return sqrt(dL * dL + da * da + db * db)
    }

    /**
     * Convenience: CIE76 ΔE directly from two sRGB colours.
     */
    fun rgbDeltaE(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Double {
        return labDeltaE(rgbToLab(r1, g1, b1), rgbToLab(r2, g2, b2))
    }
}
