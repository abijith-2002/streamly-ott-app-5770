package org.example.app

import org.apache.commons.text.WordUtils
import org.example.list.LinkedList
import org.example.utilities.SplitUtils
import org.example.utilities.StringUtils

import android.widget.TextView
import android.os.Bundle
import android.app.Activity
import android.view.View

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure window background is our dark color to prevent flashes
        window.setBackgroundDrawableResource(R.color.streamly_black)
        setContentView(R.layout.activity_main)

        // Also ensure root view background is dark
        findViewById<View>(R.id.rootMain)?.setBackgroundResource(R.color.streamly_black)

        val textView = findViewById(R.id.textView) as TextView
        textView.text = buildMessage()
    }

    private fun buildMessage(): String {
        val tokens: LinkedList = SplitUtils.split(MessageUtils.message())
        val result: String = StringUtils.join(tokens)
        return WordUtils.capitalize(result)
    }
}
