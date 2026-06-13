package com.fastcinema.downloader

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * MultiSegmentDownloader — Ko'p tarmoqli yuklagich
 * IDM kabi faylni N bo'lakka bo'lib parallel yuklaydi
 * Maksimal tezlikka erishadi
 */
class MultiSegmentDownloader(
    private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "MultiSegDL"
        const val DEFAULT_SEGMENTS = 8
        const val MAX_SEGMENTS = 16
        const val MIN_SEGMENT_SIZE = 1024 * 1024L // 1 MB
        const val BUFFER_SIZE = 8192
    }

    // ============================================================
    // Asosiy yuklash funksiyasi
    // ============================================================
    suspend fun download(
        task: DownloadTask,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {

        val tempDir = File(task.outputFile.parent, ".tmp_${task.id}")
        tempDir.mkdirs()

        try {
            // 1. Fayl hajmini va server imkoniyatlarini tekshir
            val serverInfo = getServerInfo(task.url, task.headers)
            Log.d(TAG, "Server: hajm=${serverInfo.contentLength}, range=${serverInfo.supportsRange}")

            // 2. Segment sonini aniqlash
            val segmentCount = when {
                !serverInfo.supportsRange -> 1  // Server range qo'llab-quvvatlamasa
                serverInfo.contentLength <= 0 -> 1  // Noma'lum hajm
                serverInfo.contentLength < MIN_SEGMENT_SIZE * 2 -> 1  // Juda kichik fayl
                else -> min(
                    task.segmentCount.coerceAtMost(MAX_SEGMENTS),
                    (serverInfo.contentLength / MIN_SEGMENT_SIZE).toInt()
                )
            }

            Log.d(TAG, "Segment soni: $segmentCount, hajm: ${serverInfo.contentLength}")

            return@withContext if (segmentCount == 1) {
                // Oddiy yuklash
                downloadSingle(task, tempDir, serverInfo, onProgress)
            } else {
                // Ko'p tarmoqli yuklash
                downloadMultiSegment(task, tempDir, serverInfo, segmentCount, onProgress)
            }

        } catch (e: CancellationException) {
            cleanupTempDir(tempDir)
            DownloadResult.Cancelled(task.id)
        } catch (e: Exception) {
            cleanupTempDir(tempDir)
            Log.e(TAG, "Yuklash xatosi: ${e.message}")
            DownloadResult.Error(task.id, e.message ?: "Noma'lum xato")
        }
    }

    // ============================================================
    // Server imkoniyatlarini aniqlash
    // ============================================================
    private suspend fun getServerInfo(
        url: String,
        headers: Map<String, String>
    ): ServerInfo = withContext(Dispatchers.IO) {

        val request = Request.Builder()
            .url(url)
            .head()  // HEAD so'rovi — faqat headerlar
            .apply {
                headers.forEach { (k, v) -> addHeader(k, v) }
                addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            }
            .build()

        try {
            val response = client.newCall(request).execute()
            val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
            val acceptRanges = response.header("Accept-Ranges")
            val supportsRange = acceptRanges?.contains("bytes") == true
                    || response.code == 206
            response.close()

            ServerInfo(contentLength, supportsRange)
        } catch (e: Exception) {
            // HEAD ishlamasa GET bilan urinib ko'r
            ServerInfo(-1L, false)
        }
    }

    // ============================================================
    // Oddiy (bitta) yuklash
    // ============================================================
    private suspend fun downloadSingle(
        task: DownloadTask,
        tempDir: File,
        serverInfo: ServerInfo,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {

        val tempFile = File(tempDir, "part_0")
        val downloaded = AtomicLong(0)
        val startTime = System.currentTimeMillis()

        val request = Request.Builder()
            .url(task.url)
            .apply {
                task.headers.forEach { (k, v) -> addHeader(k, v) }
                addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            }
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            return@withContext DownloadResult.Error(task.id, "HTTP ${response.code}")
        }

        val body = response.body ?: return@withContext DownloadResult.Error(task.id, "Bo'sh javob")
        val totalSize = serverInfo.contentLength.takeIf { it > 0 } ?: body.contentLength()

        body.byteStream().use { input ->
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded.addAndGet(bytesRead.toLong())

                    val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                    val speed = if (elapsed > 0) downloaded.get() / elapsed else 0.0

                    onProgress(
                        DownloadProgress(
                            taskId = task.id,
                            downloaded = downloaded.get(),
                            total = totalSize,
                            speedBps = speed.toLong(),
                            percent = if (totalSize > 0) (downloaded.get() * 100 / totalSize).toInt() else -1
                        )
                    )
                }
            }
        }

        // Temp faylni asosiy joyga ko'chir
        mergeAndSave(listOf(tempFile), task.outputFile)
        cleanupTempDir(tempDir)

        DownloadResult.Success(task.id, task.outputFile)
    }

    // ============================================================
    // Ko'p tarmoqli yuklash — asosiy engine
    // ============================================================
    private suspend fun downloadMultiSegment(
        task: DownloadTask,
        tempDir: File,
        serverInfo: ServerInfo,
        segmentCount: Int,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {

        val totalSize = serverInfo.contentLength
        val segmentSize = totalSize / segmentCount
        val startTime = System.currentTimeMillis()
        val totalDownloaded = AtomicLong(0)

        // Segment oraliqlarini hisoblash
        val segments = (0 until segmentCount).map { i ->
            val start = i * segmentSize
            val end = if (i == segmentCount - 1) totalSize - 1 else start + segmentSize - 1
            SegmentRange(i, start, end)
        }

        // Barcha segmentlarni parallel yukla
        val jobs = segments.map { segment ->
            async(Dispatchers.IO) {
                downloadSegment(
                    url = task.url,
                    headers = task.headers,
                    segment = segment,
                    tempDir = tempDir,
                    onBytesDownloaded = { bytes ->
                        val current = totalDownloaded.addAndGet(bytes)
                        val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                        val speed = if (elapsed > 0) current / elapsed else 0.0

                        onProgress(
                            DownloadProgress(
                                taskId = task.id,
                                downloaded = current,
                                total = totalSize,
                                speedBps = speed.toLong(),
                                percent = (current * 100 / totalSize).toInt(),
                                activeSegments = segmentCount
                            )
                        )
                    }
                )
            }
        }

        // Hammasi tugashini kut
        val results = jobs.awaitAll()
        val failed = results.filterIsInstance<SegmentResult.Error>()

        if (failed.isNotEmpty()) {
            return@withContext DownloadResult.Error(
                task.id,
                "Segmentlar xatosi: ${failed.map { it.segmentIndex }}"
            )
        }

        // Segmentlarni birlashtir
        val partFiles = segments.map { File(tempDir, "part_${it.index}") }
        mergeAndSave(partFiles, task.outputFile)
        cleanupTempDir(tempDir)

        Log.d(TAG, "✅ Yuklash tugadi: ${task.outputFile.name}")
        DownloadResult.Success(task.id, task.outputFile)
    }

    // ============================================================
    // Bitta segment yuklash
    // ============================================================
    private suspend fun downloadSegment(
        url: String,
        headers: Map<String, String>,
        segment: SegmentRange,
        tempDir: File,
        onBytesDownloaded: (Long) -> Unit
    ): SegmentResult = withContext(Dispatchers.IO) {

        val outputFile = File(tempDir, "part_${segment.index}")
        val alreadyDownloaded = outputFile.length()
        val startByte = segment.start + alreadyDownloaded

        // Fayl allaqachon to'liq yuklab bo'lingan
        if (alreadyDownloaded >= segment.end - segment.start + 1) {
            return@withContext SegmentResult.Success(segment.index)
        }

        try {
            val request = Request.Builder()
                .url(url)
                .apply {
                    headers.forEach { (k, v) -> addHeader(k, v) }
                    addHeader("Range", "bytes=$startByte-${segment.end}")
                    addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    addHeader("Connection", "keep-alive")
                }
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful && response.code != 206) {
                return@withContext SegmentResult.Error(segment.index, "HTTP ${response.code}")
            }

            val body = response.body ?: return@withContext SegmentResult.Error(segment.index, "Bo'sh body")

            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.seek(alreadyDownloaded)
                body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        raf.write(buffer, 0, bytesRead)
                        onBytesDownloaded(bytesRead.toLong())
                    }
                }
            }

            SegmentResult.Success(segment.index)
        } catch (e: Exception) {
            Log.e(TAG, "Segment ${segment.index} xatosi: ${e.message}")
            SegmentResult.Error(segment.index, e.message ?: "")
        }
    }

    // ============================================================
    // Segmentlarni birlashtirish
    // ============================================================
    private fun mergeAndSave(parts: List<File>, output: File) {
        output.parentFile?.mkdirs()
        output.outputStream().use { out ->
            parts.forEach { part ->
                if (part.exists()) {
                    part.inputStream().use { it.copyTo(out) }
                }
            }
        }
    }

    private fun cleanupTempDir(dir: File) {
        dir.deleteRecursively()
    }

    // ============================================================
    // Data classes
    // ============================================================

    data class ServerInfo(val contentLength: Long, val supportsRange: Boolean)

    data class SegmentRange(val index: Int, val start: Long, val end: Long)

    sealed class SegmentResult {
        data class Success(val segmentIndex: Int) : SegmentResult()
        data class Error(val segmentIndex: Int, val message: String) : SegmentResult()
    }
}
