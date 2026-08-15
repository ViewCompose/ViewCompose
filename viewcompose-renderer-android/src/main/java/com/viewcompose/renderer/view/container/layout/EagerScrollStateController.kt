package com.viewcompose.renderer.view.container

import android.view.View
import com.viewcompose.ui.state.ScrollConnector
import com.viewcompose.ui.state.ScrollState
import com.viewcompose.ui.state.ScrollStateSnapshot

/** Bridges one eager Android scroll host to the portable ScrollState snapshot contract. */
internal class EagerScrollStateController(
    private val host: View,
    private val currentLogicalValue: () -> Int,
    private val currentMaxValue: () -> Int,
    private val currentViewportSize: () -> Int,
    private val performScroll: (logicalValue: Int, animated: Boolean) -> Unit,
) : ScrollConnector {
    private var attachedState: ScrollState? = null
    private var snapshotListener: ((ScrollStateSnapshot) -> Unit)? = null
    private var currentSnapshot: ScrollStateSnapshot? = null
    private var pendingTarget: PendingTarget? = null
    private var monitorScheduled = false
    private var lastMonitorValue = 0
    private var stableMonitorFrames = 0
    private var disposed = false
    private val monitor = object : Runnable {
        override fun run() {
            monitorScheduled = false
            if (disposed || !host.isAttachedToWindow) {
                publish(scrolling = false)
                return
            }
            val nextValue = resolvedLogicalValue()
            if (nextValue == lastMonitorValue) {
                stableMonitorFrames += 1
            } else {
                lastMonitorValue = nextValue
                stableMonitorFrames = 0
                publish(scrolling = true)
            }
            if (stableMonitorFrames >= IDLE_STABLE_FRAMES) {
                publish(scrolling = false)
            } else {
                scheduleMonitor()
            }
        }
    }

    override val identity: Any
        get() = host

    fun bind(
        state: ScrollState?,
        userScrollEnabled: Boolean,
        applyUserScrollEnabled: (Boolean) -> Unit,
    ) {
        applyUserScrollEnabled(userScrollEnabled)
        if (attachedState === state) {
            publish(scrolling = currentSnapshot?.isScrollInProgress == true)
            return
        }
        attachedState?.attach(null)
        attachedState = state
        state?.attach(this)
    }

    fun onTouchStarted() {
        publish(scrolling = true)
    }

    fun onTouchEnded() {
        scheduleMonitor()
    }

    fun onScrollPositionChanged() {
        publish(scrolling = true)
        scheduleMonitor()
    }

    fun onLayoutChanged() {
        val pending = pendingTarget
        if (pending != null) {
            pendingTarget = null
            val target = pending.value.coerceAtMost(currentMaxValue())
            performScroll(target, pending.animated)
            publish(scrolling = pending.animated, allowDuringLayout = true)
            if (pending.animated) scheduleMonitor()
        } else {
            publish(
                scrolling = currentSnapshot?.isScrollInProgress == true,
                allowDuringLayout = true,
            )
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        host.removeCallbacks(monitor)
        monitorScheduled = false
        attachedState?.attach(null)
        attachedState = null
        snapshotListener = null
    }

    override fun scrollTo(value: Int, animated: Boolean) {
        if (!host.isLaidOut) {
            pendingTarget = PendingTarget(value, animated)
            return
        }
        val target = value.coerceAtMost(currentMaxValue())
        performScroll(target, animated)
        publish(scrolling = animated)
        if (animated) scheduleMonitor()
    }

    override fun currentSnapshot(): ScrollStateSnapshot? = currentSnapshot

    override fun setOnSnapshotChangedListener(listener: ((ScrollStateSnapshot) -> Unit)?) {
        snapshotListener = listener
    }

    private fun scheduleMonitor() {
        if (monitorScheduled || disposed) return
        monitorScheduled = true
        lastMonitorValue = resolvedLogicalValue()
        host.postOnAnimation(monitor)
    }

    private fun resolvedLogicalValue(): Int {
        return currentLogicalValue().coerceIn(0, currentMaxValue())
    }

    private fun publish(
        scrolling: Boolean,
        allowDuringLayout: Boolean = false,
    ) {
        if (disposed || (!allowDuringLayout && !host.isLaidOut)) return
        val maximum = currentMaxValue().coerceAtLeast(0)
        val value = currentLogicalValue().coerceIn(0, maximum)
        val previousValue = currentSnapshot?.value ?: value
        val movedBackward = value < previousValue
        val movedForward = value > previousValue
        val next = ScrollStateSnapshot(
            value = value,
            maxValue = maximum,
            viewportSize = currentViewportSize().coerceAtLeast(0),
            isScrollInProgress = scrolling,
            canScrollBackward = value > 0,
            canScrollForward = value < maximum,
            lastScrolledBackward = when {
                movedBackward -> true
                movedForward -> false
                else -> currentSnapshot?.lastScrolledBackward ?: false
            },
            lastScrolledForward = when {
                movedForward -> true
                movedBackward -> false
                else -> currentSnapshot?.lastScrolledForward ?: false
            },
        )
        if (currentSnapshot == next) return
        currentSnapshot = next
        snapshotListener?.invoke(next)
    }

    private data class PendingTarget(
        val value: Int,
        val animated: Boolean,
    )

    companion object {
        private const val IDLE_STABLE_FRAMES = 2
    }
}
