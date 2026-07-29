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
 * 一组已栅格化的多层外阴影，以及相对节点左上角的绘制偏移。
 * Rasterized multi-layer drop shadows plus their draw offset from the node's top-left corner.
 */
data class RasterizedShadow(
    val bitmap: Bitmap,
    val drawOffsetXPx: Float,
    val drawOffsetYPx: Float,
)

/**
 * 静态阴影缓存诊断快照。
 * Diagnostic snapshot for the static shadow cache.
 */
data class ShadowRasterCacheStats(
    val hits: Long,
    val misses: Long,
    val evictions: Long,
    val oversizedSkips: Long,
    val cachedBytes: Int,
)

/**
 * 将精确多层阴影栅格化到可复用 Bitmap。
 * Rasterizes exact multi-layer shadows into reusable bitmaps.
 *
 * 该类只在规格、shape 或尺寸变化时生成 Bitmap；节点平移和普通重绘必须复用结果。
 * This class creates a bitmap only when the spec, shape, or size changes. Translation and ordinary
 * redraws must reuse the cached result.
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
     * 解析或创建一个静态多层阴影。无尺寸、无阴影或超过单次栅格预算时返回 null。
     * Resolves or creates one static multi-layer shadow. Returns null for empty bounds/specs or when
     * the raster would exceed the per-entry budget.
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

    companion object {
        const val DefaultMaxCacheBytes: Int = 8 * 1024 * 1024
        const val DefaultMaxRasterBytes: Int = 32 * 1024 * 1024
        private const val BytesPerPixel: Long = 4L
        private const val MaxBitmapDimension: Long = 8192L
        private const val BlurOutsetMultiplier: Float = 2f
    }
}

private object ShadowShapePathFactory {
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
