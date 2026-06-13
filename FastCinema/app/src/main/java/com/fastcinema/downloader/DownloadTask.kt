package com.fastcinema.downloader

import java.io.File

/**
 * DownloadTask — yuklab olish vazifasi modeli
 */
data class DownloadTask(
    val id: String,
    val url: String,
    val outputFile: File,
    val title: String = "",
    val headers: Map<String, String> = emptyMap(),
    val segmentCount: Int = MultiSegmentDownloader.DEFAULT_SEGMENTS,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Yuklab olish holati
 */
enum class DownloadStatus {
    PENDING,      // Kutilmoqda
    DOWNLOADING,  // Yuklanmoqda
    PAUSED,       // To'xtatilgan
    COMPLETED,    // Tugallangan
    ERROR,        // Xato
    CANCELLED     // Bekor qilingan
}

/**
 * Yuklab olish jarayoni ma'lumotlari
 */
data class DownloadProgress(
    val taskId: String,
    val downloaded: Long,
    val total: Long,
    val speedBps: Long,      // Bytes per second
    val percent: Int,        // 0-100, -1 noma'lum
    val activeSegments: Int = 1
) {
    val speedMbps: Double get() = speedBps / 1_000_000.0
    val speedFormatted: String get() = when {
        speedBps > 1_000_000 -> "%.1f MB/s".format(speedMbps)
        speedBps > 1_000 -> "%.0f KB/s".format(speedBps / 1000.0)
        else -> "$speedBps B/s"
    }
    val downloadedFormatted: String get() = formatBytes(downloaded)
    val totalFormatted: String get() = if (total > 0) formatBytes(total) else "?"

    private fun formatBytes(bytes: Long): String = when {
        bytes > 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes > 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes > 1_000 -> "%.0f KB".format(bytes / 1000.0)
        else -> "$bytes B"
    }
}

/**
 * Yuklab olish natijasi
 */
sealed class DownloadResult {
    data class Success(val taskId: String, val file: File) : DownloadResult()
    data class Error(val taskId: String, val message: String) : DownloadResult()
    data class Cancelled(val taskId: String) : DownloadResult()
}
