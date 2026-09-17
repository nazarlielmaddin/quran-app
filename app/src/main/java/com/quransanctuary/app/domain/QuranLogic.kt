package com.quransanctuary.app.domain

import kotlin.math.max
import kotlin.math.min

/** Mirrors the website's `activeVerseIndex` (binary search over ayah-start offsets). Never throws. */
fun activeVerseIndex(timingsMs: List<Long>?, positionMs: Long): Int {
    try {
        if (timingsMs.isNullOrEmpty()) return 0
        val pos = positionMs.coerceAtLeast(0L)
        // Filter corrupt offsets defensively (negative values must not break binary search).
        // Most lists are clean, so avoid allocation in the common case.
        val hasBad = timingsMs.any { it < 0 }
        val t = timingsMs.let { if (hasBad) it.filter { v -> v >= 0 }.ifEmpty { return 0 } else it }
        val cur = pos.coerceAtLeast(0L)
        if (cur < t[0]) return 0
        if (cur >= t.last()) return t.size - 1
        var lo = 0
        var hi = t.size - 1
        var guard = 0
        while (lo < hi && guard++ < 64) {
            val mid = (lo + hi + 1) ushr 1
            // Extra bounds guard — corrupted lists must return, never throw.
            if (mid < 0 || mid >= t.size) break
            if (t[mid] <= cur) lo = mid else hi = mid - 1
        }
        return lo.coerceIn(0, t.size - 1)
    } catch (_: Exception) {
        return 0
    }
}

/** Mirrors the website's `formatTime` (m:ss, h:mm:ss for long recitations). */
fun formatTime(totalSeconds: Long): String {
    if (totalSeconds < 0) return "0:00"
    val s = (totalSeconds % 60).toInt()
    val m = ((totalSeconds / 60) % 60).toInt()
    val h = (totalSeconds / 3600).toInt()
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}

fun formatTimeMs(positionMs: Long): String = formatTime(max(0, positionMs / 1000))

fun clamp(value: Float, min: Float, max: Float): Float = max(min, min(max, value))
