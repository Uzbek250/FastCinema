package com.fastcinema.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.fastcinema.R
import com.fastcinema.databinding.ActivityPlayerBinding
import kotlin.math.abs

/**
 * PlayerActivity — ExoPlayer asosidagi to'liq ekranli video pleer
 * Gesture boshqaruvi: balandlik, yorqinlik, oldinga/orqaga
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "video_url"
        const val EXTRA_TITLE = "video_title"

        fun start(context: Context, url: String, title: String = "") {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private lateinit var gestureDetector: GestureDetector

    private val videoUrl by lazy { intent.getStringExtra(EXTRA_URL) ?: "" }
    private val videoTitle by lazy { intent.getStringExtra(EXTRA_TITLE) ?: "" }

    // Gesture holati
    private var initialBrightness = -1f
    private var initialVolume = 0
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var isSeekGesture = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFullscreen()
        setupGestures()
        initPlayer()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ============================================================
    // ExoPlayer sozlash
    // ============================================================
    private fun initPlayer() {
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this))
            .build()
            .apply {
                binding.playerView.player = this

                val mediaItem = MediaItem.fromUri(videoUrl)
                setMediaItem(mediaItem)
                playWhenReady = true
                prepare()

                addListener(object : Player.Listener {
                    override fun onIsLoadingChanged(isLoading: Boolean) {
                        binding.progressBar.visibility =
                            if (isLoading) View.VISIBLE else View.GONE
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        showError("Xato: ${error.message}")
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                binding.progressBar.visibility = View.VISIBLE
                            }
                            Player.STATE_READY -> {
                                binding.progressBar.visibility = View.GONE
                                binding.tvTitle.text = videoTitle
                            }
                            else -> {}
                        }
                    }
                })
            }
    }

    // ============================================================
    // Gesture boshqaruvi
    // ============================================================
    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // O'ng tomonni ikki marta bossang — 10 sek oldinga
                // Chap tomonni ikki marta bossang — 10 sek orqaga
                val screenWidth = binding.root.width
                val seekMs = 10_000L
                if (e.x > screenWidth / 2) {
                    player?.seekTo((player?.currentPosition ?: 0) + seekMs)
                    showGestureHint("+10s ⏩")
                } else {
                    player?.seekTo(maxOf(0, (player?.currentPosition ?: 0) - seekMs))
                    showGestureHint("-10s ⏪")
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Controls ko'rsatish/yashirish
                binding.playerView.performClick()
                return true
            }
        })

        binding.gestureOverlay.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    gestureStartX = event.x
                    gestureStartY = event.y
                    initialVolume = audioManager.getStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC
                    )
                    initialBrightness = window.attributes.screenBrightness
                    isSeekGesture = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - gestureStartX
                    val dy = event.y - gestureStartY
                    val screenWidth = binding.root.width
                    val screenHeight = binding.root.height

                    if (!isSeekGesture && abs(dy) > abs(dx) && abs(dy) > 30) {
                        // Vertikal harakat
                        val deltaRatio = -dy / screenHeight

                        if (gestureStartX < screenWidth / 2) {
                            // Chap tomon — yorqinlik
                            val newBrightness = (initialBrightness + deltaRatio).coerceIn(0.01f, 1f)
                            val params = window.attributes.apply { screenBrightness = newBrightness }
                            window.attributes = params
                            val percent = (newBrightness * 100).toInt()
                            showGestureHint("☀️ $percent%")
                        } else {
                            // O'ng tomon — ovoz
                            val delta = (deltaRatio * maxVolume).toInt()
                            val newVol = (initialVolume + delta).coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(
                                android.media.AudioManager.STREAM_MUSIC, newVol, 0
                            )
                            val percent = (newVol * 100 / maxVolume)
                            showGestureHint("🔊 $percent%")
                        }
                    } else if (!isSeekGesture && abs(dx) > abs(dy) && abs(dx) > 40) {
                        // Gorizontal harakat — seek
                        isSeekGesture = true
                        val duration = player?.duration ?: 0L
                        if (duration > 0) {
                            val seekRatio = dx / screenWidth
                            val seekDelta = (seekRatio * duration * 0.3).toLong()
                            val newPos = ((player?.currentPosition ?: 0) + seekDelta)
                                .coerceIn(0, duration)
                            val direction = if (seekDelta > 0) "⏩" else "⏪"
                            showGestureHint("$direction ${formatTime(newPos)}")
                        }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (isSeekGesture) {
                        val dx = event.x - gestureStartX
                        val duration = player?.duration ?: 0L
                        val seekRatio = dx / binding.root.width
                        val seekDelta = (seekRatio * duration * 0.3).toLong()
                        val newPos = ((player?.currentPosition ?: 0) + seekDelta).coerceIn(0, duration)
                        player?.seekTo(newPos)
                    }
                    hideGestureHint()
                }
            }
            true
        }
    }

    private fun showGestureHint(text: String) {
        binding.tvGestureHint.text = text
        binding.tvGestureHint.visibility = View.VISIBLE
    }

    private fun hideGestureHint() {
        binding.tvGestureHint.postDelayed({
            binding.tvGestureHint.visibility = View.GONE
        }, 800)
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes % 60, seconds % 60)
        } else {
            "%d:%02d".format(minutes, seconds % 60)
        }
    }

    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onResume() {
        super.onResume()
        player?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
