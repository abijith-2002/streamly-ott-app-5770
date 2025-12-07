package com.app.streamly

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * PUBLIC_INTERFACE
 * HorizontalSpaceItemDecoration adds a fixed space between horizontally-scrolling items.
 *
 * @param spacePx Space in pixels to apply between items and at the edges.
 */
class HorizontalSpaceItemDecoration(private val spacePx: Int) : RecyclerView.ItemDecoration() {

    // PUBLIC_INTERFACE
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        /** Adds spacing to left/right so that adjacent items have [spacePx] between them. */
        val position = parent.getChildAdapterPosition(view)
        val itemCount = parent.adapter?.itemCount ?: 0

        outRect.top = 0
        outRect.bottom = 0

        // Space on left of first item
        outRect.left = if (position == 0) spacePx else 0
        // Space between items and on right of last item
        outRect.right = if (position == itemCount - 1) spacePx else spacePx
    }
}
