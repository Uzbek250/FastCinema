package com.fastcinema.downloader

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * DownloadManager — barcha yuklamalarni boshqaruvchi
 * Singleton pattern
 */
class DownloadManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "DownloadManager"

        @Volatile
        private var instance: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager {
            return instance ?: synchronized(this) {
                instance ?: DownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val downloader = MultiSegmentDownloader(client)

    // Aktiv yuklamalar
    private val activeTasks = mutableMapOf<String, Job>()

    // StateFlow — UI ga real vaqt yangilanishlari
    private val _downloads = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloads: StateFlow<List<DownloadTask>> = _downloads

    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress

    // ============================================================
    // Yuklashni boshlash
    // ============================================================
    fun startDownload(
        url: String,
        title: String,
        headers: Map<String, String> = emptyMap(),
        segmentCount: Int = MultiSegmentDownloader.DEFAULT_SEGMENTS
    ): String {
        val taskId = UUID.randomUUID().toString().take(8)
        val outputFile = getOutputFile(title)

        val task = DownloadTask(
            id = taskId,
            url = url,
            outputFile = outputFile,
            title = title,
            headers = headers + getDefaultHeaders(),
            segmentCount = segmentCount,
            status = DownloadStatus.DOWNLOADING
        )

        addTask(task)

        val job = scope.launch {
            try {
                Log.d(TAG, "Yuklash boshlandi: $title")

                downloader.download(task) { progress ->
                    updateProgress(taskId, progress)
                }.also { result ->
                    when (result) {
                        is DownloadResult.Success -> {
                            updateTaskStatus(taskId, DownloadStatus.COMPLETED)
                            Log.d(TAG, "✅ Yuklandi: ${result.file.name}")
                        }
                        is DownloadResult.Error -> {
                            updateTaskStatus(taskId, DownloadStatus.ERROR)
                            Log.e(TAG, "❌ Xato: ${result.message}")
                        }
                        is DownloadResult.Cancelled -> {
                            updateTaskStatus(taskId, DownloadStatus.CANCELLED)
                        }
                    }
                }
            } catch (e: Exception) {
                updateTaskStatus(taskId, DownloadStatus.ERROR)
                Log.e(TAG, "Download exception: ${e.message}")
            } finally {
                activeTasks.remove(taskId)
            }
        }

        activeTasks[taskId] = job
        return taskId
    }

    // ============================================================
    // Yuklamani to'xtatish
    // ============================================================
    fun cancelDownload(taskId: String) {
        activeTasks[taskId]?.cancel()
        activeTasks.remove(taskId)
        updateTaskStatus(taskId, DownloadStatus.CANCELLED)
    }

    // ============================================================
    // Chiqish papkasini aniqlash
    // ============================================================
    private fun getOutputFile(title: String): File {
        val moviesDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MOVIES
        )
        val fastCinemaDir = File(moviesDir, "FastCinema")
        fastCinemaDir.mkdirs()

        val safeName = title
            .replace(Regex("[^a-zA-Z0-9а-яёА-ЯЁ-]"), "_")
            .take(50)

        return File(fastCinemaDir, "${safeName}_${System.currentTimeMillis()}.mp4")
    }

    private fun getDefaultHeaders(): Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Samsung S24 Ultra) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
        "Accept" to "*/*",
        "Accept-Language" to "uz-UZ,uz;q=0.9,ru;q=0.8"
    )

    private fun addTask(task: DownloadTask) {
        _downloads.value = _downloads.value + task
    }

    private fun updateTaskStatus(taskId: String, status: DownloadStatus) {
        _downloads.value = _downloads.value.map {
            if (it.id == taskId) it.copy(status = status) else it
        }
    }

    private fun updateProgress(taskId: String, progress: DownloadProgress) {
        _progress.value = _progress.value + (taskId to progress)
    }
}
