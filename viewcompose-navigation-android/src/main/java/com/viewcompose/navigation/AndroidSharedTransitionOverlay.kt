package com.viewcompose.navigation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.viewcompose.ui.modifier.SharedContentModifierElement
import com.viewcompose.ui.shared.SHARED_CONTENT_TAG_KEY
import com.viewcompose.ui.shared.SharedContentKey
import com.viewcompose.ui.shared.SharedContentMode
import kotlin.math.roundToInt

/**
 * Owns bounded, non-interactive shared-content snapshots for one native navigation transition.
 *
 * Pair discovery and capture run once at pre-draw, after both destination roots have their final
 * pane layout. The overlay owns no destination session and releases every bitmap at any terminal
 * path.
 */
internal class AndroidSharedTransitionOverlay(
    private val host: NavHostView,
    private val outgoingRoots: List<View>,
    private val incomingRoots: List<View>,
) {
    private val pairs = mutableListOf<SharedSnapshotPair>()
    private var latestProgress = 0f
    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var terminal = false
    private var prepared = false

    init {
        if (hasPotentialPair()) {
            schedulePreparation()
        }
    }

    /** Applies the host's existing geometry progress to every prepared shared pair. */
    fun update(progress: Float) {
        if (terminal) return
        latestProgress = progress.coerceIn(0f, 1f)
        pairs.forEach { pair -> pair.apply(latestProgress) }
    }

    /** Releases snapshots and restores endpoint state, optionally transferring committed focus. */
    fun finish(committed: Boolean) {
        if (terminal) return
        terminal = true
        removePreDrawListener()
        pairs.forEach { pair ->
            pair.release(
                host = host,
                transferFocus = committed,
            )
        }
        pairs.clear()
    }

    internal val pairCount: Int
        get() = pairs.size

    private fun hasPotentialPair(): Boolean {
        val outgoingIdentities = linkedSetOf<Pair<SharedContentKey, SharedContentMode>>()
        outgoingRoots.distinct().forEach { root ->
            root.forEachDepthFirst { view ->
                val metadata = view.getTag(SHARED_CONTENT_TAG_KEY)
                    as? SharedContentModifierElement
                    ?: return@forEachDepthFirst
                outgoingIdentities += metadata.key to metadata.mode
            }
        }
        if (outgoingIdentities.isEmpty()) return false
        return incomingRoots.distinct().any { root ->
            root.anyDepthFirst { view ->
                val metadata = view.getTag(SHARED_CONTENT_TAG_KEY)
                    as? SharedContentModifierElement
                    ?: return@anyDepthFirst false
                metadata.key to metadata.mode in outgoingIdentities
            }
        }
    }

    private fun schedulePreparation() {
        if (
            outgoingRoots.isEmpty() ||
            incomingRoots.isEmpty() ||
            !host.isAttachedToWindow
        ) {
            return
        }
        val observer = host.viewTreeObserver
        if (!observer.isAlive) return
        val listener = ViewTreeObserver.OnPreDrawListener {
            removePreDrawListener()
            prepare()
            true
        }
        preDrawListener = listener
        observer.addOnPreDrawListener(listener)
        host.invalidate()
    }

    private fun prepare() {
        if (terminal || prepared) return
        prepared = true
        if (host.width <= 0 || host.height <= 0 || !host.isAttachedToWindow) return

        val outgoing = collectEndpoints(outgoingRoots)
        val incoming = collectEndpoints(incomingRoots)
        val snapshotPixelBudget = host.width.toLong() * host.height.toLong() *
            MAX_SNAPSHOT_HOST_AREA_MULTIPLIER
        var consumedPixels = 0L

        outgoing.order.forEach { key ->
            val sourceCandidates = outgoing.byKey[key].orEmpty()
            val targetCandidates = incoming.byKey[key].orEmpty()
            if (sourceCandidates.size != 1 || targetCandidates.size != 1) return@forEach
            val source = sourceCandidates.single()
            val target = targetCandidates.single()
            if (source.metadata.mode != target.metadata.mode) return@forEach

            val requiredPixels = source.view.width.toLong() * source.view.height.toLong() +
                if (source.metadata.mode == SharedContentMode.Bounds) {
                    target.view.width.toLong() * target.view.height.toLong()
                } else {
                    0L
                }
            if (
                requiredPixels <= 0L ||
                requiredPixels > snapshotPixelBudget - consumedPixels
            ) {
                return@forEach
            }
            val pair = capturePair(source, target) ?: return@forEach
            pairs += pair
            consumedPixels += requiredPixels
            pair.attach(host)
            pair.apply(latestProgress)
        }
    }

    private fun capturePair(
        source: SharedEndpoint,
        target: SharedEndpoint,
    ): SharedSnapshotPair? {
        if (!source.canCapture() || !target.canCapture()) return null
        val sourceBounds = source.boundsInHost() ?: return null
        val targetBounds = target.boundsInHost() ?: return null
        val sourceBitmap = source.view.captureBitmapOrNull() ?: return null
        val targetBitmap = if (source.metadata.mode == SharedContentMode.Bounds) {
            target.view.captureBitmapOrNull() ?: run {
                sourceBitmap.recycle()
                return null
            }
        } else {
            null
        }
        return SharedSnapshotPair(
            mode = source.metadata.mode,
            source = EndpointVisualState.capture(source.view),
            target = EndpointVisualState.capture(target.view),
            sourceBounds = sourceBounds,
            targetBounds = targetBounds,
            sourceDrawable = BitmapDrawable(host.resources, sourceBitmap).apply {
                isFilterBitmap = true
            },
            targetDrawable = targetBitmap?.let { bitmap ->
                BitmapDrawable(host.resources, bitmap).apply {
                    isFilterBitmap = true
                }
            },
        )
    }

    private fun collectEndpoints(roots: List<View>): SharedEndpointCollection {
        val byKey = linkedMapOf<SharedContentKey, MutableList<SharedEndpoint>>()
        val order = mutableListOf<SharedContentKey>()
        roots.distinct().forEach { root ->
            root.forEachDepthFirst { view ->
                val metadata = view.getTag(SHARED_CONTENT_TAG_KEY)
                    as? SharedContentModifierElement
                    ?: return@forEachDepthFirst
                if (metadata.key !in byKey) order += metadata.key
                byKey.getOrPut(metadata.key, ::mutableListOf) += SharedEndpoint(
                    root = root,
                    view = view,
                    metadata = metadata,
                )
            }
        }
        return SharedEndpointCollection(byKey, order)
    }

    private fun removePreDrawListener() {
        val listener = preDrawListener ?: return
        preDrawListener = null
        val observer = host.viewTreeObserver
        if (observer.isAlive) observer.removeOnPreDrawListener(listener)
    }

    private data class SharedEndpointCollection(
        val byKey: Map<SharedContentKey, List<SharedEndpoint>>,
        val order: List<SharedContentKey>,
    )

    private data class SharedEndpoint(
        val root: View,
        val view: View,
        val metadata: SharedContentModifierElement,
    ) {
        fun canCapture(): Boolean {
            return view.isAttachedToWindow &&
                view.visibility == View.VISIBLE &&
                view.width > 0 &&
                view.height > 0 &&
                !view.hasSurfaceBackedContent()
        }

        fun boundsInHost(): Rect? {
            val bounds = Rect(0, 0, view.width, view.height)
            if (view !== root) {
                runCatching {
                    (root as? ViewGroup)?.offsetDescendantRectToMyCoords(view, bounds)
                        ?: return null
                }.getOrElse { return null }
            }
            bounds.offset(root.left, root.top)
            return bounds.takeIf { it.width() > 0 && it.height() > 0 }
        }
    }

    private data class EndpointVisualState(
        val view: View,
        val alpha: Float,
        val visibility: Int,
        val hadFocus: Boolean,
    ) {
        fun suppressSource() {
            view.visibility = View.INVISIBLE
        }

        fun suppressTarget() {
            view.alpha = 0f
        }

        fun restore() {
            view.alpha = alpha
            view.visibility = visibility
        }

        companion object {
            fun capture(view: View): EndpointVisualState {
                return EndpointVisualState(
                    view = view,
                    alpha = view.alpha,
                    visibility = view.visibility,
                    hadFocus = view.hasFocus(),
                )
            }
        }
    }

    private class SharedSnapshotPair(
        private val mode: SharedContentMode,
        private val source: EndpointVisualState,
        private val target: EndpointVisualState,
        private val sourceBounds: Rect,
        private val targetBounds: Rect,
        private val sourceDrawable: BitmapDrawable,
        private val targetDrawable: BitmapDrawable?,
    ) {
        private var attached = false

        fun attach(host: NavHostView) {
            if (attached) return
            attached = true
            source.suppressSource()
            target.suppressTarget()
            host.overlay.add(sourceDrawable)
            targetDrawable?.let(host.overlay::add)
        }

        fun apply(progress: Float) {
            if (!attached) return
            source.suppressSource()
            target.suppressTarget()
            val fraction = progress.coerceIn(0f, 1f)
            val bounds = Rect(
                lerp(sourceBounds.left, targetBounds.left, fraction),
                lerp(sourceBounds.top, targetBounds.top, fraction),
                lerp(sourceBounds.right, targetBounds.right, fraction),
                lerp(sourceBounds.bottom, targetBounds.bottom, fraction),
            )
            sourceDrawable.bounds = bounds
            if (mode == SharedContentMode.Element) {
                sourceDrawable.alpha = source.alpha.toDrawableAlpha()
            } else {
                sourceDrawable.alpha = (source.alpha * (1f - fraction)).toDrawableAlpha()
                targetDrawable?.apply {
                    this.bounds = bounds
                    alpha = (target.alpha * fraction).toDrawableAlpha()
                }
            }
            sourceDrawable.invalidateSelf()
            targetDrawable?.invalidateSelf()
        }

        fun release(
            host: NavHostView,
            transferFocus: Boolean,
        ) {
            if (attached) {
                host.overlay.remove(sourceDrawable)
                targetDrawable?.let(host.overlay::remove)
                attached = false
            }
            source.restore()
            target.restore()
            sourceDrawable.bitmap?.takeIf { !it.isRecycled }?.recycle()
            targetDrawable?.bitmap?.takeIf { !it.isRecycled }?.recycle()
            if (
                transferFocus &&
                source.hadFocus &&
                (target.view.isFocusable || target.view.isFocusableInTouchMode)
            ) {
                target.view.requestFocus()
            } else if (
                !transferFocus &&
                source.hadFocus &&
                (source.view.isFocusable || source.view.isFocusableInTouchMode)
            ) {
                source.view.requestFocus()
            }
        }
    }

    private companion object {
        const val MAX_SNAPSHOT_HOST_AREA_MULTIPLIER: Long = 2L
    }
}

private fun View.forEachDepthFirst(block: (View) -> Unit) {
    block(this)
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).forEachDepthFirst(block)
        }
    }
}

private fun View.anyDepthFirst(predicate: (View) -> Boolean): Boolean {
    if (predicate(this)) return true
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            if (getChildAt(index).anyDepthFirst(predicate)) return true
        }
    }
    return false
}

private fun View.hasSurfaceBackedContent(): Boolean {
    var found = false
    forEachDepthFirst { descendant ->
        if (descendant is SurfaceView || descendant is TextureView) found = true
    }
    return found
}

private fun View.captureBitmapOrNull(): Bitmap? {
    if (width <= 0 || height <= 0) return null
    return runCatching {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            try {
                draw(Canvas(bitmap))
            } catch (throwable: Throwable) {
                bitmap.recycle()
                throw throwable
            }
        }
    }.getOrNull()
}

private fun lerp(start: Int, end: Int, fraction: Float): Int {
    return (start + (end - start) * fraction).roundToInt()
}

private fun Float.toDrawableAlpha(): Int {
    return (coerceIn(0f, 1f) * 255f).roundToInt()
}
