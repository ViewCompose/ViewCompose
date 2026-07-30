package com.viewcompose.shadow.android

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RenderNode
import android.os.Build
import android.util.LruCache

/**
 * 高级阴影绘制后端策略。
 * Rendering policy for advanced shadows.
 *
 * [Auto] 在真机基准得出稳定结论前保持精确 Bitmap 路径；试验只能显式选择
 * [RenderNodeDisplayList]，避免未经验证的设备差异进入默认行为。
 * [Auto] remains on the exact bitmap path until device benchmarks provide stable evidence.
 * Experiments must explicitly select [RenderNodeDisplayList].
 */
enum class ShadowRenderPolicy(
    val wireValue: String,
) {
    Auto("auto"),
    ExactBitmap("exact_bitmap"),
    RenderNodeDisplayList("render_node"),
    ;

    companion object {
        fun fromWireValue(value: String?): ShadowRenderPolicy {
            return entries.firstOrNull { it.wireValue == value } ?: Auto
        }
    }
}

/** 实际执行一次阴影绘制的后端。 / Backend used for one shadow draw. */
enum class ShadowRenderBackend {
    Bitmap,
    RenderNodeDisplayList,
}

/** 后端选择原因，供性能报告和 Demo 诊断展示。 / Backend selection reason for diagnostics. */
enum class ShadowRenderDecisionReason {
    ExplicitExactBitmap,
    AutoExactPendingEvidence,
    ExplicitRenderNode,
    RenderNodeApiUnavailable,
    SoftwareCanvas,
    RenderNodeFailure,
}

/** 一次后端选择结果。 / One backend selection decision. */
data class ShadowRenderBackendDecision(
    val backend: ShadowRenderBackend,
    val reason: ShadowRenderDecisionReason,
)

/** 阴影后端的进程内诊断快照。 / Process-wide shadow backend diagnostics. */
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

/**
 * 纯后端选择器；把 API 和硬件 Canvas 条件显式化，便于稳定测试。
 * Pure backend selector with explicit API and hardware-canvas inputs.
 */
object ShadowRenderBackendSelector {
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

    fun stats(): RenderNodeShadowCacheStats
}

/**
 * 把稳定阴影 Bitmap 录制为 RenderNode display list。
 * Records stable shadow bitmaps into RenderNode display lists.
 *
 * 缓存按 Bitmap 实例和实际字节数限制；节点位移、缩放、旋转与透明度仍由外层 Canvas/
 * RenderNode 属性更新，不会重新录制 display list。
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
