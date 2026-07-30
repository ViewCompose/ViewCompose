package com.viewcompose.shadow.android

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.LruCache
import com.viewcompose.ui.unit.UiDp
import kotlin.math.abs
import kotlin.math.max

/**
 * 已栅格化的前景内阴影。Bitmap 与节点内容尺寸完全一致，不参与布局或溢出计算。
 * Rasterized foreground inner shadow. The bitmap exactly matches content bounds and never affects
 * layout or overflow.
 */
data class RasterizedInnerShadow(
    val bitmap: Bitmap,
)

/**
 * 将单层/多层内阴影栅格化到有界缓存。
 * Rasterizes single- and multi-layer inner shadows into a bounded cache.
 *
 * 实现先构造“无限外部区域减去偏移后内部 shape”的反向 mask，再把结果裁到原始 shape。
 * This builds an inverse mask (outer region minus the shifted inner shape) and clips the result to
 * the original shape, producing a true inset shadow without wrapper Views or offscreen child layers.
 */
class InnerShadowBitmapRasterizer(
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

    private val cache = object : LruCache<InnerShadowBitmapCacheKey, RasterizedInnerShadow>(
        maxCacheBytes,
    ) {
        override fun sizeOf(
            key: InnerShadowBitmapCacheKey,
            value: RasterizedInnerShadow,
        ): Int = value.bitmap.allocationByteCount

        override fun entryRemoved(
            evicted: Boolean,
            key: InnerShadowBitmapCacheKey,
            oldValue: RasterizedInnerShadow,
            newValue: RasterizedInnerShadow?,
        ) {
            if (evicted) {
                evictions += 1
            }
        }
    }

    fun rasterize(
        widthPx: Int,
        heightPx: Int,
        layoutDirection: Int,
        spec: ResolvedInnerShadowSpec,
    ): RasterizedInnerShadow? {
        if (widthPx <= 0 || heightPx <= 0 || spec.groups.isEmpty()) return null
        val key = InnerShadowBitmapCacheKey(
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
        val requiredBytes = widthPx.toLong() * heightPx * BytesPerPixel
        if (
            widthPx > MaxBitmapDimension ||
            heightPx > MaxBitmapDimension ||
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
        )
        if (requiredBytes <= cache.maxSize()) {
            cache.put(key, rasterized)
        }
        return rasterized
    }

    fun clear() {
        cache.evictAll()
    }

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
        spec: ResolvedInnerShadowSpec,
    ): RasterizedInnerShadow {
        val bitmap = Bitmap.createBitmap(
            widthPx,
            heightPx,
            Bitmap.Config.ARGB_8888,
        )
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        val contentBounds = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        spec.groups.forEach { group ->
            val originalShape = ShadowShapePathFactory.create(
                shape = group.shape,
                bounds = contentBounds,
                layoutDirection = layoutDirection,
                density = spec.density,
            )
            val saveCount = canvas.save()
            canvas.clipPath(originalShape)
            group.shadows.forEach { shadow ->
                paint.color = shadow.color
                paint.maskFilter = if (shadow.blurRadiusPx > 0f) {
                    BlurMaskFilter(shadow.blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
                } else {
                    null
                }
                canvas.drawPath(
                    createInverseMask(
                        widthPx = widthPx,
                        heightPx = heightPx,
                        layoutDirection = layoutDirection,
                        spec = spec,
                        group = group,
                        shadow = shadow,
                    ),
                    paint,
                )
            }
            canvas.restoreToCount(saveCount)
        }
        paint.maskFilter = null
        return RasterizedInnerShadow(bitmap)
    }

    private fun createInverseMask(
        widthPx: Int,
        heightPx: Int,
        layoutDirection: Int,
        spec: ResolvedInnerShadowSpec,
        group: ResolvedShadowGroup,
        shadow: ResolvedShadowLayer,
    ): Path {
        val spread = shadow.spreadRadiusPx
        val holeBounds = RectF(
            shadow.offsetXPx + spread,
            shadow.offsetYPx + spread,
            widthPx + shadow.offsetXPx - spread,
            heightPx + shadow.offsetYPx - spread,
        )
        val outerPadding = max(widthPx, heightPx).toFloat() +
            shadow.blurRadiusPx * BlurOutsetMultiplier +
            abs(shadow.offsetXPx) +
            abs(shadow.offsetYPx) +
            abs(spread)
        return Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(
                -outerPadding,
                -outerPadding,
                widthPx + outerPadding,
                heightPx + outerPadding,
                Path.Direction.CW,
            )
            if (holeBounds.width() > 0f && holeBounds.height() > 0f) {
                val insetShape = group.shape.inset(
                    UiDp(max(0f, spread) / spec.density.density),
                )
                addPath(
                    ShadowShapePathFactory.create(
                        shape = insetShape,
                        bounds = holeBounds,
                        layoutDirection = layoutDirection,
                        density = spec.density,
                    ),
                )
            }
        }
    }

    private data class InnerShadowBitmapCacheKey(
        val widthPx: Int,
        val heightPx: Int,
        val layoutDirection: Int,
        val spec: ResolvedInnerShadowSpec,
    )

    companion object {
        const val DefaultMaxCacheBytes: Int = 8 * 1024 * 1024
        const val DefaultMaxRasterBytes: Int = 32 * 1024 * 1024
        private const val BytesPerPixel: Long = 4L
        private const val MaxBitmapDimension: Int = 8192
        private const val BlurOutsetMultiplier: Float = 2f
    }
}
