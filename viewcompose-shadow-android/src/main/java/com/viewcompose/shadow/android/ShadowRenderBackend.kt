package com.viewcompose.shadow.android

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RenderNode
import android.os.Build
import android.util.LruCache

/**
 * Selects how a previously rasterized shadow bitmap is replayed during a `ViewGroup` draw.
 *
 * [Auto] and [ExactBitmap] currently use direct bitmap drawing. [RenderNodeDisplayList] requests an
 * Android 10+ hardware-accelerated display-list cache and falls back when unavailable. This policy
 * does not change how the shadow bitmap itself is rasterized.
 *
 * @property wireValue stable configuration value accepted by [fromWireValue]
 */
enum class ShadowRenderPolicy(
    val wireValue: String,
) {
    /** Chooses the evidence-backed default, currently direct exact-bitmap replay. */
    Auto("auto"),
    /** Always replays the raster directly with `Canvas.drawBitmap`. */
    ExactBitmap("exact_bitmap"),
    /** Requests a cached `RenderNode` display list when API and canvas capabilities allow it. */
    RenderNodeDisplayList("render_node"),
    ;

    /** Converts persisted configuration values into policies. */
    companion object {
        /**
         * Returns the policy matching [value], falling back to [Auto] for `null` or unknown values.
         *
         * @param value serialized [wireValue]
         * @return the matching policy or [Auto]
         */
        fun fromWireValue(value: String?): ShadowRenderPolicy {
            return entries.firstOrNull { it.wireValue == value } ?: Auto
        }
    }
}

/** Identifies the backend that replays one already-rasterized shadow. */
enum class ShadowRenderBackend {
    /** Direct `Canvas.drawBitmap` replay on hardware or software canvases. */
    Bitmap,
    /** Android 10+ hardware `RenderNode` display-list replay. */
    RenderNodeDisplayList,
}

/** Explains why [ShadowRenderBackendSelector] chose a replay backend. */
enum class ShadowRenderDecisionReason {
    /** [ShadowRenderPolicy.ExactBitmap] explicitly selected direct replay. */
    ExplicitExactBitmap,
    /** [ShadowRenderPolicy.Auto] retained the current evidence-backed bitmap default. */
    AutoExactPendingEvidence,
    /** Explicit RenderNode policy met API and hardware-canvas requirements. */
    ExplicitRenderNode,
    /** RenderNode was requested below Android 10. */
    RenderNodeApiUnavailable,
    /** RenderNode was requested while drawing to a software canvas. */
    SoftwareCanvas,
    /** RenderNode replay threw and the draw fell back to direct bitmap replay. */
    RenderNodeFailure,
}

/**
 * Records the backend and reason selected for one shadow replay.
 *
 * @property backend replay implementation to use
 * @property reason capability or policy branch that selected [backend]
 */
data class ShadowRenderBackendDecision(
    val backend: ShadowRenderBackend,
    val reason: ShadowRenderDecisionReason,
)

/**
 * Captures process-wide replay diagnostics at one instant.
 *
 * Counts accumulate until [ShadowDecorationLayer.resetBackendDiagnostics] and are confined to the
 * renderer/UI thread except for the volatile policy read.
 *
 * @property policy policy active when the snapshot was created
 * @property bitmapDraws number of direct bitmap replays
 * @property renderNodeDraws number of successful RenderNode replays
 * @property renderNodeRecordings number of display lists recorded
 * @property renderNodeCacheHits successful bitmap-identity display-list lookups
 * @property renderNodeCacheEvictions display lists removed by the LRU byte budget
 * @property renderNodeCachedBytes current bitmap allocation bytes represented by cached display lists
 * @property decisionsByReason cumulative replay decisions grouped by reason
 * @property lastDecision most recent decision, or `null` before the first replay/reset
 */
data class ShadowRenderBackendStats(
    val policy: ShadowRenderPolicy,
    val bitmapDraws: Long,
    val renderNodeDraws: Long,
    val renderNodeRecordings: Long,
    val renderNodeCacheHits: Long,
    val renderNodeCacheEvictions: Long,
    val renderNodeCachedBytes: Int,
    val decisionsByReason: Map<ShadowRenderDecisionReason, Long>,
    val lastDecision: ShadowRenderBackendDecision?,
)

/** Selects a deterministic shadow replay backend from policy and platform capabilities. */
object ShadowRenderBackendSelector {
    /**
     * Returns the backend decision without reading global Android state.
     *
     * [ShadowRenderPolicy.Auto] and [ShadowRenderPolicy.ExactBitmap] always select bitmap replay.
     * Explicit RenderNode replay requires API 29 or newer and a hardware-accelerated canvas.
     *
     * @sample com.viewcompose.shadow.android.samples.selectShadowBackendSample
     * @param policy requested replay policy
     * @param sdkInt Android SDK level to evaluate
     * @param hardwareAccelerated whether the destination canvas is hardware accelerated
     * @return deterministic backend and selection reason
     */
    fun select(
        policy: ShadowRenderPolicy,
        sdkInt: Int,
        hardwareAccelerated: Boolean,
    ): ShadowRenderBackendDecision {
        return when (policy) {
            ShadowRenderPolicy.Auto -> ShadowRenderBackendDecision(
                backend = ShadowRenderBackend.Bitmap,
                reason = ShadowRenderDecisionReason.AutoExactPendingEvidence,
            )

            ShadowRenderPolicy.ExactBitmap -> ShadowRenderBackendDecision(
                backend = ShadowRenderBackend.Bitmap,
                reason = ShadowRenderDecisionReason.ExplicitExactBitmap,
            )

            ShadowRenderPolicy.RenderNodeDisplayList -> when {
                sdkInt < Build.VERSION_CODES.Q -> ShadowRenderBackendDecision(
                    backend = ShadowRenderBackend.Bitmap,
                    reason = ShadowRenderDecisionReason.RenderNodeApiUnavailable,
                )

                !hardwareAccelerated -> ShadowRenderBackendDecision(
                    backend = ShadowRenderBackend.Bitmap,
                    reason = ShadowRenderDecisionReason.SoftwareCanvas,
                )

                else -> ShadowRenderBackendDecision(
                    backend = ShadowRenderBackend.RenderNodeDisplayList,
                    reason = ShadowRenderDecisionReason.ExplicitRenderNode,
                )
            }
        }
    }
}

internal data class RenderNodeShadowCacheStats(
    val recordings: Long,
    val hits: Long,
    val evictions: Long,
    val cachedBytes: Int,
)

internal interface ShadowDisplayListRenderer {
    fun draw(
        canvas: Canvas,
        bitmap: Bitmap,
        alpha: Float,
    )

    fun clear()

    fun resetDiagnostics()

    fun stats(): RenderNodeShadowCacheStats
}

/**
 * Records stable shadow bitmaps into RenderNode display lists.
 *
 * The cache is bounded by bitmap identity and allocated bytes. Position, transforms, and alpha are
 * applied outside the recording and therefore do not rebuild the display list.
 */
@TargetApi(Build.VERSION_CODES.Q)
internal class RenderNodeShadowRenderer(
    maxCacheBytes: Int = DefaultMaxCacheBytes,
) : ShadowDisplayListRenderer {
    init {
        require(maxCacheBytes > 0) { "maxCacheBytes must be greater than zero." }
    }

    private var recordings: Long = 0
    private var hits: Long = 0
    private var evictions: Long = 0

    private val cache = object : LruCache<Bitmap, RenderNode>(maxCacheBytes) {
        override fun sizeOf(
            key: Bitmap,
            value: RenderNode,
        ): Int = key.allocationByteCount

        override fun entryRemoved(
            evicted: Boolean,
            key: Bitmap,
            oldValue: RenderNode,
            newValue: RenderNode?,
        ) {
            if (evicted) {
                evictions += 1
            }
        }
    }

    override fun draw(
        canvas: Canvas,
        bitmap: Bitmap,
        alpha: Float,
    ) {
        val node = cache.get(bitmap)?.also {
            hits += 1
        } ?: record(bitmap).also { recorded ->
            if (bitmap.allocationByteCount <= cache.maxSize()) {
                cache.put(bitmap, recorded)
            }
        }
        node.alpha = alpha.coerceIn(0f, 1f)
        canvas.drawRenderNode(node)
    }

    override fun clear() {
        cache.evictAll()
    }

    override fun resetDiagnostics() {
        recordings = 0
        hits = 0
        evictions = 0
    }

    override fun stats(): RenderNodeShadowCacheStats {
        return RenderNodeShadowCacheStats(
            recordings = recordings,
            hits = hits,
            evictions = evictions,
            cachedBytes = cache.size(),
        )
    }

    private fun record(bitmap: Bitmap): RenderNode {
        recordings += 1
        return RenderNode("ViewComposeShadow").apply {
            setPosition(0, 0, bitmap.width, bitmap.height)
            val recordingCanvas = beginRecording()
            recordingCanvas.drawBitmap(bitmap, 0f, 0f, RecordingPaint)
            endRecording()
        }
    }

    private companion object {
        const val DefaultMaxCacheBytes: Int = 8 * 1024 * 1024
        val RecordingPaint = Paint(
            Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG,
        )
    }
}
