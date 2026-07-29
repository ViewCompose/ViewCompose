package com.viewcompose.shadow.android

import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import kotlin.math.roundToInt

/**
 * 将节点阴影规格连接到父 ViewGroup 绘制顺序的轻量 Decoration Layer。
 * Lightweight decoration layer connecting node shadow specs to the parent ViewGroup draw order.
 *
 * 节点本身不增加 wrapper View；支持该协议的父容器应在 super.drawChild 之前调用
 * [drawBehindChild]。这样阴影与对应 child 使用完全相同的 sibling/z 排序。
 * The node does not gain a wrapper View. Participating parents call [drawBehindChild] before
 * super.drawChild, preserving the exact sibling/z ordering used by the corresponding child.
 */
object ShadowDecorationLayer {
    private val rasterizer = ShadowBitmapRasterizer()
    private val bitmapPaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG,
    )

    /**
     * 更新节点的不可变阴影规格。空规格会移除旧阴影。
     * Updates the node's immutable shadow spec. An empty spec removes the previous shadow.
     *
     * @return true when the effective spec changed.
     */
    fun update(
        view: View,
        spec: ResolvedShadowSpec,
    ): Boolean {
        val normalized = spec.takeUnless { it.groups.isEmpty() }
        val previous = view.getTag(R.id.viewcompose_shadow_spec) as? ResolvedShadowSpec
        if (previous == normalized) return false
        view.setTag(R.id.viewcompose_shadow_spec, normalized)
        view.invalidate()
        (view.parent as? View)?.invalidate()
        return true
    }

    /**
     * 返回节点当前安装的阴影规格，供诊断和宿主集成使用。
     * Returns the shadow spec currently installed on a node for diagnostics and host integration.
     */
    fun specOrNull(view: View): ResolvedShadowSpec? {
        return view.getTag(R.id.viewcompose_shadow_spec) as? ResolvedShadowSpec
    }

    /**
     * 在 child 内容之前绘制其缓存阴影。
     * Draws the child's cached shadow before the child content.
     *
     * child.matrix 已包含 translation/scale/rotation/pivot，因此阴影可直接跟随属性动画，
     * 而无需重建缓存。
     * child.matrix already contains translation/scale/rotation/pivot, so property animations move
     * the shadow without rebuilding its cached bitmap.
     */
    fun drawBehindChild(
        canvas: Canvas,
        parent: ViewGroup,
        child: View,
    ) {
        val spec = specOrNull(child) ?: return
        if (child.width <= 0 || child.height <= 0 || child.alpha <= 0f) return
        val raster = rasterizer.rasterize(
            widthPx = child.width,
            heightPx = child.height,
            layoutDirection = child.layoutDirection,
            spec = spec,
        ) ?: return

        val saveCount = canvas.save()
        canvas.translate(
            (child.left - parent.scrollX).toFloat(),
            (child.top - parent.scrollY).toFloat(),
        )
        if (!child.matrix.isIdentity) {
            canvas.concat(child.matrix)
        }
        bitmapPaint.alpha = (child.alpha * 255f)
            .roundToInt()
            .coerceIn(0, 255)
        canvas.drawBitmap(
            raster.bitmap,
            raster.drawOffsetXPx,
            raster.drawOffsetYPx,
            bitmapPaint,
        )
        canvas.restoreToCount(saveCount)
    }

    /**
     * 返回进程内有界阴影缓存诊断。
     * Returns diagnostics for the process-wide bounded shadow cache.
     */
    fun cacheStats(): ShadowRasterCacheStats = rasterizer.stats()

    /**
     * 清空静态阴影缓存；主要供内存压力处理与测试使用。
     * Clears static shadow rasters, primarily for memory pressure handling and tests.
     */
    fun clearCache() {
        rasterizer.clear()
    }
}
