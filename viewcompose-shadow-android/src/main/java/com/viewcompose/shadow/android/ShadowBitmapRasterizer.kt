package com.viewcompose.shadow.android

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.LruCache
import android.view.View
import com.viewcompose.ui.shape.UiCorner
import com.viewcompose.ui.shape.UiCornerFamily
import com.viewcompose.ui.shape.UiCornerSize
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDensity
import kotlin.math.ceil
import kotlin.math.max

/**
 * Owns a rasterized drop-shadow bitmap and its position relative to the decorated node.
 *
 * The bitmap may extend beyond node bounds. Ownership remains with the rasterizer cache; consumers
 * must not mutate or recycle it.
 *
 * @property bitmap ARGB bitmap containing transparent padding and all shadow layers
 * @property drawOffsetXPx horizontal bitmap origin relative to the node in physical pixels
 * @property drawOffsetYPx vertical bitmap origin relative to the node in physical pixels
 */
data class RasterizedShadow(
    val bitmap: Bitmap,
    val drawOffsetXPx: Float,
    val drawOffsetYPx: Float,
)

/**
 * Captures cumulative raster-cache diagnostics at one instant.
 *
 * Counters are not reset by cache eviction or [ShadowBitmapRasterizer.clear].
 *
 * @property hits successful equality-key cache lookups
 * @property misses lookups that required a rasterization attempt
 * @property evictions entries removed by the LRU byte budget
 * @property oversizedSkips attempts rejected by dimension or per-raster byte limits
 * @property cachedBytes current `Bitmap.allocationByteCount` total retained by the cache
 */
data class ShadowRasterCacheStats(
    val hits: Long,
    val misses: Long,
    val evictions: Long,
    val oversizedSkips: Long,
    val cachedBytes: Int,
)

/**
 * Rasterizes multi-layer drop shadows into byte-bounded reusable Android bitmaps.
 *
 * Cache identity includes content size, layout direction, and the complete resolved specification;
 * translation and ordinary redraw do not rebuild the bitmap. The instance is unsynchronized and is
 * intended for UI-thread use. Cached bitmaps are owned by this object and are not explicitly recycled.
 *
 * @param maxCacheBytes positive LRU allocation-byte budget shared by retained raster entries
 * @param maxRasterBytes positive upper bound for one attempted bitmap allocation
 * @throws IllegalArgumentException if either byte budget is not positive
 */
class ShadowBitmapRasterizer(
    maxCacheBytes: Int = DefaultMaxCacheBytes,
    private val maxRasterBytes: Int = DefaultMaxRasterBytes,
) {
    init {
        require(maxCacheBytes > 0) { "maxCacheBytes must be greater than zero." }
        require(maxRasterBytes > 0) { "maxRasterBytes must be greater than zero." }
    }

    private var hits: Long = 0
    private var misses: Long = 0
    private var evictions: Long = 0
    private var oversizedSkips: Long = 0

    private val cache = object : LruCache<ShadowBitmapCacheKey, RasterizedShadow>(maxCacheBytes) {
        override fun sizeOf(
            key: ShadowBitmapCacheKey,
            value: RasterizedShadow,
        ): Int = value.bitmap.allocationByteCount

        override fun entryRemoved(
            evicted: Boolean,
            key: ShadowBitmapCacheKey,
            oldValue: RasterizedShadow,
            newValue: RasterizedShadow?,
        ) {
            if (evicted) {
                evictions += 1
            }
        }
    }

    /**
     * Returns a cached drop-shadow raster or creates one synchronously on the calling thread.
     *
     * The method returns `null` without allocating for non-positive content bounds, an empty spec,
     * dimensions above 8192 pixels, or an allocation above the per-raster budget. A valid raster
     * larger than the configured cache budget is returned but not cached. Bitmap allocation failures
     * propagate.
     *
     * @sample com.viewcompose.shadow.android.samples.rasterizeShadowSample
     * @param widthPx decorated content width in physical pixels
     * @param heightPx decorated content height in physical pixels
     * @param layoutDirection Android `View.LAYOUT_DIRECTION_*` used for start/end corners
     * @param spec immutable resolved shadow specification used as part of the cache key
     * @return an owned cached/new raster, or `null` when drawing should be skipped
     */
    fun rasterize(
        widthPx: Int,
        heightPx: Int,
        layoutDirection: Int,
        spec: ResolvedShadowSpec,
    ): RasterizedShadow? {
        if (widthPx <= 0 || heightPx <= 0 || spec.groups.isEmpty()) return null
        val key = ShadowBitmapCacheKey(
            widthPx = widthPx,
            heightPx = heightPx,
            layoutDirection = layoutDirection,
            spec = spec,
        )
        cache.get(key)?.let { cached ->
            hits += 1
            return cached
        }
        misses += 1
        val outsets = calculateOutsets(spec)
        val bitmapWidth = widthPx.toLong() + outsets.left + outsets.right
        val bitmapHeight = heightPx.toLong() + outsets.top + outsets.bottom
        val requiredBytes = bitmapWidth * bitmapHeight * BytesPerPixel
        if (
            bitmapWidth <= 0L ||
            bitmapHeight <= 0L ||
            bitmapWidth > MaxBitmapDimension ||
            bitmapHeight > MaxBitmapDimension ||
            requiredBytes > maxRasterBytes
        ) {
            oversizedSkips += 1
            return null
        }
        val rasterized = createRaster(
            widthPx = widthPx,
            heightPx = heightPx,
            layoutDirection = layoutDirection,
            spec = spec,
            outsets = outsets,
            bitmapWidth = bitmapWidth.toInt(),
            bitmapHeight = bitmapHeight.toInt(),
        )
        if (requiredBytes <= cache.maxSize()) {
            cache.put(key, rasterized)
        }
        return rasterized
    }

    /** Evicts all retained drop-shadow bitmaps without resetting cumulative diagnostics. */
    fun clear() {
        cache.evictAll()
    }

    /** Returns a snapshot of cumulative counters and current retained bytes. */
    fun stats(): ShadowRasterCacheStats {
        return ShadowRasterCacheStats(
            hits = hits,
            misses = misses,
            evictions = evictions,
            oversizedSkips = oversizedSkips,
            cachedBytes = cache.size(),
        )
    }

    private fun createRaster(
        widthPx: Int,
        heightPx: Int,
        layoutDirection: Int,
        spec: ResolvedShadowSpec,
        outsets: ShadowOutsets,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ): RasterizedShadow {
        val bitmap = Bitmap.createBitmap(
            bitmapWidth,
            bitmapHeight,
            Bitmap.Config.ARGB_8888,
        )
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        spec.groups.forEach { group ->
            group.shadows.forEach { shadow ->
                val spread = shadow.spreadRadiusPx
                val bounds = RectF(
                    outsets.left + shadow.offsetXPx - spread,
                    outsets.top + shadow.offsetYPx - spread,
                    outsets.left + widthPx + shadow.offsetXPx + spread,
                    outsets.top + heightPx + shadow.offsetYPx + spread,
                )
                if (bounds.width() <= 0f || bounds.height() <= 0f) {
                    return@forEach
                }
                paint.color = shadow.color
                paint.maskFilter = if (shadow.blurRadiusPx > 0f) {
                    BlurMaskFilter(shadow.blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
                } else {
                    null
                }
                canvas.drawPath(
                    ShadowShapePathFactory.create(
                        shape = group.shape,
                        bounds = bounds,
                        layoutDirection = layoutDirection,
                        density = spec.density,
                    ),
                    paint,
                )
            }
        }
        paint.maskFilter = null
        return RasterizedShadow(
            bitmap = bitmap,
            drawOffsetXPx = -outsets.left.toFloat(),
            drawOffsetYPx = -outsets.top.toFloat(),
        )
    }

    private fun calculateOutsets(spec: ResolvedShadowSpec): ShadowOutsets {
        var left = 0f
        var top = 0f
        var right = 0f
        var bottom = 0f
        spec.groups.forEach { group ->
            group.shadows.forEach { shadow ->
                val blurOutset = shadow.blurRadiusPx * BlurOutsetMultiplier
                val spreadOutset = max(0f, shadow.spreadRadiusPx)
                val extent = blurOutset + spreadOutset
                left = max(left, extent - shadow.offsetXPx)
                right = max(right, extent + shadow.offsetXPx)
                top = max(top, extent - shadow.offsetYPx)
                bottom = max(bottom, extent + shadow.offsetYPx)
            }
        }
        return ShadowOutsets(
            left = ceil(left).toInt(),
            top = ceil(top).toInt(),
            right = ceil(right).toInt(),
            bottom = ceil(bottom).toInt(),
        )
    }

    private data class ShadowBitmapCacheKey(
        val widthPx: Int,
        val heightPx: Int,
        val layoutDirection: Int,
        val spec: ResolvedShadowSpec,
    )

    private data class ShadowOutsets(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    /** Defines default memory budgets for drop-shadow rasterization. */
    companion object {
        /** Default process-instance LRU budget: 8 MiB of bitmap allocation bytes. */
        const val DefaultMaxCacheBytes: Int = 8 * 1024 * 1024
        /** Default upper bound for one raster allocation: 32 MiB. */
        const val DefaultMaxRasterBytes: Int = 32 * 1024 * 1024
        private const val BytesPerPixel: Long = 4L
        private const val MaxBitmapDimension: Long = 8192L
        private const val BlurOutsetMultiplier: Float = 2f
    }
}

internal object ShadowShapePathFactory {
    fun create(
        shape: UiShape,
        bounds: RectF,
        layoutDirection: Int,
        density: UiDensity,
    ): Path {
        val physicalCorners = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            PhysicalCorners(
                topLeft = shape.topEnd,
                topRight = shape.topStart,
                bottomRight = shape.bottomStart,
                bottomLeft = shape.bottomEnd,
            )
        } else {
            PhysicalCorners(
                topLeft = shape.topStart,
                topRight = shape.topEnd,
                bottomRight = shape.bottomEnd,
                bottomLeft = shape.bottomStart,
            )
        }
        val maxCornerSize = max(0f, minOf(bounds.width(), bounds.height()) / 2f)
        val topLeft = physicalCorners.topLeft.resolvePx(bounds, density, maxCornerSize)
        val topRight = physicalCorners.topRight.resolvePx(bounds, density, maxCornerSize)
        val bottomRight = physicalCorners.bottomRight.resolvePx(bounds, density, maxCornerSize)
        val bottomLeft = physicalCorners.bottomLeft.resolvePx(bounds, density, maxCornerSize)
        return Path().apply {
            moveTo(bounds.left + topLeft, bounds.top)
            lineTo(bounds.right - topRight, bounds.top)
            addTopRightCorner(bounds, physicalCorners.topRight.family, topRight)
            lineTo(bounds.right, bounds.bottom - bottomRight)
            addBottomRightCorner(bounds, physicalCorners.bottomRight.family, bottomRight)
            lineTo(bounds.left + bottomLeft, bounds.bottom)
            addBottomLeftCorner(bounds, physicalCorners.bottomLeft.family, bottomLeft)
            lineTo(bounds.left, bounds.top + topLeft)
            addTopLeftCorner(bounds, physicalCorners.topLeft.family, topLeft)
            close()
        }
    }

    private fun UiCorner.resolvePx(
        bounds: RectF,
        density: UiDensity,
        maxCornerSize: Float,
    ): Float {
        val resolved = when (val cornerSize = size) {
            is UiCornerSize.Absolute -> density.toPx(cornerSize.size)
            is UiCornerSize.Relative -> minOf(bounds.width(), bounds.height()) * cornerSize.fraction
        }
        return resolved.coerceIn(0f, maxCornerSize)
    }

    private fun Path.addTopRightCorner(
        bounds: RectF,
        family: UiCornerFamily,
        size: Float,
    ) {
        if (size == 0f || family == UiCornerFamily.Cut) {
            lineTo(bounds.right, bounds.top + size)
        } else {
            arcTo(
                bounds.right - size * 2f,
                bounds.top,
                bounds.right,
                bounds.top + size * 2f,
                -90f,
                90f,
                false,
            )
        }
    }

    private fun Path.addBottomRightCorner(
        bounds: RectF,
        family: UiCornerFamily,
        size: Float,
    ) {
        if (size == 0f || family == UiCornerFamily.Cut) {
            lineTo(bounds.right - size, bounds.bottom)
        } else {
            arcTo(
                bounds.right - size * 2f,
                bounds.bottom - size * 2f,
                bounds.right,
                bounds.bottom,
                0f,
                90f,
                false,
            )
        }
    }

    private fun Path.addBottomLeftCorner(
        bounds: RectF,
        family: UiCornerFamily,
        size: Float,
    ) {
        if (size == 0f || family == UiCornerFamily.Cut) {
            lineTo(bounds.left, bounds.bottom - size)
        } else {
            arcTo(
                bounds.left,
                bounds.bottom - size * 2f,
                bounds.left + size * 2f,
                bounds.bottom,
                90f,
                90f,
                false,
            )
        }
    }

    private fun Path.addTopLeftCorner(
        bounds: RectF,
        family: UiCornerFamily,
        size: Float,
    ) {
        if (size == 0f || family == UiCornerFamily.Cut) {
            lineTo(bounds.left + size, bounds.top)
        } else {
            arcTo(
                bounds.left,
                bounds.top,
                bounds.left + size * 2f,
                bounds.top + size * 2f,
                180f,
                90f,
                false,
            )
        }
    }

    private data class PhysicalCorners(
        val topLeft: UiCorner,
        val topRight: UiCorner,
        val bottomRight: UiCorner,
        val bottomLeft: UiCorner,
    )
}
