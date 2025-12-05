package org.example.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * PUBLIC_INTERFACE
 * MainActivity hosts the home screen with a top navigation bar and a hero banner carousel.
 *
 * - On load, fetches GET https://4b746313.api.kavia.app/api/banner
 * - Parses a JSON object with "banners": ["URL1", ...]
 * - Displays a horizontal carousel of banner cards (rounded corners, centerCrop)
 * - Preserves #090808 background and Figtree font via app theme
 * - Handles network errors with a retry option and a silent placeholder on failure
 */
class MainActivity : Activity() {

    private lateinit var bannerRecycler: RecyclerView
    private lateinit var bannerAdapter: BannerAdapter
    private lateinit var bannerProgress: ProgressBar
    private lateinit var errorContainer: View
    private lateinit var retryButton: Button

    private lateinit var navSearch: TextView
    private lateinit var navHome: TextView
    private lateinit var navMovies: TextView
    private lateinit var navTvShows: TextView

    private val httpClient by lazy { OkHttpClient() }

    // PUBLIC_INTERFACE
    override fun onCreate(savedInstanceState: Bundle?) {
        /** Set up UI with nav bar and banner carousel; kick off banner fetch. */
        super.onCreate(savedInstanceState)

        // Ensure window background is our dark color to prevent flashes
        window.setBackgroundDrawableResource(R.color.streamly_black)
        setContentView(R.layout.activity_main)

        // Also ensure root view background is dark
        findViewById<View>(R.id.rootMain)?.setBackgroundResource(R.color.streamly_black)

        // Nav bar setup
        navSearch = findViewById(R.id.navSearch)
        navHome = findViewById(R.id.navHome)
        navMovies = findViewById(R.id.navMovies)
        navTvShows = findViewById(R.id.navTvShows)
        setActiveNavHome()

        val navClickListener = View.OnClickListener { v ->
            when (v.id) {
                R.id.navSearch -> {
                    setNavColors(active = navSearch)
                    Toast.makeText(this, getString(R.string.nav_search), Toast.LENGTH_SHORT).show()
                }
                R.id.navHome -> {
                    setNavColors(active = navHome)
                    // Already on home; no-op
                }
                R.id.navMovies -> {
                    setNavColors(active = navMovies)
                    Toast.makeText(this, getString(R.string.nav_movies), Toast.LENGTH_SHORT).show()
                }
                R.id.navTvShows -> {
                    setNavColors(active = navTvShows)
                    Toast.makeText(this, getString(R.string.nav_tv_shows), Toast.LENGTH_SHORT).show()
                }
            }
        }
        navSearch.setOnClickListener(navClickListener)
        navHome.setOnClickListener(navClickListener)
        navMovies.setOnClickListener(navClickListener)
        navTvShows.setOnClickListener(navClickListener)

        // Views for loading and error states
        bannerProgress = findViewById(R.id.bannerProgress)
        errorContainer = findViewById(R.id.errorContainer)
        retryButton = findViewById(R.id.retryButton)
        retryButton.setOnClickListener {
            fetchBanners()
        }

        // Recycler setup
        bannerRecycler = findViewById(R.id.bannerRecycler)
        bannerRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        bannerAdapter = BannerAdapter(emptyList())
        bannerRecycler.adapter = bannerAdapter

        val spacing = resources.getDimensionPixelSize(R.dimen.space_16)
        bannerRecycler.addItemDecoration(HorizontalSpaceItemDecoration(spacing))
        bannerRecycler.setHasFixedSize(true)

        // Fetch banners
        fetchBanners()
    }

    private fun setActiveNavHome() {
        setNavColors(active = navHome)
    }

    private fun setNavColors(active: TextView) {
        val activeColor = resources.getColor(R.color.streamly_on_surface)
        val inactiveColor = resources.getColor(R.color.streamly_secondary)
        navSearch.setTextColor(if (active === navSearch) activeColor else inactiveColor)
        navHome.setTextColor(if (active === navHome) activeColor else inactiveColor)
        navMovies.setTextColor(if (active === navMovies) activeColor else inactiveColor)
        navTvShows.setTextColor(if (active === navTvShows) activeColor else inactiveColor)
    }

    private fun showLoading(show: Boolean) {
        bannerProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(show: Boolean) {
        errorContainer.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setBanners(urls: List<String>) {
        bannerAdapter.updateData(urls)
    }

    private fun parseBanners(json: String): List<String> {
        return try {
            val root = JSONObject(json)
            val arr: JSONArray = root.optJSONArray("banners") ?: JSONArray()
            val urls = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val url = arr.optString(i)
                if (!url.isNullOrBlank()) {
                    urls.add(url)
                }
            }
            urls
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchBanners() {
        showLoading(true)
        showError(false)

        val request = Request.Builder()
            .url("https://4b746313.api.kavia.app/api/banner")
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    // Graceful handling: show retry or silently leave placeholder items hidden
                    showError(true)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful || bodyString.isBlank()) {
                    runOnUiThread {
                        showLoading(false)
                        showError(true)
                    }
                    return
                }
                val urls = parseBanners(bodyString)
                runOnUiThread {
                    showLoading(false)
                    if (urls.isEmpty()) {
                        // Silent fail: show error container to allow retry, but keep UI clean otherwise
                        showError(true)
                    } else {
                        showError(false)
                        setBanners(urls)
                    }
                }
            }
        })
    }
}
