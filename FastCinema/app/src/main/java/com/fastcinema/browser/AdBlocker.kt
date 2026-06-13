package com.fastcinema.browser

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

/**
 * AdBlocker — reklama bloklovchi modul
 * O'zbek va rus kino saytlarining reklama domenlarini bloklaydi
 * EasyList + o'zbek sayt filtrlari asosida
 */
class AdBlocker(private val context: Context) {

    companion object {
        private const val TAG = "AdBlocker"

        // ============================================================
        // BLOKLANGAN DOMENLAR — Reklama serverlari
        // ============================================================
        private val BLOCKED_DOMAINS = setOf(
            // Google reklama
            "doubleclick.net",
            "googleadservices.com",
            "googlesyndication.com",
            "adservice.google.com",
            "googletagservices.com",
            "googletagmanager.com",
            "google-analytics.com",
            "analytics.google.com",

            // Yandex reklama
            "an.yandex.ru",
            "mc.yandex.ru",
            "yandex-team.ru",
            "metrika.yandex.ru",

            // Keng tarqalgan reklama tarmoqlari
            "pagead2.googlesyndication.com",
            "adnxs.com",
            "adsrvr.org",
            "advertising.com",
            "adblade.com",
            "adform.net",
            "adition.com",
            "adcolony.com",
            "admob.com",
            "ads.pubmatic.com",
            "secure.adnxs.com",
            "ib.adnxs.com",

            // Pop-up / redirect saytlar
            "popcash.net",
            "popads.net",
            "pop.xyz",
            "trafficjunky.net",
            "trafficrouter.io",
            "redirect.disqus.com",
            "exoclick.com",
            "exosrv.com",
            "hilltopads.net",
            "propellerads.com",
            "adsterra.com",

            // Tracking va analytics
            "hotjar.com",
            "mouseflow.com",
            "fullstory.com",
            "mixpanel.com",
            "segment.com",
            "amplitude.com",
            "heap.io",
            "smartlook.com",

            // Kino saytlardagi maxsus reklamalar
            "cdn.syndication.twimg.com",
            "static.doubleclick.net",
            "stats.wp.com",
            "pixel.wp.com",

            // O'zbek/rus saytlardagi reklama CDN lar
            "deliver.psmcode.net",
            "cdn.creativebloq.com",
            "banneradz.ru",
            "marketgid.com",
            "teasernet.com",
            "relap.io",
            "gnezdo.ru",
            "mgid.com"
        )

        // ============================================================
        // BLOKLANGAN URL PATTERN LAR — keyword asosida
        // ============================================================
        private val BLOCKED_PATTERNS = listOf(
            "/ads/",
            "/adv/",
            "/advertisement/",
            "/banner/",
            "/banners/",
            "/popup/",
            "/popunder/",
            "/preroll/",
            "/ad-",
            "-ad.",
            "/tracking/",
            "/tracker/",
            "/analytics/",
            "/pixel/",
            "/beacon/",
            "adblock-detector",
            "detectadblock",
            "ad_script",
            "adsense",
            "adsbygoogle",
            "/prebid/",
            "/prebidjs/",
            "ima3.js",             // Google IMA (video pre-roll)
            "imasdk.googleapis.com",
            "vast.xml",
            "vpaid",
            "googlevideo.com/videoplayback?.*ctier=L",  // YouTube reklamalari
        )

        // ============================================================
        // BLOQLASH KERAK BO'LMAGAN — Oq ro'yxat (whitelist)
        // Video CDN lari — bular hech qachon bloklanmasin
        // ============================================================
        private val WHITELIST_DOMAINS = setOf(
            "kinolar.uz",
            "paropakar.uz",
            "kinogo.eu",
            "rezka.ag",
            "hdrezka.ag",
            "uakino.club",
            "youtube.com",
            "googlevideo.com",
            "ytimg.com",
            "cdninstagram.com",
            "akamaized.net",
            "cloudfront.net",
            "fastly.net",
            "cdn77.org",
            // O'zbek va rus video CDN lari
            "mail.ru",
            "vk.com",
            "cdnvideo.ru"
        )

        // Bo'sh response (blok uchun)
        private val EMPTY_RESPONSE = WebResourceResponse(
            "text/plain", "utf-8", ByteArrayInputStream("".toByteArray())
        )
    }

    // Statistika
    private var blockedCount = 0L
    private var savedBytes = 0L

    /**
     * Asosiy bloklash funksiyasi
     * WebViewClient.shouldInterceptRequest() dan chaqiriladi
     */
    fun shouldBlock(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        val host = request.url.host ?: return null

        // Oq ro'yxatdagi domenlarni o'tkazib yubor
        if (WHITELIST_DOMAINS.any { host.contains(it) }) {
            return null
        }

        // Domen tekshiruvi
        if (isDomainBlocked(host)) {
            blockedCount++
            Log.d(TAG, "🚫 Bloklandi (domen): $host")
            return EMPTY_RESPONSE
        }

        // Pattern tekshiruvi
        if (isPatternBlocked(url)) {
            blockedCount++
            Log.d(TAG, "🚫 Bloklandi (pattern): $url")
            return EMPTY_RESPONSE
        }

        return null
    }

    /**
     * Domen bloklash tekshiruvi
     * subdomen ham tekshiriladi: ads.example.com → example.com
     */
    private fun isDomainBlocked(host: String): Boolean {
        // To'liq domen moslik
        if (BLOCKED_DOMAINS.contains(host)) return true

        // Subdomen tekshiruvi
        val parts = host.split(".")
        for (i in 0 until parts.size - 1) {
            val parent = parts.drop(i).joinToString(".")
            if (BLOCKED_DOMAINS.contains(parent)) return true
        }

        return false
    }

    /**
     * URL pattern bloklash tekshiruvi
     */
    private fun isPatternBlocked(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return BLOCKED_PATTERNS.any { pattern ->
            lowerUrl.contains(pattern.lowercase())
        }
    }

    /**
     * JavaScript injection — sayt yuklanganidan keyin
     * DOM dagi reklama elementlarini yashiradi
     */
    fun getAdHidingScript(): String = """
        (function() {
            // Reklama elementlarini yashirish
            const adSelectors = [
                '[id*="ad"]', '[class*="ad-"]', '[class*="-ad"]',
                '[id*="banner"]', '[class*="banner"]',
                '[id*="popup"]', '[class*="popup"]',
                '[id*="overlay"]', '[class*="overlay-ad"]',
                '.adsbygoogle', '#google_ads_frame',
                'ins.adsbygoogle',
                '[data-ad]', '[data-ads]',
                // Kino saytlardagi maxsus selectorlar
                '.kinogo-banner', '.kinogo-popup',
                '.rezka-ad', '.hdrezka-popup',
                '[class*="preroll"]', '[class*="pre-roll"]',
                '.vast-player', '#vast-container',
                // Umumiy pop-up layerlar
                '.modal-backdrop:not(.video-modal)',
                'div[style*="position:fixed"][style*="z-index:9"]',
                'div[style*="position: fixed"][style*="z-index: 9"]'
            ];
            
            function hideAds() {
                adSelectors.forEach(selector => {
                    try {
                        document.querySelectorAll(selector).forEach(el => {
                            // Video pleer emasligini tekshir
                            if (!el.querySelector('video') && !el.closest('video')) {
                                el.style.display = 'none';
                                el.style.visibility = 'hidden';
                                el.style.opacity = '0';
                                el.style.pointerEvents = 'none';
                            }
                        });
                    } catch(e) {}
                });
            }
            
            // Darhol chaqir
            hideAds();
            
            // DOM o'zgarishlarini kuzat
            const observer = new MutationObserver(() => hideAds());
            observer.observe(document.body || document.documentElement, {
                childList: true, subtree: true
            });
            
            // AdBlock detector larni aldash
            window.canRunAds = true;
            window.isAdBlockActive = false;
            if (typeof window.adsbygoogle !== 'undefined') {
                window.adsbygoogle = window.adsbygoogle || [];
            }
        })();
    """.trimIndent()

    /**
     * Statistika
     */
    fun getStats(): AdBlockStats = AdBlockStats(blockedCount, savedBytes)

    fun resetStats() {
        blockedCount = 0
        savedBytes = 0
    }

    data class AdBlockStats(
        val blockedRequests: Long,
        val savedBytes: Long
    )
}
