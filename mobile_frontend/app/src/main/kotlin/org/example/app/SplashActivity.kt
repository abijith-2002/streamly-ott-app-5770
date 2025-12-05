package org.example.app

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Window
import android.view.WindowManager
import org.example.app.R

/**
 * PUBLIC_INTERFACE
 * SplashActivity displays a splash screen with a #090808 background and Figtree typography.
 * It waits approximately 3 seconds (or until initialization completes) before navigating to Home (MainActivity).
 */
class SplashActivity : Activity() {

    private val splashDurationMs: Long = 3000L
    private val handler = Handler(Looper.getMainLooper())
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Request to show no title and ensure window background is our dark color to avoid flashes
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)

        // For devices pre-Android 12 we use a content layout
        setContentView(R.layout.activity_splash)

        // Optional: Set flags to draw edge to edge with dark bars
        window.statusBarColor = resources.getColor(R.color.streamly_black)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = resources.getColor(R.color.streamly_black)
        }

        // Simulate init and navigate to main after ~3s
        scheduleStart()
    }

    private fun scheduleStart() {
        if (started) return
        started = true
        handler.postDelayed({
            startMain()
        }, splashDurationMs)
    }

    private fun startMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        // Fade transition for smoothness
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
