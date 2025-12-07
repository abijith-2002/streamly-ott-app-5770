package org.example.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import coil.load
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
 * - Shows the poster image full-width at the top with gradient overlay for readability.
 * - Fetches GET https://4b746313.api.kavia.app/api/info/{id} to load {title, description}.
 * - Renders title/description over the gradient with Figtree font.
 * - Provides a pill-shaped "Watch now" button (#C60A0A).
 * - On "Watch now": GET /api/play, parse {"url": "<mediaUrl>"}, and open PlayerActivity for playback.
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

    // Overlay elements (kept; no inline player)
    private lateinit var gradientOverlay: View
    private lateinit var overlayContent: View

    // Container and scroll (kept for layout)
    private lateinit var extraContentScroll: ScrollView

    // Extras
    private var posterUrl: String = ""
    private var displayName: String = ""

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
        extraContentScroll = findViewById(R.id.extraContentScroll)

        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        displayName = intent.getStringExtra(EXTRA_NAME).orEmpty()
        posterUrl = intent.getStringExtra(EXTRA_POSTER).orEmpty()

        // Load poster immediately
        posterImage.load(posterUrl) {
            crossfade(true)
            placeholder(R.drawable.poster_placeholder)
            error(R.drawable.poster_placeholder)
        }

        // Set initial title from rail (replaced by server title if available)
        titleText.text = displayName
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
     * Handles the "Watch now" action: calls /api/play, parses the media URL, and launches PlayerActivity.
     */
    private fun onWatchNowClicked() {
        // Provide immediate UI feedback
        Toast.makeText(this, getString(R.string.watch_now), Toast.LENGTH_SHORT).show()

        // Disable the button briefly and show progress
        watchNowButton.isEnabled = false
        progressBar.visibility = View.VISIBLE

        val url = "$baseUrl/api/play"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
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

                    runOnUiThread {
                        watchNowButton.isEnabled = true
                        progressBar.visibility = View.GONE
                        if (mediaUrl.isBlank()) {
                            Toast.makeText(this@ContentInfoActivity, "Missing playback URL.", Toast.LENGTH_LONG).show()
                            return@runOnUiThread
                        }
                        // Launch PlayerActivity
                        val intent = PlayerActivity.newIntent(
                            context = this@ContentInfoActivity,
                            mediaUrl = mediaUrl,
                            title = titleText.text?.toString(),
                            poster = posterUrl
                        )
                        startActivity(intent)
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }
                } catch (_: Exception) {
                    runOnUiThread {
                        watchNowButton.isEnabled = true
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@ContentInfoActivity, "Invalid playback response.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
}
