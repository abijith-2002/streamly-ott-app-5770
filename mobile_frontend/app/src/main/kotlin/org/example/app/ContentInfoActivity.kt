package org.example.app

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import coil.load
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.StyledPlayerView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/**
 * PUBLIC_INTERFACE
 * ContentInfoActivity displays details about a selected show.
 *
 * It expects Intent extras:
 * - EXTRA_ID (String): The content ID used for fetching details.
 * - EXTRA_NAME (String): The display name from the rail.
 * - EXTRA_POSTER (String): The poster image URL to show at the top.
 *
 * Behavior:
 * - Shows the poster image full-width at the top.
 * - Overlays a bottom gradient that blends into #090808 for readability.
 * - Fetches GET https://4b746313.api.kavia.app/api/info/{id} to load {title, description}.
 * - Renders title/description over the gradient with Figtree font.
 * - Provides a pill-shaped "Watch now" button (#C60A0A).
 * - On "Watch now": GET /api/play, parse {"url": "<mediaUrl>"}, and start playback with ExoPlayer.
 */
class ContentInfoActivity : Activity() {

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_POSTER = "extra_poster"
        private const val TAG = "ContentInfoActivity"
    }

    private val httpClient by lazy { OkHttpClient() }
    private val baseUrl = "https://4b746313.api.kavia.app"

    private lateinit var posterImage: ImageView
    private lateinit var titleText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var watchNowButton: Button
    private lateinit var progressBar: ProgressBar

    // Overlay elements to hide when playback starts
    private lateinit var gradientOverlay: View
    private lateinit var overlayContent: View

    // Player view for rendering video
    private lateinit var playerView: StyledPlayerView

    // Container and scroll for fullscreen toggle management
    private lateinit var headerContainer: FrameLayout
    private lateinit var extraContentScroll: ScrollView
    private var defaultHeaderHeightPx: Int = 0
    private var isFullscreen: Boolean = false

    // ExoPlayer instance (created on demand)
    private var exoPlayer: ExoPlayer? = null

    // PUBLIC_INTERFACE
    override fun onCreate(savedInstanceState: Bundle?) {
        /** Initialize UI, load poster, and then fetch content details by id. */
        super.onCreate(savedInstanceState)
        // Ensure dark background to avoid white flashes
        window.setBackgroundDrawableResource(R.color.streamly_black)
        setContentView(R.layout.activity_content_info)

        posterImage = findViewById(R.id.detailPosterImage)
        titleText = findViewById(R.id.infoTitle)
        descriptionText = findViewById(R.id.infoDescription)
        watchNowButton = findViewById(R.id.watchNowButton)
        progressBar = findViewById(R.id.infoProgress)
        gradientOverlay = findViewById(R.id.gradientOverlay)
        overlayContent = findViewById(R.id.overlayContent)
        playerView = findViewById(R.id.playerView)
        headerContainer = findViewById(R.id.headerContainer)
        extraContentScroll = findViewById(R.id.extraContentScroll)
        defaultHeaderHeightPx = resources.getDimensionPixelSize(R.dimen.player_header_height)

        // Configure player view (hidden initially)
        playerView.visibility = View.GONE
        playerView.keepScreenOn = true
        playerView.useController = true

        // Wire player controller buttons (settings / fullscreen)
        wirePlayerControls()

        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val poster = intent.getStringExtra(EXTRA_POSTER).orEmpty()

        // Load poster immediately
        posterImage.load(poster) {
            crossfade(true)
            placeholder(R.drawable.poster_placeholder)
            error(R.drawable.poster_placeholder)
        }

        // Set initial title from rail (replaced by server title if available)
        titleText.text = name
        descriptionText.text = "" // Will be populated after network call

        // Attach click handler to "Watch now"
        watchNowButton.setOnClickListener {
            onWatchNowClicked()
        }

        if (id.isNotBlank()) {
            fetchInfo(id)
        }
    }

    /**
     * PUBLIC_INTERFACE
     * Wires the controller's settings and fullscreen buttons.
     */
    private fun wirePlayerControls() {
        // Try immediate lookup
        attachControllerListenersIfPresent()
        // Also post a second attempt in case controller inflates later
        playerView.post {
            attachControllerListenersIfPresent()
        }
    }

    private fun attachControllerListenersIfPresent() {
        val fullscreenButton = playerView.findViewById<ImageButton?>(R.id.exo_fullscreen)
        val settingsButton = playerView.findViewById<ImageButton?>(R.id.exo_settings)
        fullscreenButton?.setOnClickListener { toggleFullscreen() }
        settingsButton?.setOnClickListener { onSettingsClicked() }
    }

    /**
     * PUBLIC_INTERFACE
     * Toggles fullscreen playback by hiding system UI and resizing the player container.
     *
     * Behavior:
     * - Enter: hide status/navigation bars, expand headerContainer to match_parent, hide extra content.
     * - Exit: show system bars, restore header height from dimens, show extra content.
     */
    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            // Hide system UI for immersive fullscreen
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            // Expand player to full height and hide scroll content
            headerContainer.layoutParams = headerContainer.layoutParams.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            headerContainer.requestLayout()
            extraContentScroll.visibility = View.GONE
        } else {
            // Show system UI
            window.insetsController?.show(WindowInsets.Type.systemBars())

            // Restore player height and show scroll content
            headerContainer.layoutParams = headerContainer.layoutParams.apply {
                height = defaultHeaderHeightPx
            }
            headerContainer.requestLayout()
            extraContentScroll.visibility = View.VISIBLE
        }
    }

    /**
     * PUBLIC_INTERFACE
     * Stub settings handler; replace with real settings screen or dialog when available.
     */
    private fun onSettingsClicked() {
        Toast.makeText(this, getString(R.string.player_settings), Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Settings clicked (stub).")
    }

    /**
     * PUBLIC_INTERFACE
     * Fetches the content details to populate UI.
     */
    private fun fetchInfo(id: String) {
        progressBar.visibility = View.VISIBLE

        val url = "$baseUrl/api/info/$id"
        Log.d(TAG, "Fetching info: $url")
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Info fetch failed", e)
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    // Keep existing title from extras; description remains empty
                    Toast.makeText(this@ContentInfoActivity, "Failed to load info.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful || bodyString.isBlank()) {
                    Log.e(TAG, "Info fetch error: code=${response.code}, bodyEmpty=${bodyString.isBlank()}")
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@ContentInfoActivity, "Failed to load info.", Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                try {
                    val obj = JSONObject(bodyString)
                    val title = obj.optString("title")
                    val description = obj.optString("description")

                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        if (!title.isNullOrBlank()) {
                            titleText.text = title
                        }
                        if (!description.isNullOrBlank()) {
                            descriptionText.text = description
                        }
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Info parse error", ex)
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@ContentInfoActivity, "Failed to parse info.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    /**
     * PUBLIC_INTERFACE
     * Handles the "Watch now" action: calls /api/play, parses the media URL, and starts playback.
     */
    private fun onWatchNowClicked() {
        // Provide immediate UI feedback
        Toast.makeText(this, getString(R.string.watch_now), Toast.LENGTH_SHORT).show()

        // Disable the button briefly and show progress
        watchNowButton.isEnabled = false
        progressBar.visibility = View.VISIBLE

        val url = "$baseUrl/api/play"
        Log.d(TAG, "Requesting playback URL: $url")
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Playback API call failed", e)
                runOnUiThread {
                    watchNowButton.isEnabled = true
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@ContentInfoActivity,
                        "Unable to start playback. Check your network.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful || bodyString.isBlank()) {
                    Log.e(TAG, "Playback API error: code=${response.code}, bodyEmpty=${bodyString.isBlank()}")
                    runOnUiThread {
                        watchNowButton.isEnabled = true
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@ContentInfoActivity, "Playback API error.", Toast.LENGTH_LONG).show()
                    }
                    return
                }
                try {
                    val obj = JSONObject(bodyString)
                    val mediaUrl = obj.optString("url", "").trim()

                    if (mediaUrl.isBlank()) {
                        Log.e(TAG, "Playback response missing 'url' field: $bodyString")
                        runOnUiThread {
                            watchNowButton.isEnabled = true
                            progressBar.visibility = View.GONE
                            Toast.makeText(this@ContentInfoActivity, "Missing playback URL.", Toast.LENGTH_LONG).show()
                        }
                        return
                    }

                    Log.d(TAG, "Received playback URL: $mediaUrl")
                    runOnUiThread {
                        startPlayback(mediaUrl)
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Playback response parse error", ex)
                    runOnUiThread {
                        watchNowButton.isEnabled = true
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@ContentInfoActivity, "Invalid playback response.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    /**
     * PUBLIC_INTERFACE
     * Initializes ExoPlayer if needed, sets the media item, prepares, and starts playback.
     *
     * @param mediaUrl The URL string to play.
     */
    private fun startPlayback(mediaUrl: String) {
        try {
            val parsed = Uri.parse(mediaUrl)
            if (parsed.scheme.isNullOrBlank()) {
                Log.e(TAG, "Invalid playback URL (no scheme): $mediaUrl")
                watchNowButton.isEnabled = true
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Invalid playback URL.", Toast.LENGTH_LONG).show()
                return
            }

            // Lazily create player with robust error listener
            val player = exoPlayer ?: ExoPlayer.Builder(this).build().also { created ->
                exoPlayer = created
                created.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "ExoPlayer error: ${error.errorCodeName}", error)
                        Toast.makeText(
                            this@ContentInfoActivity,
                            "Playback error: ${error.errorCodeName}",
                            Toast.LENGTH_LONG
                        ).show()
                        progressBar.visibility = View.GONE
                        watchNowButton.isEnabled = true
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                Log.d(TAG, "Player buffering...")
                                progressBar.visibility = View.VISIBLE
                            }
                            Player.STATE_READY -> {
                                Log.d(TAG, "Player ready.")
                                progressBar.visibility = View.GONE
                            }
                            Player.STATE_ENDED -> {
                                Log.d(TAG, "Playback ended.")
                            }
                            Player.STATE_IDLE -> {
                                Log.d(TAG, "Player idle.")
                            }
                        }
                    }
                })
            }

            // Attach player to view and show player
            if (playerView.player !== player) {
                playerView.player = player
            }
            playerView.visibility = View.VISIBLE

            // Ensure controller buttons are wired
            wirePlayerControls()

            // Hide poster and overlays so video is visible
            posterImage.visibility = View.GONE
            gradientOverlay.visibility = View.GONE
            overlayContent.visibility = View.GONE

            // Prepare media item and start playback
            val item = MediaItem.fromUri(parsed)
            player.setMediaItem(item, /* startPositionMs = */ 0)
            player.prepare()
            player.playWhenReady = true
            player.play() // ensure playback starts immediately

            // Keep UI responsive
            watchNowButton.isEnabled = true
            progressBar.visibility = View.GONE

            // Informative toast (non-intrusive)
            Toast.makeText(this, "Starting playback…", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Playback started for URL: $mediaUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start playback", e)
            watchNowButton.isEnabled = true
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Failed to start playback.", Toast.LENGTH_LONG).show()
        }
    }

    // PUBLIC_INTERFACE
    override fun onStop() {
        /** Pause playback when leaving screen; keep player to allow resume in onStart if needed. */
        super.onStop()
        exoPlayer?.playWhenReady = false
        exoPlayer?.pause()
        Log.d(TAG, "onStop: player paused")
    }

    // PUBLIC_INTERFACE
    override fun onDestroy() {
        /** Release player resources when Activity is destroyed. */
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
        playerView.player = null
        Log.d(TAG, "onDestroy: player released")
    }
}
