package com.quransanctuary.app.data.audio

/**
 * App-private audio paths.
 *
 * The Quran directory is versioned because the bundled timings belong to a
 * specific audio edit. Reusing the old `audio/` tree would pair a QDC download
 * with QUL timings and highlight the wrong ayah.
 */
object AudioStorage {
    const val QURAN_DIR = "audio-qul-v1"
    const val LEGACY_DIR = "audio"
    const val AMBIENT_DIR = "audio/ambient"
}
