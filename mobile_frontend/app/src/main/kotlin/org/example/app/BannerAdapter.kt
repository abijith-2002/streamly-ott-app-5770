package org.example.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load

/**
 * PUBLIC_INTERFACE
 * BannerAdapter binds a list of banner image URLs into a horizontal carousel.
 *
 * Each item is a rounded-corner card sized for ~705x397 aspect ratio, with images center-cropped.
 */
class BannerAdapter(
    private var items: List<String> = emptyList()
) : RecyclerView.Adapter<BannerAdapter.BannerViewHolder>() {

    /** ViewHolder for banner item. */
    class BannerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.bannerImage)
    }

    // PUBLIC_INTERFACE
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        /** Inflate the banner card item. */
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_banner, parent, false)
        return BannerViewHolder(view)
    }

    // PUBLIC_INTERFACE
    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        /** Bind each banner image using Coil with placeholder and error fallbacks. */
        val url = items[position]
        holder.image.load(url) {
            crossfade(true)
            placeholder(R.drawable.banner_placeholder)
            error(R.drawable.banner_placeholder)
        }
    }

    // PUBLIC_INTERFACE
    override fun getItemCount(): Int = items.size

    /**
     * PUBLIC_INTERFACE
     * Update the list of banner URLs and refresh the carousel.
     *
     * @param newItems The new list of banner image URLs.
     */
    fun updateData(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged()
    }
}
