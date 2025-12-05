package org.example.app

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
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
 * - Shows the poster image full-width at the top.
 * - Overlays a bottom gradient that blends into #090808 for readability.
 * - Fetches GET https://4b746313.api.kavia.app/api/info/{id} to load {title, description}.
 * - Renders title/description over the gradient with Figtree font.
 * - Provides a pill-shaped "Watch now" button (#C60A0A).
 */
class ContentInfoActivity : Activity() {

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_POSTER = "extra_poster"
    }

    private val httpClient by lazy { OkHttpClient() }
    private val baseUrl = "https://4b746313.api.kavia.app"

    private lateinit var posterImage: ImageView
    private lateinit var titleText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var watchNowButton: Button
    private lateinit var progressBar: ProgressBar

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

        // Hook up watch button (placeholder)
        watchNowButton.setOnClickListener {
            Toast.makeText(this, getString(R.string.watch_now), Toast.LENGTH_SHORT).show()
        }

        if (id.isNotBlank()) {
            fetchInfo(id)
        }
    }

    private fun fetchInfo(id: String) {
        progressBar.visibility = View.VISIBLE

        val url = "$baseUrl/api/info/$id"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    // Keep existing title from extras; description remains empty
                    // Show a lightweight toast for feedback; screen is still usable.
                    Toast.makeText(this@ContentInfoActivity, "Failed to load info.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful || bodyString.isBlank()) {
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
                } catch (_: Exception) {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@ContentInfoActivity, "Failed to parse info.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
