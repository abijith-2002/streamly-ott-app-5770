package com.app.streamly

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import coil.load
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.StyledPlayerView

/**
 * PUBLIC_INTERFACE
 * PlayerActivity displays an immersive, full-screen ExoPlayer instance with custom rounded timebar controls.
 *
 * Intent extras accepted:
 * - EXTRA_MEDIA_URL (String, required): direct URL to the media stream to play.
 * - EXTRA_TITLE (String, optional): title to display (accessibility/contentDescription).
 * - EXTRA_POSTER (String, optional): poster URL to show temporarily as artwork.
 *
 * Lifecycle:
 * - Creates and prepares ExoPlayer in onStart/onResume depending on SDK level.
 * - Releases ExoPlayer in onPause/onStop depending on SDK level.
 * - Keeps screen on during playback via playerView.keepScreenOn and window flags.
 */
class PlayerActivity : Activity() {

    companion object {
        const val EXTRA_MEDIA_URL = "extra_media_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_POSTER = "extra_poster"
        private const val TAG = "PlayerActivity"

        // PUBLIC_INTERFACE
        fun newIntent(context: Context, mediaUrl: String, title: String? = null, poster: String? = null): Intent {
            /** Build an Intent for launching the PlayerActivity with media metadata. */
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_URL, mediaUrl)
                if (!title.isNullOrBlank()) putExtra(EXTRA_TITLE, title)
                if (!poster.isNullOrBlank()) putExtra(EXTRA_POSTER, poster)
            }
        }
    }

    private lateinit var playerView: StyledPlayerView
    private var posterView: ImageView? = null
    private var titleView: TextView? = null

    private var exoPlayer: ExoPlayer? = null
    private var mediaUrl: String = ""
    private var title: String = ""
    private var poster: String = ""

    // Unified controller animation group (applies to progress, times, play/pause, settings together)
    private var controllerGroup: View? = null
    private val showInterpolator = DecelerateInterpolator() // Material-like ease-out for showing
    private val hideInterpolator = AccelerateInterpolator() // Ease-in for hiding
    private val controllerAnimDurationMs = 220L
    private val controllerTranslateYPx: Float = 0f // No translate; fade-only to avoid bottom row clipping

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark background to avoid white flashes
        window.setBackgroundDrawableResource(R.color.streamly_black)
        // Edge-to-edge: allow content to draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_player)

        // Read extras
        mediaUrl = intent.getStringExtra(EXTRA_MEDIA_URL).orEmpty()
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        poster = intent.getStringExtra(EXTRA_POSTER).orEmpty()

        if (mediaUrl.isBlank()) {
            Toast.makeText(this, "Missing media URL", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        playerView = findViewById(R.id.playerView)
        posterView = findViewById(R.id.playerPoster)
        titleView = findViewById(R.id.playerTitle)

        // Keep screen on while this activity is active
        playerView.keepScreenOn = true
        // Apply a single global timeout for the entire group (no per-view timers)
        playerView.controllerShowTimeoutMs = 5000
        playerView.useController = true

        // Set initial UI
        titleView?.text = title
        if (poster.isNotBlank()) {
            posterView?.visibility = View.VISIBLE
            posterView?.load(poster) {
                crossfade(true)
                placeholder(R.drawable.poster_placeholder)
                error(R.drawable.poster_placeholder)
            }
        } else {
            posterView?.visibility = View.GONE
        }

        // Hook controller group + animations before user interaction
        setupUnifiedControllerAnimations()

        // Wire custom controls: settings and fullscreen close/back
        wireControllerButtons()

        // Fullscreen flags
        enterImmersiveMode()
    }

    private fun wireControllerButtons() {
        // Try immediate lookup; StyledPlayerView inflates controller lazily
        attachControllerListenersIfPresent()
        playerView.post {
            attachControllerListenersIfPresent()
        }
    }

    private fun attachControllerListenersIfPresent() {
        val settingsButton = playerView.findViewById<ImageButton?>(R.id.exo_settings)
        val fullscreenButton = playerView.findViewById<ImageButton?>(R.id.exo_fullscreen)
        settingsButton?.setOnClickListener {
            Toast.makeText(this, getString(R.string.player_settings), Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Settings clicked (stub).")
        }
        // Use fullscreen button as a back/exit in dedicated player activity
        fullscreenButton?.setOnClickListener {
            onBackPressed()
        }
    }

    /**
     * Sets up a single controller visibility coordinator that animates the entire controls group
     * (play/pause, timebar, times, and settings) with the same easing and duration.
     *
     * Uses StyledPlayerView.setControllerVisibilityListener to sync with the controller lifecycle
     * and ensures the auto-hide timeout applies to the whole group simultaneously.
     */
    private fun setupUnifiedControllerAnimations() {
        // Find our unified group; fallback to root controller if custom group not found
        controllerGroup = playerView.findViewById(R.id.controllerGroup)
            ?: playerView.findViewById(R.id.exo_controller)

        // Initialize group to visible without flicker; StyledPlayerView will trigger hide later via timeout
        controllerGroup?.apply {
            alpha = 1f
            translationY = 0f
            visibility = View.VISIBLE
        }

        // Animate when the controller visibility changes
        playerView.setControllerVisibilityListener(
            StyledPlayerView.ControllerVisibilityListener { visibility ->
                animateControllerGroup(show = visibility == View.VISIBLE)
            }
        )

        // Also ensure initial state after inflation (in case listener hasn't fired yet)
        playerView.post {
            // Default show state on start; Exo will auto-hide after timeout
            animateControllerGroup(show = true, immediate = true)
        }
    }

    /**
     * Animate the unified controls group using consistent easing and durations.
     * Fade-only to avoid bottom-row clipping where the time bar could appear to hide earlier.
     */
    private fun animateControllerGroup(show: Boolean, immediate: Boolean = false) {
        val group = controllerGroup ?: return
        group.animate().cancel()

        if (show) {
            group.visibility = View.VISIBLE
            if (immediate) {
                group.alpha = 1f
                return
            }
            if (group.alpha < 1f) {
                group.alpha = 0f
            }
            group.animate()
                .alpha(1f)
                .setDuration(controllerAnimDurationMs)
                .setInterpolator(showInterpolator)
                .withEndAction { group.visibility = View.VISIBLE }
                .start()
        } else {
            if (immediate) {
                group.alpha = 0f
                group.visibility = View.GONE
                return
            }
            group.animate()
                .alpha(0f)
                .setDuration(controllerAnimDurationMs)
                .setInterpolator(hideInterpolator)
                .withEndAction { group.visibility = View.GONE }
                .start()
        }
    }

    private fun initializePlayer() {
        if (exoPlayer != null) return

        try {
            val uri = Uri.parse(mediaUrl)
            if (uri.scheme.isNullOrBlank()) {
                Toast.makeText(this, "Invalid media URL", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            val player = ExoPlayer.Builder(this).build().also { created ->
                exoPlayer = created
                created.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "ExoPlayer error: ${error.errorCodeName}", error)
                        Toast.makeText(
                            this@PlayerActivity,
                            "Playback error: ${error.errorCodeName}",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                // Hide poster when ready to render
                                posterView?.visibility = View.GONE
                            }
                            Player.STATE_ENDED -> {
                                // Keep activity; user can exit manually.
                            }
                        }
                    }
                })
            }

            if (playerView.player !== player) {
                playerView.player = player
            }

            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
            player.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize player", e)
            Toast.makeText(this, "Unable to start playback", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
        playerView.player = null
    }

    private fun enterImmersiveMode() {
        // Keep screen on at window level while active
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= 24) {
            initializePlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-enable immersive edge-to-edge and keep screen on during playback
        playerView.keepScreenOn = true
        enterImmersiveMode()
        if (Build.VERSION.SDK_INT < 24) {
            initializePlayer()
        }
    }

    override fun onPause() {
        // Restore system bars and allow screen to turn off when leaving the activity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
        @Suppress("DEPRECATION")
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerView.keepScreenOn = false

        if (Build.VERSION.SDK_INT < 24) {
            releasePlayer()
        }
        super.onPause()
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= 24) {
            releasePlayer()
        }
        super.onStop()
    }

    override fun onBackPressed() {
        // Release and finish
        releasePlayer()
        super.onBackPressed()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}
