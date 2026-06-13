package com.fastcinema.cast

import android.content.Context
import android.util.Log
import com.google.android.gms.cast.framework.*
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.cast.*

/**
 * CastManager — Google Cast orqali TV ga uzatish
 */
class CastManager(private val context: Context) {

    companion object {
        private const val TAG = "CastManager"
    }

    private var sessionManager: SessionManager? = null
    private var castSession: CastSession? = null

    init {
        try {
            sessionManager = CastContext.getSharedInstance(context).sessionManager
            setupSessionListener()
        } catch (e: Exception) {
            Log.w(TAG, "Cast context not available: ${e.message}")
        }
    }

    private fun setupSessionListener() {
        sessionManager?.addSessionManagerListener(object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                castSession = session
                Log.d(TAG, "✅ Cast sessiyasi boshlandi")
            }

            override fun onSessionEnded(session: CastSession, error: Int) {
                castSession = null
                Log.d(TAG, "Cast sessiyasi tugadi")
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                castSession = session
            }

            override fun onSessionStarting(session: CastSession) {}
            override fun onSessionStartFailed(session: CastSession, error: Int) {}
            override fun onSessionEnding(session: CastSession) {}
            override fun onSessionResuming(session: CastSession, sessionId: String) {}
            override fun onSessionResumeFailed(session: CastSession, error: Int) {}
            override fun onSessionSuspended(session: CastSession, reason: Int) {}
        }, CastSession::class.java)
    }

    /**
     * Video ni TV ga yuborish
     */
    fun castVideo(url: String, title: String, thumbnailUrl: String = "") {
        val session = castSession ?: run {
            Log.w(TAG, "Aktiv Cast sessiyasi yo'q")
            return
        }

        val mediaMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            if (thumbnailUrl.isNotEmpty()) {
                addImage(WebImage(android.net.Uri.parse(thumbnailUrl)))
            }
        }

        val mediaInfo = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("video/mp4")
            .setMetadata(mediaMetadata)
            .build()

        val mediaLoadRequestData = com.google.android.gms.cast.framework.media.MediaLoadRequestData
            .Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()

        session.remoteMediaClient?.load(mediaLoadRequestData)
        Log.d(TAG, "📺 TV ga yuborildi: $title")
    }

    fun isCasting(): Boolean = castSession?.isConnected == true

    fun stopCasting() {
        castSession?.remoteMediaClient?.stop()
    }
}

/**
 * CastOptionsProvider — Cast SDK sozlamalari
 */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        return CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
