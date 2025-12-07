package com.app.streamly

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load

/**
 * PUBLIC_INTERFACE
 * PosterAdapter binds a list of [PosterItem] into a horizontal row of portrait cards.
 *
 * Each item uses a rounded-corner MaterialCard with centerCrop images, ensuring a consistent size
 * across rails with the same corner radius as the hero banner.
 */
class PosterAdapter(
    private var items: List<PosterItem> = emptyList(),
    private val onItemClick: ((PosterItem) -> Unit)? = null
) : RecyclerView.Adapter<PosterAdapter.PosterViewHolder>() {

    /** ViewHolder for poster item. */
    class PosterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.posterImage)
    }

    // PUBLIC_INTERFACE
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PosterViewHolder {
        /** Inflate the poster card item. */
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_poster, parent, false)
        return PosterViewHolder(view)
    }

    // PUBLIC_INTERFACE
    override fun onBindViewHolder(holder: PosterViewHolder, position: Int) {
        /** Bind each portrait poster using Coil with placeholder and error fallbacks. */
        val item = items[position]
        holder.image.load(item.poster) {
            crossfade(true)
            placeholder(R.drawable.poster_placeholder)
            error(R.drawable.poster_placeholder)
        }
        // Accessibility: set description to the item's name
        holder.image.contentDescription = item.name

        // Handle clicks: navigate to ContentInfoActivity via host-provided callback
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(item)
        }
    }

    // PUBLIC_INTERFACE
    override fun getItemCount(): Int = items.size

    /**
     * PUBLIC_INTERFACE
     * Update the list of poster items and refresh the rail.
     *
     * @param newItems The new list of [PosterItem] items.
     */
    fun updateData(newItems: List<PosterItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}

/**
 * PUBLIC_INTERFACE
 * Data model representing a single portrait poster item.
 *
 * @property id Content identifier.
 * @property name Display name for the item.
 * @property poster Full URL to a poster image.
 */
data class PosterItem(
    val id: String,
    val name: String,
    val poster: String
)
