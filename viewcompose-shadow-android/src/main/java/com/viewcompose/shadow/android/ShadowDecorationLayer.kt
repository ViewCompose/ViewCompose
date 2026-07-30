package com.viewcompose.shadow.android

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import java.util.EnumMap
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
    private var renderNodeRenderer: ShadowDisplayListRenderer? = null
    @Volatile
    private var renderPolicy: ShadowRenderPolicy = ShadowRenderPolicy.Auto
    private var bitmapDraws: Long = 0
    private var renderNodeDraws: Long = 0
    private val decisionsByReason = EnumMap<ShadowRenderDecisionReason, Long>(
        ShadowRenderDecisionReason::class.java,
    )
    private var lastDecision: ShadowRenderBackendDecision? = null

    /**
     * 设置后续阴影绘制策略。默认 Auto 在基准结论落地前仍使用精确 Bitmap。
     * Sets the policy for subsequent shadow draws. Auto remains exact-bitmap until benchmarked.
     *
     * @return true when the policy changed.
     */
    fun setRenderPolicy(policy: ShadowRenderPolicy): Boolean {
        if (renderPolicy == policy) return false
        renderPolicy = policy
        return true
    }

    fun renderPolicy(): ShadowRenderPolicy = renderPolicy

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
        canvas.translate(
            raster.drawOffsetXPx,
            raster.drawOffsetYPx,
        )
        drawRaster(
            canvas = canvas,
            bitmap = raster.bitmap,
            alpha = child.alpha,
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
        drawRaster(
            canvas = canvas,
            bitmap = raster.bitmap,
            alpha = child.alpha,
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

    /** 返回当前后端策略、实际绘制次数和 display-list 缓存统计。 */
    fun backendStats(): ShadowRenderBackendStats {
        val renderNodeStats = renderNodeRenderer?.stats()
        return ShadowRenderBackendStats(
            policy = renderPolicy,
            bitmapDraws = bitmapDraws,
            renderNodeDraws = renderNodeDraws,
            renderNodeRecordings = renderNodeStats?.recordings ?: 0,
            renderNodeCacheHits = renderNodeStats?.hits ?: 0,
            renderNodeCacheEvictions = renderNodeStats?.evictions ?: 0,
            renderNodeCachedBytes = renderNodeStats?.cachedBytes ?: 0,
            decisionsByReason = decisionsByReason.toMap(),
            lastDecision = lastDecision,
        )
    }

    /** 清空后端计数但保留当前策略和缓存。 / Clears backend counters while retaining policy/cache. */
    fun resetBackendDiagnostics() {
        bitmapDraws = 0
        renderNodeDraws = 0
        renderNodeRenderer?.resetDiagnostics()
        decisionsByReason.clear()
        lastDecision = null
    }

    /**
     * 清空静态阴影缓存；主要供内存压力处理与测试使用。
     * Clears static shadow rasters, primarily for memory pressure handling and tests.
     */
    fun clearCache() {
        rasterizer.clear()
        innerRasterizer.clear()
        renderNodeRenderer?.clear()
    }

    private fun drawRaster(
        canvas: Canvas,
        bitmap: Bitmap,
        alpha: Float,
    ) {
        var decision = ShadowRenderBackendSelector.select(
            policy = renderPolicy,
            sdkInt = Build.VERSION.SDK_INT,
            hardwareAccelerated = canvas.isHardwareAccelerated,
        )
        if (decision.backend == ShadowRenderBackend.RenderNodeDisplayList) {
            try {
                drawRenderNode(
                    canvas = canvas,
                    bitmap = bitmap,
                    alpha = alpha,
                )
                renderNodeDraws += 1
                recordDecision(decision)
                return
            } catch (_: RuntimeException) {
                decision = ShadowRenderBackendDecision(
                    backend = ShadowRenderBackend.Bitmap,
                    reason = ShadowRenderDecisionReason.RenderNodeFailure,
                )
            }
        }
        bitmapPaint.alpha = (alpha * 255f)
            .roundToInt()
            .coerceIn(0, 255)
        canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)
        bitmapDraws += 1
        recordDecision(decision)
    }

    private fun recordDecision(decision: ShadowRenderBackendDecision) {
        if (
            lastDecision != decision &&
            renderPolicy != ShadowRenderPolicy.Auto
        ) {
            Log.i(
                BackendLogTag,
                "policy=${renderPolicy.wireValue} backend=${decision.backend} reason=${decision.reason}",
            )
        }
        lastDecision = decision
        decisionsByReason[decision.reason] = decisionsByReason.getOrDefault(
            decision.reason,
            0L,
        ) + 1
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun drawRenderNode(
        canvas: Canvas,
        bitmap: Bitmap,
        alpha: Float,
    ) {
        val renderer = renderNodeRenderer ?: RenderNodeShadowRenderer().also {
            renderNodeRenderer = it
        }
        renderer.draw(
            canvas = canvas,
            bitmap = bitmap,
            alpha = alpha,
        )
    }

    private const val BackendLogTag: String = "ViewComposeShadow"
}
