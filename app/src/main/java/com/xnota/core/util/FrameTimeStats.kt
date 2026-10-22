package com.xnota.core.util

/**
 * Frame-time telemetry (the fork's debug HUD): a rolling histogram of the last [window] frames'
 * work-time — min/avg/p95/max — plus an input-to-render latency estimate, fed pairwise (pointer
 * arrival stamp, frame-present stamp) and read as a rolling mean.
 *
 * Pure accumulator: the GL renderer feeds it on its thread, the HUD reads a snapshot on the main
 * thread, and the arithmetic is exact and unit-testable. The p95 is the honest "how bad is it
 * when it's bad" number for a jank meter: the max of 120 frames is one freak, the p95 is a rate.
 */
class FrameTimeStats(
    /** How many recent frame times the histogram holds. */
    private val window: Int = DEFAULT_WINDOW,
) {
    private val framesMs = DoubleArray(window)
    private var count = 0
    private var head = 0

    // Latency pairs: (frame-present ns − input-arrival ns) for frames that carried fresh input.
    private val latencyMs = DoubleArray(window)
    private var latencyCount = 0
    private var latencyHead = 0

    /** Record one frame's work-time in ms. */
    fun addFrame(ms: Double) {
        if (ms.isNaN() || ms < 0.0 || !ms.isFinite()) return
        framesMs[head] = ms
        head = (head + 1) % window
        if (count < window) count++
    }

    /**
     * Record the latency of one frame that presented [ms] after the pointer sample it drew
     * arrived. Only called when the pair is measurable and fresh; a stale stamp is noise.
     */
    fun addLatency(ms: Double) {
        if (ms.isNaN() || ms < 0.0 || !ms.isFinite()) return
        latencyMs[latencyHead] = ms
        latencyHead = (latencyHead + 1) % window
        if (latencyCount < window) latencyCount++
    }

    /** The histogram over the window; all zeros before any frame arrives. */
    fun snapshot(): Snapshot {
        if (count == 0) return Snapshot(0, 0.0, 0.0, 0.0, 0.0, latencyCount, latencyAverage())
        // Copy-out then sort: the ring order is write state, the percentiles need order. The
        // window is 120 doubles — the copy is nothing next to the frame it measures.
        val live = DoubleArray(count)
        for (i in 0 until count) live[i] = framesMs[(head - 1 - i + window * 2) % window]
        live.sort()
        var sum = 0.0
        for (v in live) sum += v
        return Snapshot(
            count = count,
            minMs = live[0],
            avgMs = sum / count,
            p95Ms = percentileOf(live, 0.95),
            maxMs = live[count - 1],
            latencyCount = latencyCount,
            latencyAvgMs = latencyAverage(),
        )
    }

    /** Rolling mean of the recorded input-to-render latencies, or null before any pair. */
    fun latencyAverage(): Double? {
        if (latencyCount == 0) return null
        var sum = 0.0
        for (i in 0 until latencyCount) sum += latencyMs[i]
        return sum / latencyCount
    }

    /** Drop everything (an EGL context rebuild makes the old numbers lies). */
    fun reset() {
        count = 0
        head = 0
        latencyCount = 0
        latencyHead = 0
    }

    private fun percentileOf(sorted: DoubleArray, q: Double): Double {
        if (sorted.isEmpty()) return 0.0
        // Nearest-rank percentile: the smallest value whose rank covers q of the samples. On 120
        // frames, p95 is the 114th — the six worst frames sit above it.
        val rank = kotlin.math.ceil(q * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    data class Snapshot(
        val count: Int,
        val minMs: Double,
        val avgMs: Double,
        val p95Ms: Double,
        val maxMs: Double,
        val latencyCount: Int,
        val latencyAvgMs: Double?,
    )

    companion object {
        /** Two seconds at 60 Hz: long enough for a rate, short enough to catch a regression now. */
        const val DEFAULT_WINDOW = 120
    }
}
