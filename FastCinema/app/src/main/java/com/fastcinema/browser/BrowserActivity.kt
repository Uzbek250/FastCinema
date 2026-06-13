package com.fastcinema.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fastcinema.cast.CastManager
import com.fastcinema.databinding.ActivityBrowserBinding
import com.fastcinema.downloader.DownloadManager
import com.fastcinema.player.PlayerActivity
import com.fastcinema.sniffer.VideoSniffer
import kotlinx.coroutines.launch

/**
 * BrowserActivity — asosiy brauzer ekrani
 */
@SuppressLint("SetJavaScriptEnabled")
class BrowserActivity : AppCompatActivity(), FastWebViewClient.BrowserListener {

    private lateinit var binding: ActivityBrowserBinding
    private lateinit var adBlocker: AdBlocker
    private lateinit var videoSniffer: VideoSniffer
    private lateinit var castManager: CastManager
    private lateinit var downloadManager: DownloadManager
    private var currentVideoUrl: String? = null

    // O'zbek va rus kino saytlari tez kirish uchun
    private val quickSites = listOf(
        QuickSite("Kinolar", "https://kinolar.uz"),
        QuickSite("Kinogo", "https://kinogo.eu"),
        QuickSite("HDRezka", "https://hdrezka.ag"),
        QuickSite("Paropakar", "https://paropakar.uz"),
        QuickSite("Ziyouz", "https://ziyouz.com")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adBlocker = AdBlocker(this)
        videoSniffer = VideoSniffer()
        castManager = CastManager(this)
        downloadManager = DownloadManager.getInstance(this)

        setupWebView()
        setupUrlBar()
        setupVideoSnifferCallback()
        setupQuickSites()
        setupButtons()

        // Boshlang'ich sahifa
        binding.webView.loadUrl("https://kinolar.uz")
    }

    // ============================================================
    // WebView sozlash
    // ============================================================
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                mediaPlaybackRequiresUserGesture = false
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Samsung SM-S928B) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/124.0.0.0 Mobile Safari/537.36"
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }

            webViewClient = FastWebViewClient(adBlocker, videoSniffer, this@BrowserActivity)

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    onProgressChanged(newProgress)
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    binding.tvTitle.text = title ?: ""
                }

                // Fullscreen video qo'llab-quvvatlash
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    binding.fullscreenContainer.apply {
                        visibility = View.VISIBLE
                        addView(view)
                    }
                    binding.webView.visibility = View.GONE
                }

                override fun onHideCustomView() {
                    binding.fullscreenContainer.apply {
                        removeAllViews()
                        visibility = View.GONE
                    }
                    binding.webView.visibility = View.VISIBLE
                }
            }

            // JavaScript interface — video topilganda xabar olish
            addJavascriptInterface(object : Any() {
                @JavascriptInterface
                fun onVideoFound(url: String, source: String) {
                    runOnUiThread { handleVideoFound(url) }
                }

                @JavascriptInterface
                fun onIframeFound(iframeUrl: String) {
                    // iframe URL ni ham yuklash (embedded player)
                }
            }, "FastCinemaInterface")
        }
    }

    // ============================================================
    // URL bar sozlash
    // ============================================================
    private fun setupUrlBar() {
        binding.etUrl.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                loadUrl(binding.etUrl.text.toString())
                true
            } else false
        }

        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack()
        }

        binding.btnForward.setOnClickListener {
            if (binding.webView.canGoForward()) binding.webView.goForward()
        }

        binding.btnRefresh.setOnClickListener {
            binding.webView.reload()
        }

        binding.btnHome.setOnClickListener {
            binding.webView.loadUrl("https://kinolar.uz")
        }
    }

    private fun loadUrl(input: String) {
        val url = when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(".") -> "https://$input"
            else -> "https://www.google.com/search?q=${input.replace(" ", "+")}"
        }
        binding.webView.loadUrl(url)
        binding.etUrl.clearFocus()
    }

    // ============================================================
    // Video topilganda
    // ============================================================
    private fun setupVideoSnifferCallback() {
        videoSniffer.onVideoDetected = { video ->
            runOnUiThread {
                currentVideoUrl = video.url
                showVideoPanel(video)
            }
        }
    }

    private fun handleVideoFound(url: String) {
        currentVideoUrl = url
        val video = VideoSniffer.DetectedVideo(
            url = url,
            format = VideoSniffer.VideoFormat.UNKNOWN,
            source = "JS Interface",
            timestamp = System.currentTimeMillis()
        )
        showVideoPanel(video)
    }

    private fun showVideoPanel(video: VideoSniffer.DetectedVideo) {
        binding.videoPanel.visibility = View.VISIBLE
        binding.tvVideoFormat.text = "🎬 ${video.format.displayName}"

        binding.btnPlayInApp.setOnClickListener {
            PlayerActivity.start(this, video.url, binding.tvTitle.text.toString())
        }

        binding.btnDownload.setOnClickListener {
            val title = binding.tvTitle.text.toString().ifEmpty { "Kino" }
            downloadManager.startDownload(video.url, title)
            Toast.makeText(this, "⬇️ Yuklash boshlandi: $title", Toast.LENGTH_SHORT).show()
        }

        binding.btnCastToTv.setOnClickListener {
            if (castManager.isCasting()) {
                castManager.castVideo(video.url, binding.tvTitle.text.toString())
                Toast.makeText(this, "📺 TV ga yuborildi", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "TV ga ulanmagan. Cast tugmasini bosing", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnClosePanel.setOnClickListener {
            binding.videoPanel.visibility = View.GONE
        }
    }

    // ============================================================
    // Tez saytlar
    // ============================================================
    private fun setupQuickSites() {
        binding.quickSitesContainer.removeAllViews()
        quickSites.forEach { site ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = site.name
                setOnClickListener { binding.webView.loadUrl(site.url) }
            }
            binding.quickSitesContainer.addView(chip)
        }
    }

    private fun setupButtons() {
        binding.btnAdBlockStats.setOnClickListener {
            val stats = adBlocker.getStats()
            Toast.makeText(
                this,
                "🛡️ Bloklandi: ${stats.blockedRequests} ta reklama",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ============================================================
    // BrowserListener callbacks
    // ============================================================
    override fun onPageStarted(url: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.etUrl.setText(url)
        binding.videoPanel.visibility = View.GONE
        currentVideoUrl = null
    }

    override fun onPageFinished(url: String) {
        binding.progressBar.visibility = View.GONE
        binding.etUrl.setText(url)
    }

    override fun onProgressChanged(progress: Int) {
        binding.progressBar.progress = progress
        if (progress == 100) {
            binding.progressBar.visibility = View.GONE
        }
    }

    // Back button — brauzer tarixida orqaga
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    data class QuickSite(val name: String, val url: String)
}
