package com.fastcinema.browser

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.fastcinema.sniffer.VideoSniffer

/**
 * FastWebViewClient — asosiy brauzer mexanizmi
 * - Har bir so'rovni ushlab turadi
 * - AdBlocker ga yuboradi
 * - VideoSniffer ga yuboradi
 */
class FastWebViewClient(
    private val adBlocker: AdBlocker,
    private val videoSniffer: VideoSniffer,
    private val listener: BrowserListener
) : WebViewClient() {

    interface BrowserListener {
        fun onPageStarted(url: String)
        fun onPageFinished(url: String)
        fun onProgressChanged(progress: Int)
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        // 1. Avval AdBlocker tekshiruvi
        val blocked = adBlocker.shouldBlock(request)
        if (blocked != null) return blocked

        // 2. VideoSniffer — video URL larni kuzat
        videoSniffer.checkRequest(request)

        // 3. Normal o'tkazib yubor
        return null
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        videoSniffer.clear()
        listener.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)

        // Reklama yashiruvchi skriptni inject qil
        view.evaluateJavascript(adBlocker.getAdHidingScript(), null)

        // Video detector skriptni inject qil
        view.evaluateJavascript(videoSniffer.getVideoDetectionScript(), null)

        listener.onPageFinished(url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: android.webkit.WebResourceError
    ) {
        // Faqat main frame xatolarini ko'rsat
        if (request.isForMainFrame) {
            super.onReceivedError(view, request, error)
        }
    }
}
