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
 * 节点本身不增加 wrapper View；支持该协议的父容器应分别在 super.drawChild 前后调用
 * [drawBehindChild] 与 [drawOverChild]。这样外阴影、节点内容和内阴影使用同一 sibling/z 排序。
 * The node does not gain a wrapper View. Participating parents call [drawBehindChild] before and
 * [drawOverChild] after super.drawChild, preserving one sibling/z order across outer shadow,
 * child content, and inner shadow.
 */
object ShadowDecorationLayer {
    private val rasterizer = ShadowBitmapRasterizer()
    private val innerRasterizer = InnerShadowBitmapRasterizer()
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
     * 更新节点的不可变前景内阴影规格。空规格会移除旧内阴影。
     * Updates the node's immutable foreground inner-shadow spec. An empty spec removes it.
     */
    fun updateInner(
        view: View,
        spec: ResolvedInnerShadowSpec,
    ): Boolean {
        val normalized = spec.takeUnless { it.groups.isEmpty() }
        val previous = view.getTag(R.id.viewcompose_inner_shadow_spec) as? ResolvedInnerShadowSpec
        if (previous == normalized) return false
        view.setTag(R.id.viewcompose_inner_shadow_spec, normalized)
        view.invalidate()
        (view.parent as? View)?.invalidate()
        return true
    }

    /**
     * 返回节点当前安装的内阴影规格。
     * Returns the inner-shadow spec currently installed on a node.
     */
    fun innerSpecOrNull(view: View): ResolvedInnerShadowSpec? {
        return view.getTag(R.id.viewcompose_inner_shadow_spec) as? ResolvedInnerShadowSpec
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
     * 在 child 完成自身背景、内容、子树和 foreground 后绘制缓存内阴影。
     * Draws the cached inner shadow after the child has drawn its background, content, subtree, and
     * foreground. This is a visual-only foreground plane and does not affect input dispatch.
     */
    fun drawOverChild(
        canvas: Canvas,
        parent: ViewGroup,
        child: View,
    ) {
        val spec = innerSpecOrNull(child) ?: return
        if (child.width <= 0 || child.height <= 0 || child.alpha <= 0f) return
        val raster = innerRasterizer.rasterize(
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
            0f,
            0f,
            bitmapPaint,
        )
        canvas.restoreToCount(saveCount)
    }

    /**
     * 返回进程内有界阴影缓存诊断。
     * Returns diagnostics for the process-wide bounded shadow cache.
     */
    fun cacheStats(): ShadowRasterCacheStats = rasterizer.stats()

    /** Returns diagnostics for the process-wide bounded inner-shadow cache. */
    fun innerCacheStats(): ShadowRasterCacheStats = innerRasterizer.stats()

    /**
     * 清空静态阴影缓存；主要供内存压力处理与测试使用。
     * Clears static shadow rasters, primarily for memory pressure handling and tests.
     */
    fun clearCache() {
        rasterizer.clear()
        innerRasterizer.clear()
    }
}
