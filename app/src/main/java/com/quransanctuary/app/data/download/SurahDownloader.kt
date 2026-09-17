package com.quransanctuary.app.data.download

import android.content.Context
import com.quransanctuary.app.data.local.DownloadEntity
import com.quransanctuary.app.data.local.LibraryDatabase
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DownloadStatus(val key: String, val progress: Int, val downloading: Boolean)

/**
 * Offline surah/ambient downloads — fully in-app (plain HTTPS stream into the
 * app-private files dir), so offline listening just works.
 *
 * Why not the system DownloadManager: it runs in another process and is
 * FORBIDDEN from writing to app-private internal storage —
 * `setDestinationUri(file:///.../files/...)` throws
 * `SecurityException: Unsupported path` on modern Android (and misbehaves on
 * several OEM skins). An in-app stream has no cross-process file grants, no
 * extra permissions and identical UX (progress + Room ledger + same API, so
 * all call sites stay untouched).
 */
class SurahDownloader(
    private val context: Context,
    private val db: LibraryDatabase,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val _active = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val active: Flow<Map<String, DownloadStatus>> = _active.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()
    private val jobsLock = Any()

    fun audioKey(reciterId: String, surahId: Int) = "$reciterId:$surahId"
    fun ambientKey(id: String) = "ambient:$id"

    fun isDownloaded(key: String, reciterId: String, surahId: Int): Boolean =
        targetFile(key, reciterId, surahId).let { it.exists() && it.length() > 0 }

    fun targetFile(key: String, reciterId: String, surahId: Int): File =
        if (key.startsWith("ambient:")) File(context.filesDir, "audio/ambient/${key.removePrefix("ambient:")}.mp3")
        else File(context.filesDir, "audio/$reciterId/$surahId.mp3")

    fun enqueue(key: String, url: String, label: String, reciterId: String = "", surahId: Int = 0) {
        synchronized(jobsLock) {
            // Already queued/running — never stack duplicate jobs.
            if (jobs.containsKey(key)) return
            if (_active.value.containsKey(key)) return
        }
        val file = targetFile(key, reciterId, surahId)
        if (file.exists() && file.length() > 0) return
        _active.value = _active.value + (key to DownloadStatus(key, 0, true))
        val job = scope.launch {
            try {
                downloadToFile(url, file) { pct ->
                    _active.value = _active.value + (key to DownloadStatus(key, pct, true))
                }
                db.downloads().upsert(
                    DownloadEntity(key, reciterId, surahId, label, file.length(), "done")
                )
            } catch (_: Exception) {
                // Partial/corrupt output must never pose as a download.
                runCatching { file.delete() }
                runCatching { partFile(file).delete() }
            } finally {
                synchronized(jobsLock) { jobs.remove(key) }
                _active.value = _active.value - key
            }
        }
        synchronized(jobsLock) { jobs[key] = job }
    }

    private fun partFile(target: File): File = File(target.parent, "${target.name}.part")

    @Throws(Exception::class)
    private suspend fun downloadToFile(url: String, target: File, onProgress: (Int) -> Unit) =
        withContext(Dispatchers.IO) {
            target.parentFile?.mkdirs()
            val part = partFile(target)
            runCatching { part.delete() }
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "QuranSanctuary/1.0")
                    setRequestProperty("Accept", "audio/mpeg,audio/*,*/*")
                }
                connection.connect()
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("HTTP $code")
                val total = connection.contentLengthLong.takeIf { it > 0 }
                BufferedInputStream(connection.inputStream, 32 * 1024).use { input ->
                    FileOutputStream(part).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var downloaded = 0L
                        var lastPct = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total != null && total > 0) {
                                val pct = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                                if (pct != lastPct) {
                                    lastPct = pct
                                    withContext(Dispatchers.Main.immediate) {
                                        runCatching { onProgress(pct) }
                                    }
                                }
                            }
                        }
                        output.fd.sync()
                    }
                }
                val bytes = part.length()
                if (bytes <= 0) throw IllegalStateException("empty body")
                runCatching { target.delete() }
                if (!part.renameTo(target)) {
                    // renameTo can fail on odd filesystems — copy as fallback.
                    part.copyTo(target, overwrite = true)
                    runCatching { part.delete() }
                }
                if (!target.exists() || target.length() <= 0) throw IllegalStateException("finalize failed")
            } finally {
                runCatching { connection?.disconnect() }
            }
        }

    suspend fun delete(key: String, reciterId: String = "", surahId: Int = 0) {
        synchronized(jobsLock) { jobs.remove(key) }?.cancel()
        _active.value = _active.value - key
        runCatching { targetFile(key, reciterId, surahId).delete() }
        db.downloads().delete(key)
    }

    fun downloadsDirSize(): Long {
        return try {
            val root = File(context.filesDir, "audio")
            if (!root.exists()) return 0
            root.walkTopDown().filter { it.isFile && it.extension != "part" }.sumOf { it.length() }
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun clearAll() {
        synchronized(jobsLock) {
            val all = jobs.values.toList()
            jobs.clear()
            all.forEach { runCatching { it.cancel() } }
        }
        _active.value = emptyMap()
        runCatching { File(context.filesDir, "audio").deleteRecursively() }
        for (d in db.downloads().observe().first()) db.downloads().delete(d.key)
    }
}
