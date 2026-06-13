package com.fastcinema.sniffer

import android.util.Log
import android.webkit.WebResourceRequest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * VideoSniffer — WebView trafigidan video URL larni tutib oladi
 * .mp4, .m3u8, .mkv, .avi va boshqa formatlarni aniqlaydi
 */
class VideoSniffer {

    companion object {
        private const val TAG = "VideoSniffer"

        // Video kengaytmalari
        private val VIDEO_EXTENSIONS = setOf(
            ".mp4", ".m3u8", ".mkv", ".avi", ".mov",
            ".webm", ".ts", ".flv", ".wmv", ".3gp", ".mpd"
        )

        // Video CDN va stream pattern lari
        private val VIDEO_PATTERNS = listOf(
            // HLS stream
            ".m3u8",
            "playlist.m3u8",
            "index.m3u8",
            "master.m3u8",
            // DASH stream
            ".mpd",
            "manifest.mpd",
            // Direct video fayllar
            ".mp4?",
            ".mp4&",
            "/video/",
            "/stream/",
            "/play/",
            "videoplayback",
            // Kino saytlardagi pattern lar
            "get_file",
            "getfilm",
            "cdn.film",
            "video.cdn",
            // Rus/O'zbek CDN lar
            "cdnvideo.ru",
            "videocdn",
            "v.kz",
            "stream.kz",
            // Keng tarqalgan hosters
            "sibnet.ru/video",
            "myvi.ru/watch",
            "vk.com/video_ext",
            "mail.ru/video",
        )

        // Bu URL larni video deb hisoblamaslik (reklama video lari)
        private val SKIP_PATTERNS = listOf(
            "ad.mp4",
            "ads/",
            "preroll",
            "vast",
            "vpaid",
            "doubleclick",
            "googleads",
            "adsystem"
        )

        // Content-Type asosida video aniqlash
        private val VIDEO_CONTENT_TYPES = setOf(
            "video/mp4",
            "video/webm",
            "video/x-matroska",
            "application/x-mpegURL",
            "application/vnd.apple.mpegurl",
            "application/dash+xml",
            "video/ogg",
            "video/quicktime"
        )
    }

    // Topilgan video URL lar (thread-safe)
    private val detectedVideos = CopyOnWriteArrayList<DetectedVideo>()

    // Listener
    var onVideoDetected: ((DetectedVideo) -> Unit)? = null

    /**
     * Har bir WebRequest ni tekshirib o'tadi
     * FastWebViewClient.shouldInterceptRequest() dan chaqiriladi
     */
    fun checkRequest(request: WebResourceRequest) {
        val url = request.url.toString()
        val headers = request.requestHeaders

        // Skip pattern larni tekshir
        if (SKIP_PATTERNS.any { url.lowercase().contains(it) }) return

        // Content-Type headerini tekshir
        val contentType = headers["Content-Type"] ?: headers["Accept"] ?: ""
        if (VIDEO_CONTENT_TYPES.any { contentType.contains(it, ignoreCase = true) }) {
            addVideo(url, detectFormat(url), "Content-Type: $contentType")
            return
        }

        // URL pattern tekshiruvi
        val lowerUrl = url.lowercase()
        if (VIDEO_PATTERNS.any { lowerUrl.contains(it) }) {
            addVideo(url, detectFormat(url), "URL Pattern")
            return
        }
    }

    /**
     * JavaScript injection orqali video elementlarni topish
     * Sayt yuklanganidan keyin chaqiriladi
     */
    fun getVideoDetectionScript(): String = """
        (function() {
            function reportVideo(url, source) {
                if (!url || url.length < 10) return;
                // Android ga xabar berish
                if (window.FastCinemaInterface) {
                    window.FastCinemaInterface.onVideoFound(url, source);
                }
            }
            
            // Video elementlarini kuzat
            function checkVideoElements() {
                document.querySelectorAll('video').forEach(video => {
                    if (video.src) reportVideo(video.src, 'VIDEO_TAG');
                    video.querySelectorAll('source').forEach(src => {
                        if (src.src) reportVideo(src.src, 'SOURCE_TAG');
                    });
                });
            }
            
            // iframe larni kuzat
            function checkIframes() {
                document.querySelectorAll('iframe').forEach(iframe => {
                    try {
                        if (iframe.src) {
                            // iframe src ni yuborish
                            if (window.FastCinemaInterface) {
                                window.FastCinemaInterface.onIframeFound(iframe.src);
                            }
                        }
                    } catch(e) {}
                });
            }
            
            // XMLHttpRequest ni ushlab turish
            const origOpen = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method, url) {
                const lUrl = (url || '').toLowerCase();
                if (lUrl.includes('.m3u8') || lUrl.includes('.mp4') || 
                    lUrl.includes('videoplayback') || lUrl.includes('.mpd')) {
                    reportVideo(url, 'XHR');
                }
                return origOpen.apply(this, arguments);
            };
            
            // fetch ni ushlab turish
            const origFetch = window.fetch;
            window.fetch = function(url, opts) {
                const lUrl = (typeof url === 'string' ? url : '').toLowerCase();
                if (lUrl.includes('.m3u8') || lUrl.includes('.mp4') || 
                    lUrl.includes('.mpd')) {
                    reportVideo(typeof url === 'string' ? url : '', 'FETCH');
                }
                return origFetch.apply(this, arguments);
            };
            
            // DOM kuzatuvchi
            const observer = new MutationObserver(() => {
                checkVideoElements();
                checkIframes();
            });
            
            observer.observe(document.documentElement, {
                childList: true, subtree: true, attributes: true,
                attributeFilter: ['src']
            });
            
            // Darhol tekshir
            checkVideoElements();
            checkIframes();
        })();
    """.trimIndent()

    private fun addVideo(url: String, format: VideoFormat, source: String) {
        // Dublikatni tekshir
        if (detectedVideos.any { it.url == url }) return

        val video = DetectedVideo(
            url = url,
            format = format,
            source = source,
            timestamp = System.currentTimeMillis()
        )

        detectedVideos.add(video)
        Log.d(TAG, "🎬 Video topildi [$source]: ${url.take(80)}...")
        onVideoDetected?.invoke(video)
    }

    private fun detectFormat(url: String): VideoFormat {
        val lower = url.lowercase()
        return when {
            lower.contains(".m3u8") -> VideoFormat.HLS
            lower.contains(".mpd") -> VideoFormat.DASH
            lower.contains(".mp4") -> VideoFormat.MP4
            lower.contains(".mkv") -> VideoFormat.MKV
            lower.contains(".webm") -> VideoFormat.WEBM
            lower.contains(".ts") -> VideoFormat.TS
            else -> VideoFormat.UNKNOWN
        }
    }

    fun getDetectedVideos(): List<DetectedVideo> = detectedVideos.toList()

    fun getBestVideo(): DetectedVideo? {
        // Afzallik: HLS > DASH > MP4 > boshqalar
        return detectedVideos.maxByOrNull { it.format.priority }
    }

    fun clear() = detectedVideos.clear()

    // ============================================================
    // Data classes
    // ============================================================

    data class DetectedVideo(
        val url: String,
        val format: VideoFormat,
        val source: String,
        val timestamp: Long,
        var title: String = "",
        var thumbnailUrl: String = ""
    )

    enum class VideoFormat(val priority: Int, val displayName: String) {
        HLS(10, "HLS Stream (.m3u8)"),
        DASH(9, "DASH Stream (.mpd)"),
        MP4(8, "MP4 Video"),
        MKV(7, "MKV Video"),
        WEBM(6, "WebM Video"),
        TS(5, "TS Stream"),
        UNKNOWN(1, "Video")
    }
}
