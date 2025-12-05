package org.example.app

import android.app.Activity
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
 * MainActivity hosts the home screen with a top navigation bar, a hero banner carousel,
 * and several horizontal content rails under the banner.
 *
 * - On load, fetches GET https://4b746313.api.kavia.app/api/banner
 * - Parses a JSON object with "banners": ["URL1", ...]
 * - Displays a horizontal carousel of banner cards (rounded corners, centerCrop)
 * - Adds rails for Trending, Continue Watching, Action, Drama, Horror, Family, Comedy
 *   fetching from corresponding API endpoints that return an array of {id, name, poster}
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

    // Rail containers to control visibility after data load
    private lateinit var containerTrending: View
    private lateinit var containerContinueWatching: View
    private lateinit var containerAction: View
    private lateinit var containerDrama: View
    private lateinit var containerHorror: View
    private lateinit var containerFamily: View
    private lateinit var containerComedy: View

    // Rail RecyclerViews
    private lateinit var recyclerTrending: RecyclerView
    private lateinit var recyclerContinueWatching: RecyclerView
    private lateinit var recyclerAction: RecyclerView
    private lateinit var recyclerDrama: RecyclerView
    private lateinit var recyclerHorror: RecyclerView
    private lateinit var recyclerFamily: RecyclerView
    private lateinit var recyclerComedy: RecyclerView

    // Rail adapters
    private lateinit var adapterTrending: PosterAdapter
    private lateinit var adapterContinueWatching: PosterAdapter
    private lateinit var adapterAction: PosterAdapter
    private lateinit var adapterDrama: PosterAdapter
    private lateinit var adapterHorror: PosterAdapter
    private lateinit var adapterFamily: PosterAdapter
    private lateinit var adapterComedy: PosterAdapter

    private val httpClient by lazy { OkHttpClient() }
    private val baseUrl = "https://4b746313.api.kavia.app"

    // PUBLIC_INTERFACE
    override fun onCreate(savedInstanceState: Bundle?) {
        /** Set up UI with nav bar, banner carousel, and content rails; kick off network fetches. */
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

        // Hero banner recycler setup
        bannerRecycler = findViewById(R.id.bannerRecycler)
        bannerRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        bannerAdapter = BannerAdapter(emptyList())
        bannerRecycler.adapter = bannerAdapter

        val spacing = resources.getDimensionPixelSize(R.dimen.space_16)
        bannerRecycler.addItemDecoration(HorizontalSpaceItemDecoration(spacing))
        bannerRecycler.setHasFixedSize(true)

        // Rails setup (containers and recyclers)
        containerTrending = findViewById(R.id.containerTrending)
        containerContinueWatching = findViewById(R.id.containerContinueWatching)
        containerAction = findViewById(R.id.containerAction)
        containerDrama = findViewById(R.id.containerDrama)
        containerHorror = findViewById(R.id.containerHorror)
        containerFamily = findViewById(R.id.containerFamily)
        containerComedy = findViewById(R.id.containerComedy)

        recyclerTrending = findViewById(R.id.recyclerTrending)
        recyclerContinueWatching = findViewById(R.id.recyclerContinueWatching)
        recyclerAction = findViewById(R.id.recyclerAction)
        recyclerDrama = findViewById(R.id.recyclerDrama)
        recyclerHorror = findViewById(R.id.recyclerHorror)
        recyclerFamily = findViewById(R.id.recyclerFamily)
        recyclerComedy = findViewById(R.id.recyclerComedy)

        adapterTrending = setupPosterRail(recyclerTrending, spacing)
        adapterContinueWatching = setupPosterRail(recyclerContinueWatching, spacing)
        adapterAction = setupPosterRail(recyclerAction, spacing)
        adapterDrama = setupPosterRail(recyclerDrama, spacing)
        adapterHorror = setupPosterRail(recyclerHorror, spacing)
        adapterFamily = setupPosterRail(recyclerFamily, spacing)
        adapterComedy = setupPosterRail(recyclerComedy, spacing)

        // Hide rails until data is available
        containerTrending.visibility = View.GONE
        containerContinueWatching.visibility = View.GONE
        containerAction.visibility = View.GONE
        containerDrama.visibility = View.GONE
        containerHorror.visibility = View.GONE
        containerFamily.visibility = View.GONE
        containerComedy.visibility = View.GONE

        // Fetch banners and rails
        fetchBanners()
        fetchAllRails()
    }

    private fun setupPosterRail(recyclerView: RecyclerView, spacingPx: Int): PosterAdapter {
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        val adapter = PosterAdapter(emptyList())
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(HorizontalSpaceItemDecoration(spacingPx))
        recyclerView.setHasFixedSize(true)
        return adapter
    }

    private fun fetchAllRails() {
        // Each category tries one or more endpoints (fallbacks handle dash/underscore variants when applicable).
        fetchRail(paths = listOf("$baseUrl/api/trending"), adapterTrending, containerTrending)
        fetchRail(paths = listOf("$baseUrl/api/continue-watching", "$baseUrl/api/continue_watching"), adapterContinueWatching, containerContinueWatching)
        fetchRail(paths = listOf("$baseUrl/api/action"), adapterAction, containerAction)
        fetchRail(paths = listOf("$baseUrl/api/drama"), adapterDrama, containerDrama)
        fetchRail(paths = listOf("$baseUrl/api/horror"), adapterHorror, containerHorror)
        fetchRail(paths = listOf("$baseUrl/api/family"), adapterFamily, containerFamily)
        fetchRail(paths = listOf("$baseUrl/api/comedy"), adapterComedy, containerComedy)
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
            .url("$baseUrl/api/banner")
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

    // ------------------------
    // Rails: Networking & Parsing
    // ------------------------

    private fun fetchRail(paths: List<String>, adapter: PosterAdapter, container: View) {
        attemptFetchRail(paths, 0,
            onSuccess = { items ->
                runOnUiThread {
                    if (items.isNotEmpty()) {
                        adapter.updateData(items)
                        container.visibility = View.VISIBLE
                    } else {
                        container.visibility = View.GONE
                    }
                }
            },
            onFailure = {
                runOnUiThread {
                    container.visibility = View.GONE
                }
            }
        )
    }

    private fun attemptFetchRail(
        paths: List<String>,
        index: Int,
        onSuccess: (List<PosterItem>) -> Unit,
        onFailure: () -> Unit
    ) {
        if (index >= paths.size) {
            onFailure()
            return
        }
        val request = Request.Builder().url(paths[index]).get().build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Try next fallback path
                attemptFetchRail(paths, index + 1, onSuccess, onFailure)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) {
                    // Try next fallback path
                    attemptFetchRail(paths, index + 1, onSuccess, onFailure)
                    return
                }
                val items = parsePosters(body)
                onSuccess(items)
            }
        })
    }

    private fun parsePosters(json: String): List<PosterItem> {
        return try {
            // Endpoints return an array of {id, name, poster}; be resilient to object-wrapped arrays.
            val array: JSONArray? = when {
                json.trim().startsWith("[") -> JSONArray(json)
                else -> {
                    val root = JSONObject(json)
                    root.optJSONArray("items")
                        ?: root.optJSONArray("results")
                        ?: root.optJSONArray("data")
                }
            }
            val arr = array ?: JSONArray()
            val list = mutableListOf<PosterItem>()
            for (i in 0 until arr.length()) {
                val obj: JSONObject = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val name = obj.optString("name")
                val poster = obj.optString("poster")
                if (poster.isNullOrBlank()) continue
                list.add(PosterItem(id = id, name = name, poster = poster))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
