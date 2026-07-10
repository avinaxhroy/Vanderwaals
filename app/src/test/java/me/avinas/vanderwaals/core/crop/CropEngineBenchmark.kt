package me.avinas.vanderwaals.core.crop

import org.junit.Test

/**
 * Microbenchmark for the pure [CropEngine] pipeline (no Android, no Bitmap).
 *
 * Times the full decision path the adapter delegates to: analyze → saliencyMap
 * → focalPoints → optimalCrop. Uses the working resolution the adapter feeds
 * (256px long side, matching SaliencyDetector.WORKING_MAX) plus a larger size
 * to show scaling. Plain JUnit4 warm-up + measured median; good enough to
 * characterise cost without pulling in JMH.
 */
class CropEngineBenchmark {

    private fun px(r: Int, g: Int, b: Int): Int = (0xff shl 24) or (r shl 16) or (g shl 8) or b

    private fun synthetic(w: Int, h: Int): IntArray {
        val img = IntArray(w * h)
        // A few bright blobs on a textured background — realistic workload.
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val base = ((x xor y) and 0x3f) + 40
            img[i] = px(base, base, base)
        }
        for (y in h / 3 until h * 2 / 3) for (x in w / 3 until w * 2 / 3) {
            img[y * w + x] = px(220, 180, 120)
        }
        img[(h / 5) * w + w - w / 5] = px(240, 240, 240)
        return img
    }

    private fun bench(label: String, w: Int, h: Int, runs: Int = 20, warmup: Int = 5) {
        val img = synthetic(w, h)
        val targetW = (w * 0.5f).toInt().coerceAtLeast(1)
        val targetH = h
        val timings = LongArray(runs)

        // Pipeline closure (allocates the working arrays each run, as the adapter would).
        fun run(): CropEngine.CropRect {
            val stats = CropEngine.analyze(img, w, h)
            val sal = CropEngine.saliencyMap(img, w, h, stats)
            val focals = CropEngine.focalPoints(sal, w, h, stats)
            return CropEngine.optimalCrop(w, h, targetW, targetH, focals)
        }

        repeat(warmup) { run() }
        for (r in 0 until runs) {
            val t0 = System.nanoTime()
            run()
            timings[r] = System.nanoTime() - t0
        }
        timings.sort()
        val medianNs = timings[runs / 2]
        val meanNs = timings.average().toLong()
        val minNs = timings.first()
        val maxNs = timings.last()
        System.out.printf(
            "%-28s %5dx%-5d  median=%6.2fms  mean=%6.2fms  min=%6.2fms  max=%6.2fms%n",
            label, w, h,
            medianNs / 1e6, meanNs / 1e6, minNs / 1e6, maxNs / 1e6
        )
    }

    @Test
    fun benchmarkFullPipeline() {
        bench("working (256 long side)", 256, 256)
        bench("working portrait", 144, 256)
        bench("working landscape", 256, 144)
        bench("2x working (512)", 512, 512)
        bench("4x working (1024)", 1024, 1024)
    }

    @Test
    fun benchmarkSaliencyMapOnly() {
        val w = 256; val h = 256
        val img = synthetic(w, h)
        repeat(5) { CropEngine.saliencyMap(img, w, h) }
        val t = LongArray(20) {
            val t0 = System.nanoTime()
            CropEngine.saliencyMap(img, w, h)
            System.nanoTime() - t0
        }.also { it.sort() }
        System.out.printf("saliencyMap 256x256  median=%.2fms%n", t[10] / 1e6)
    }
}
