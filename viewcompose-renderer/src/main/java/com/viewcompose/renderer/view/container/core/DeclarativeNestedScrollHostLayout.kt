package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import com.viewcompose.ui.gesture.NestedScrollConnection
import com.viewcompose.ui.gesture.NestedScrollDispatcher
import com.viewcompose.ui.gesture.NestedScrollDispatcherConnector
import com.viewcompose.ui.gesture.NestedScrollSource
import com.viewcompose.ui.gesture.ScrollDelta
import com.viewcompose.ui.gesture.ScrollVelocity
import com.viewcompose.renderer.decoration.DecorationChildDrawingOrder
import com.viewcompose.renderer.decoration.DecorationDrawingOrderContainer
import com.viewcompose.renderer.decoration.ViewDecorationDrawing
import kotlin.math.roundToInt

/**
 * Android host for nestedScroll modifiers that bridges View nested scrolling and framework gesture protocols.
 * Android host for nestedScroll modifiers, bridging View nested-scroll callbacks to UIFramework gestures.
 */
internal class DeclarativeNestedScrollHostLayout(
    context: Context,
) : FrameLayout(context),
    ChildHostViewGroup,
    DecorationDrawingOrderContainer,
    NestedScrollingParent3 {
    private val decorationDrawing = ViewDecorationDrawing(this)

    override val childHost: FrameLayout
        get() = this

    private val parentHelper = NestedScrollingParentHelper(this)
    private val childHelper = NestedScrollingChildHelper(this).apply {
        isNestedScrollingEnabled = true
    }
    private var connection: NestedScrollConnection = EmptyNestedScrollConnection
    private var dispatcher: NestedScrollDispatcher? = null
    private val dispatcherConnector = HostDispatcherConnector()

    init {
        clipChildren = false
        clipToPadding = false
    }

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int =
        DecorationChildDrawingOrder.getChildDrawingOrder(this, childCount, drawingPosition)

    override fun setDecorationDrawingOrderEnabled(enabled: Boolean) {
        isChildrenDrawingOrderEnabled = enabled
    }

    fun update(
        connection: NestedScrollConnection,
        dispatcher: NestedScrollDispatcher?,
    ) {
        // The dispatcher may change during node reuse, so detach the old connection before attaching the new one.
        // The dispatcher can change during node reuse, so detach the old connector before attaching the new one.
        if (this.dispatcher !== dispatcher) {
            this.dispatcher?.detach(dispatcherConnector)
            this.dispatcher = dispatcher
            dispatcher?.attach(dispatcherConnector)
        }
        this.connection = connection
    }

    fun dispose() {
        dispatcher?.detach(dispatcherConnector)
        dispatcher = null
        connection = EmptyNestedScrollConnection
        childHelper.stopNestedScroll(ViewCompat.TYPE_TOUCH)
        childHelper.stopNestedScroll(ViewCompat.TYPE_NON_TOUCH)
    }

    override fun drawChild(
        canvas: Canvas,
        child: View,
        drawingTime: Long,
    ): Boolean {
        if (!decorationDrawing.hasDecoratedChildren) {
            return super.drawChild(canvas, child, drawingTime)
        }
        val decoration = decorationDrawing.decorationOrNull(child)
            ?: return super.drawChild(canvas, child, drawingTime)
        decorationDrawing.drawBehindChild(canvas, child, decoration)
        val drawn = super.drawChild(canvas, child, drawingTime)
        decorationDrawing.drawOverChild(canvas, child, decoration)
        return drawn
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        DecorationChildDrawingOrder.onViewAdded(this, child)
        decorationDrawing.onViewAdded(child)
    }

    override fun onViewRemoved(child: View) {
        decorationDrawing.onViewRemoved(child)
        super.onViewRemoved(child)
        DecorationChildDrawingOrder.onViewRemoved(this, child)
    }

    override fun onStartNestedScroll(
        child: View,
        target: View,
        axes: Int,
        type: Int,
    ): Boolean {
        return axes and (ViewCompat.SCROLL_AXIS_HORIZONTAL or ViewCompat.SCROLL_AXIS_VERTICAL) != 0
    }

    override fun onNestedScrollAccepted(
        child: View,
        target: View,
        axes: Int,
        type: Int,
    ) {
        parentHelper.onNestedScrollAccepted(child, target, axes, type)
        childHelper.startNestedScroll(axes, type)
    }

    override fun onStopNestedScroll(
        target: View,
        type: Int,
    ) {
        parentHelper.onStopNestedScroll(target, type)
        childHelper.stopNestedScroll(type)
    }

    override fun onNestedPreScroll(
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int,
    ) {
        // Offer pre-scroll to outer parents first, then pass the remainder to the current connection.
        // Pre-scroll is offered to outer parents first, then the remaining delta is offered to this connection.
        val parentConsumed = IntArray(2)
        childHelper.dispatchNestedPreScroll(
            dx,
            dy,
            parentConsumed,
            null,
            type,
        )
        consumed[0] += parentConsumed[0]
        consumed[1] += parentConsumed[1]

        val available = ScrollDelta(
            x = (dx - parentConsumed[0]).toFloat(),
            y = (dy - parentConsumed[1]).toFloat(),
        )
        val local = connection.onPreScroll(
            available = available,
            source = type.toNestedScrollSource(),
        ).coerceToAvailable(available)
        consumed[0] += local.x.roundToInt()
        consumed[1] += local.y.roundToInt()
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray,
    ) {
        val childConsumed = ScrollDelta(
            x = dxConsumed.toFloat(),
            y = dyConsumed.toFloat(),
        )
        val available = ScrollDelta(
            x = dxUnconsumed.toFloat(),
            y = dyUnconsumed.toFloat(),
        )
        val local = connection.onPostScroll(
            consumed = childConsumed,
            available = available,
            source = type.toNestedScrollSource(),
        ).coerceToAvailable(available)
        consumed[0] += local.x.roundToInt()
        consumed[1] += local.y.roundToInt()

        // Bubble remaining post-scroll to the parent to preserve ordering across nested connections.
        // The remaining post-scroll delta bubbles to parents, preserving multi-layer nestedScroll ordering.
        val remaining = available - local
        val parentConsumed = IntArray(2)
        childHelper.dispatchNestedScroll(
            dxConsumed + local.x.roundToInt(),
            dyConsumed + local.y.roundToInt(),
            remaining.x.roundToInt(),
            remaining.y.roundToInt(),
            null,
            type,
            parentConsumed,
        )
        consumed[0] += parentConsumed[0]
        consumed[1] += parentConsumed[1]
    }

    override fun onNestedPreFling(
        target: View,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        if (childHelper.dispatchNestedPreFling(velocityX, velocityY)) {
            return true
        }
        val available = ScrollVelocity(velocityX, velocityY)
        return !connection.onPreFling(available)
            .coerceToAvailable(available)
            .isZero
    }

    override fun onNestedFling(
        target: View,
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean,
    ): Boolean {
        val velocity = ScrollVelocity(velocityX, velocityY)
        val childConsumed = if (consumed) velocity else ScrollVelocity.Zero
        val available = if (consumed) ScrollVelocity.Zero else velocity
        val local = connection.onPostFling(
            consumed = childConsumed,
            available = available,
        ).coerceToAvailable(available)
        val remaining = available - local
        val parentConsumed = childHelper.dispatchNestedFling(
            remaining.x,
            remaining.y,
            consumed || !local.isZero,
        )
        return !local.isZero || parentConsumed
    }

    override fun getNestedScrollAxes(): Int = parentHelper.nestedScrollAxes

    override fun onStartNestedScroll(
        child: View,
        target: View,
        axes: Int,
    ): Boolean = onStartNestedScroll(
        child = child,
        target = target,
        axes = axes,
        type = ViewCompat.TYPE_TOUCH,
    )

    override fun onNestedScrollAccepted(
        child: View,
        target: View,
        axes: Int,
    ) {
        onNestedScrollAccepted(
            child = child,
            target = target,
            axes = axes,
            type = ViewCompat.TYPE_TOUCH,
        )
    }

    override fun onStopNestedScroll(target: View) {
        onStopNestedScroll(
            target = target,
            type = ViewCompat.TYPE_TOUCH,
        )
    }

    override fun onNestedPreScroll(
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
    ) {
        onNestedPreScroll(
            target = target,
            dx = dx,
            dy = dy,
            consumed = consumed,
            type = ViewCompat.TYPE_TOUCH,
        )
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
    ) {
        onNestedScroll(
            target = target,
            dxConsumed = dxConsumed,
            dyConsumed = dyConsumed,
            dxUnconsumed = dxUnconsumed,
            dyUnconsumed = dyUnconsumed,
            type = ViewCompat.TYPE_TOUCH,
            consumed = IntArray(2),
        )
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
    ) {
        onNestedScroll(
            target = target,
            dxConsumed = dxConsumed,
            dyConsumed = dyConsumed,
            dxUnconsumed = dxUnconsumed,
            dyUnconsumed = dyUnconsumed,
            type = type,
            consumed = IntArray(2),
        )
    }

    internal fun dispatchPreScrollFromDescendant(
        available: ScrollDelta,
        source: NestedScrollSource,
    ): ScrollDelta {
        val type = source.toViewCompatType()
        startFor(available, type)
        val parentConsumed = IntArray(2)
        childHelper.dispatchNestedPreScroll(
            available.x.roundToInt(),
            available.y.roundToInt(),
            parentConsumed,
            null,
            type,
        )
        val fromParent = ScrollDelta(
            x = parentConsumed[0].toFloat(),
            y = parentConsumed[1].toFloat(),
        ).coerceToAvailable(available)
        val remaining = available - fromParent
        val local = connection.onPreScroll(
            available = remaining,
            source = source,
        ).coerceToAvailable(remaining)
        return fromParent + local
    }

    internal fun dispatchPostScrollFromDescendant(
        consumed: ScrollDelta,
        available: ScrollDelta,
        source: NestedScrollSource,
    ): ScrollDelta {
        val local = connection.onPostScroll(
            consumed = consumed,
            available = available,
            source = source,
        ).coerceToAvailable(available)
        val remaining = available - local
        val type = source.toViewCompatType()
        startFor(consumed + available, type)
        val parentConsumed = IntArray(2)
        childHelper.dispatchNestedScroll(
            (consumed.x + local.x).roundToInt(),
            (consumed.y + local.y).roundToInt(),
            remaining.x.roundToInt(),
            remaining.y.roundToInt(),
            null,
            type,
            parentConsumed,
        )
        val fromParent = ScrollDelta(
            x = parentConsumed[0].toFloat(),
            y = parentConsumed[1].toFloat(),
        ).coerceToAvailable(remaining)
        return local + fromParent
    }

    internal fun dispatchPreFlingFromDescendant(
        available: ScrollVelocity,
    ): ScrollVelocity {
        if (childHelper.dispatchNestedPreFling(available.x, available.y)) {
            return available
        }
        return connection.onPreFling(available).coerceToAvailable(available)
    }

    internal fun dispatchPostFlingFromDescendant(
        consumed: ScrollVelocity,
        available: ScrollVelocity,
    ): ScrollVelocity {
        val local = connection.onPostFling(
            consumed = consumed,
            available = available,
        ).coerceToAvailable(available)
        val remaining = available - local
        val parentConsumed = childHelper.dispatchNestedFling(
            remaining.x,
            remaining.y,
            !consumed.isZero || !local.isZero,
        )
        return local + if (parentConsumed) remaining else ScrollVelocity.Zero
    }

    private fun startFor(
        delta: ScrollDelta,
        type: Int,
    ) {
        var axes = ViewCompat.SCROLL_AXIS_NONE
        if (delta.x != 0f) {
            axes = axes or ViewCompat.SCROLL_AXIS_HORIZONTAL
        }
        if (delta.y != 0f) {
            axes = axes or ViewCompat.SCROLL_AXIS_VERTICAL
        }
        if (axes != ViewCompat.SCROLL_AXIS_NONE) {
            childHelper.startNestedScroll(axes, type)
        }
    }

    private inner class HostDispatcherConnector : NestedScrollDispatcherConnector {
        override val identity: Any
            get() = this@DeclarativeNestedScrollHostLayout

        override fun dispatchPreScroll(
            available: ScrollDelta,
            source: NestedScrollSource,
        ): ScrollDelta = this@DeclarativeNestedScrollHostLayout.dispatchPreScrollFromDescendant(
            available = available,
            source = source,
        )

        override fun dispatchPostScroll(
            consumed: ScrollDelta,
            available: ScrollDelta,
            source: NestedScrollSource,
        ): ScrollDelta = this@DeclarativeNestedScrollHostLayout.dispatchPostScrollFromDescendant(
            consumed = consumed,
            available = available,
            source = source,
        )

        override fun dispatchPreFling(
            available: ScrollVelocity,
        ): ScrollVelocity =
            this@DeclarativeNestedScrollHostLayout.dispatchPreFlingFromDescendant(available)

        override fun dispatchPostFling(
            consumed: ScrollVelocity,
            available: ScrollVelocity,
        ): ScrollVelocity = this@DeclarativeNestedScrollHostLayout.dispatchPostFlingFromDescendant(
            consumed = consumed,
            available = available,
        )
    }
}

private object EmptyNestedScrollConnection : NestedScrollConnection

private fun Int.toNestedScrollSource(): NestedScrollSource {
    return if (this == ViewCompat.TYPE_TOUCH) {
        NestedScrollSource.UserInput
    } else {
        NestedScrollSource.Fling
    }
}

private fun NestedScrollSource.toViewCompatType(): Int {
    return if (this == NestedScrollSource.UserInput) {
        ViewCompat.TYPE_TOUCH
    } else {
        ViewCompat.TYPE_NON_TOUCH
    }
}

private fun ScrollDelta.coerceToAvailable(available: ScrollDelta): ScrollDelta {
    return ScrollDelta(
        x = x.coerceConsumption(available.x),
        y = y.coerceConsumption(available.y),
    )
}

private fun ScrollVelocity.coerceToAvailable(
    available: ScrollVelocity,
): ScrollVelocity {
    return ScrollVelocity(
        x = x.coerceConsumption(available.x),
        y = y.coerceConsumption(available.y),
    )
}

private fun Float.coerceConsumption(available: Float): Float {
    if (!isFinite() || !available.isFinite() || available == 0f) {
        return 0f
    }
    return if (available > 0f) {
        coerceIn(0f, available)
    } else {
        coerceIn(available, 0f)
    }
}
