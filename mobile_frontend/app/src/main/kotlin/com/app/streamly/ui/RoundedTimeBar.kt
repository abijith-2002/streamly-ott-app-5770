package com.app.streamly.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.exoplayer2.ui.TimeBar
import com.app.streamly.R
import kotlin.math.max
import kotlin.math.min

/**
 * PUBLIC_INTERFACE
 * RoundedTimeBar is a custom ExoPlayer time bar with a Material 3–styled rounded track.
 *
 * It extends DefaultTimeBar to preserve all built-in accessibility and scrubbing behavior,
 * but overrides drawing to render a pill-shaped track for:
 * - Unplayed (base)
 * - Buffered (secondary)
 * - Played (primary)
 *
 * Integration:
 * - Use this view in controller layout with id @id/exo_progress so StyledPlayerControlView
 *   binds to it automatically.
 * - It respects app:bar_height and app:touch_target_height attributes indirectly via dimens.
 * - The default scrubber is suppressed and a custom thumb is drawn. The thumb radius is now
 *   a fixed dimension so reducing the track thickness does NOT change the thumb size.
 */
class RoundedTimeBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : DefaultTimeBar(context, attrs, defStyleAttr) {

    private val rect = RectF()

    // Colors - Material-ish styling based on app theme (white on dark surface)
    private val baseColor = ContextCompat.getColor(context, R.color.streamly_on_surface)
    private val playedColor = baseColor
    private val bufferedColor = withAlpha(baseColor, 0x99) // ~60% alpha
    private val unplayedColor = withAlpha(baseColor, 0x66) // ~40% alpha

    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = playedColor
    }
    private val bufferedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = bufferedColor
    }
    private val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = unplayedColor
    }
    // Custom thumb paint (same color as played to appear connected)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = playedColor
    }

    private var durationMs: Long = 0L
    private var positionMs: Long = 0L
    private var bufferedPositionMs: Long = 0L

    // Sizing
    private val preferredBarHeightPx: Float =
        resources.getDimension(R.dimen.timebar_height)
    private val cornerRadiusDefaultPx: Float =
        resources.getDimension(R.dimen.timebar_corner_radius)
    // Fixed thumb radius so it remains the same even if bar height changes
    private val thumbRadiusPx: Float =
        resources.getDimension(R.dimen.timebar_thumb_radius)

    init {
        // Make DefaultTimeBar track transparent so only ad markers draw from parent
        setPlayedColor(Color.TRANSPARENT)
        setBufferedColor(Color.TRANSPARENT)
        setUnplayedColor(Color.TRANSPARENT)

        // Suppress DefaultTimeBar's scrubber: we will draw our own
        setScrubberColor(Color.TRANSPARENT)

        // Keep our bar in sync while scrubbing (before the control view pushes setPosition updates)
        addListener(object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                positionMs = position
                invalidate()
            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) {
                positionMs = position
                invalidate()
            }

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                positionMs = position
                invalidate()
            }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Ensure the time bar never runs its own lingering animations.
        // The activity animates the unified controller group; the bar should not animate independently.
        animate().cancel()
        alpha = 1f
    }

    override fun onDetachedFromWindow() {
        // Cancel any ongoing animations to avoid leaking animators.
        animate().cancel()
        super.onDetachedFromWindow()
    }

    // PUBLIC_INTERFACE
    override fun setDuration(durationMs: Long) {
        /** Update duration and refresh custom drawing. */
        super.setDuration(durationMs)
        this.durationMs = max(0L, durationMs)
        invalidate()
    }

    // PUBLIC_INTERFACE
    override fun setPosition(position: Long) {
        /** Update position and refresh custom drawing. */
        super.setPosition(position)
        this.positionMs = max(0L, position)
        invalidate()
    }

    // PUBLIC_INTERFACE
    override fun setBufferedPosition(bufferedPosition: Long) {
        /** Update buffered position and refresh custom drawing. */
        super.setBufferedPosition(bufferedPosition)
        this.bufferedPositionMs = max(0L, bufferedPosition)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        // Draw custom rounded track with custom thumb
        drawRoundedTrackAndThumb(canvas)
        // Let DefaultTimeBar draw ad markers, etc. (track/scrubber are transparent)
        super.onDraw(canvas)
    }

    /**
     * Draws the full rounded track (unplayed, buffered, played) and a custom thumb.
     * The thumb has a fixed radius independent of the bar height to preserve its size
     * when the track thickness changes.
     */
    private fun drawRoundedTrackAndThumb(canvas: Canvas) {
        val pl = paddingLeft.toFloat()
        val pr = paddingRight.toFloat()
        val pt = paddingTop.toFloat()
        val pb = paddingBottom.toFloat()

        val totalW = (width - paddingLeft - paddingRight).toFloat().coerceAtLeast(0f)
        val totalH = (height - paddingTop - paddingBottom).toFloat().coerceAtLeast(0f)
        if (totalW <= 0f || totalH <= 0f) return

        // Center the track vertically inside the touch target height
        val barHeight = min(preferredBarHeightPx, totalH)
        val centerY = pt + totalH / 2f
        val top = centerY - barHeight / 2f
        val bottom = centerY + barHeight / 2f
        val left = pl
        val right = pl + totalW
        val trackRadius = min(cornerRadiusDefaultPx, barHeight / 2f)

        // Base: unplayed full track
        rect.set(left, top, right, bottom)
        canvas.drawRoundRect(rect, trackRadius, trackRadius, unplayedPaint)

        if (durationMs <= 0L) {
            // No duration known yet; nothing more to draw
            return
        }

        val bufferedFrac = bufferedPositionMs.toSafeFrac(durationMs)
        val playedFrac = positionMs.toSafeFrac(durationMs)

        // Clip to rounded track while drawing segments
        val clipPath = Path().apply {
            addRoundRect(rect, trackRadius, trackRadius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)

        // Buffered segment
        if (bufferedFrac > 0f) {
            val bufferedRight = left + totalW * bufferedFrac
            rect.set(left, top, bufferedRight.coerceIn(left, right), bottom)
            canvas.drawRoundRect(rect, trackRadius, trackRadius, bufferedPaint)
        }

        // Played segment
        if (playedFrac > 0f) {
            val playedRight = left + totalW * playedFrac
            rect.set(left, top, playedRight.coerceIn(left, right), bottom)
            canvas.drawRoundRect(rect, trackRadius, trackRadius, playedPaint)
        }

        // Done drawing segments
        canvas.restore()

        // Custom thumb: center exactly at the end of the played segment.
        // Draw OUTSIDE the track clip so it can be larger than the bar and remain the same size
        // even when the bar thickness changes.
        val thumbCenterX = (left + totalW * playedFrac).coerceIn(left, right)
        val thumbCenterY = centerY
        canvas.drawCircle(thumbCenterX, thumbCenterY, thumbRadiusPx, thumbPaint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        val a = (alpha and 0xFF) shl 24
        return a or (color and 0x00FFFFFF)
    }

    private fun Long.toSafeFrac(denom: Long): Float {
        if (denom <= 0L) return 0f
        val f = this.toDouble() / denom.toDouble()
        return f.coerceIn(0.0, 1.0).toFloat()
    }
}
