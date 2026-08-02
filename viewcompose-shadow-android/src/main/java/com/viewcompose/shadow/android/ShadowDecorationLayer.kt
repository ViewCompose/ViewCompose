package com.viewcompose.shadow.android

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.viewcompose.renderer.decoration.AndroidViewDecorationRuntime
import java.util.EnumMap
import kotlin.math.roundToInt

/**
 * Connects per-View shadow specifications to the renderer's parent drawing planes.
 *
 * Installing this backend does not add wrapper Views. A participating renderer parent invokes
 * [drawBehindChild] before and [drawOverChild] after `super.drawChild`, preserving the child's
 * sibling/z ordering. Mutable state and caches are process-wide and UI-thread confined unless a
 * member explicitly states otherwise.
 */
object ShadowDecorationLayer {
    private val rendererBackend: ShadowViewDecorationBackend by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ShadowViewDecorationBackend()
    }
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
     * Installs this optional module as the renderer's process-wide View decoration backend.
     *
     * Installation replaces a previously installed backend for future updates. Call it during
     * application initialization before rendering decorated content. Packaging this artifact also
     * enables one-shot ServiceLoader discovery, so explicit installation is optional.
     */
    fun install() {
        AndroidViewDecorationRuntime.install(rendererBackend)
    }

    /**
     * Sets the process-wide replay policy for subsequent shadow draws.
     *
     * Existing rasters remain cached. The volatile policy can be selected from any thread, but callers
     * should normally configure it during application initialization.
     *
     * @param policy replay policy to publish
     * @return `true` when the effective policy changed
     */
    fun setRenderPolicy(policy: ShadowRenderPolicy): Boolean {
        if (renderPolicy == policy) return false
        renderPolicy = policy
        return true
    }

    /** Returns the current process-wide replay policy as a live volatile value. */
    fun renderPolicy(): ShadowRenderPolicy = renderPolicy

    /**
     * Replaces the drop-shadow specification stored on [view].
     *
     * An empty specification removes the tag. A changed value invalidates the View and its current
     * parent; equality-identical updates do nothing. Call on the UI thread.
     *
     * @param view mounted View receiving backend-owned tag state
     * @param spec complete replacement drop-shadow specification
     * @return `true` when stored state changed
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
     * Returns the drop-shadow specification currently tagged on [view].
     *
     * @param view View to inspect
     * @return stored immutable specification, or `null` when none is installed
     */
    fun specOrNull(view: View): ResolvedShadowSpec? {
        return view.getTag(R.id.viewcompose_shadow_spec) as? ResolvedShadowSpec
    }

    /**
     * Replaces the foreground inner-shadow specification stored on [view].
     *
     * An empty specification removes the tag. A changed value invalidates the View and its current
     * parent; equality-identical updates do nothing. Call on the UI thread.
     *
     * @param view mounted View receiving backend-owned tag state
     * @param spec complete replacement inner-shadow specification
     * @return `true` when stored state changed
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
     * Returns the inner-shadow specification currently tagged on [view].
     *
     * @param view View to inspect
     * @return stored immutable specification, or `null` when none is installed
     */
    fun innerSpecOrNull(view: View): ResolvedInnerShadowSpec? {
        return view.getTag(R.id.viewcompose_inner_shadow_spec) as? ResolvedInnerShadowSpec
    }

    /**
     * Draws [child]'s cached drop shadow immediately before its normal content.
     *
     * The child matrix supplies translation, scale, rotation, and pivot transforms without rerasterizing.
     * Missing specs, non-positive bounds, transparent children, and rejected rasters produce no draw.
     * Call only from the direct parent's UI-thread drawing pass.
     *
     * @param canvas active canvas in [parent] coordinates
     * @param parent direct parent dispatching child drawing
     * @param child direct decorated child
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
     * Draws [child]'s cached inner shadow after its background, content, subtree, and foreground.
     *
     * The visual-only plane follows the child matrix and does not affect layout or input dispatch.
     * Missing specs, non-positive bounds, transparent children, and rejected rasters produce no draw.
     * Call only from the direct parent's UI-thread drawing pass.
     *
     * @param canvas active canvas in [parent] coordinates
     * @param parent direct parent dispatching child drawing
     * @param child direct decorated child
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

    /** Returns current process-wide drop-shadow raster-cache diagnostics. */
    fun cacheStats(): ShadowRasterCacheStats = rasterizer.stats()

    /** Returns current process-wide inner-shadow raster-cache diagnostics. */
    fun innerCacheStats(): ShadowRasterCacheStats = innerRasterizer.stats()

    /** Returns a snapshot of current replay policy, draw counts, decisions, and display-list cache. */
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

    /** Clears replay counters and the last decision while retaining policy and all cached rasters. */
    fun resetBackendDiagnostics() {
        bitmapDraws = 0
        renderNodeDraws = 0
        renderNodeRenderer?.resetDiagnostics()
        decisionsByReason.clear()
        lastDecision = null
    }

    /**
     * Evicts drop-shadow, inner-shadow, and display-list caches.
     *
     * Use this UI-thread operation for explicit memory-pressure handling or tests. Cumulative raster
     * diagnostics are retained; replay diagnostics are also unchanged.
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
