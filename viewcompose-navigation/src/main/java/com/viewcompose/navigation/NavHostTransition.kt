package com.viewcompose.navigation

import com.viewcompose.navigation.core.NavBackStackSnapshot
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavPaneScene
import com.viewcompose.navigation.core.NavStackMutation

/**
 * 单次视觉转场的递增标识。
 * Monotonic identifier for one visual transition.
 */
@JvmInline
internal value class NavHostTransitionId(
    val value: Long,
)

/**
 * 转场终止原因，用于区分正常完成、重定向和宿主销毁。
 * Transition terminal reason, distinguishing completion, redirection, and host destruction.
 */
internal enum class NavHostTransitionOutcome {
    Completed,
    Cancelled,
    Redirected,
    HostDestroyed,
}

/**
 * 已提交导航事务对应的视觉转场模型。
 * Visual transition model for a committed navigation transaction.
 *
 * [beforeScene] 和 [afterScene] 保存 pane 投影，[visibleEntryIds] 限定需要保留在宿主中的
 * entry，[layerOrder] 决定动画期间的绘制顺序。
 * [beforeScene] and [afterScene] keep pane projections, [visibleEntryIds] bounds entries retained
 * in the host, and [layerOrder] determines draw order during animation.
 */
internal data class NavHostTransition(
    val id: NavHostTransitionId,
    val command: NavCommand,
    val before: NavBackStackSnapshot,
    val after: NavBackStackSnapshot,
    val mutation: NavStackMutation,
    val outgoingEntry: NavEntry,
    val incomingEntry: NavEntry,
    val beforeScene: NavPaneScene,
    val afterScene: NavPaneScene,
    val retainedEntries: List<NavEntry>,
    val visibleEntryIds: Set<NavEntryId>,
    val layerOrder: List<NavEntryId>,
)

/**
 * 转场完成后回传给协调器的结果。
 * Result returned to the coordinator when a transition finishes.
 */
internal data class NavHostTransitionResult(
    val transition: NavHostTransition,
    val outcome: NavHostTransitionOutcome,
)

/**
 * 单次 predictive back 预览的递增标识。
 * Monotonic identifier for one predictive-back preview.
 */
@JvmInline
internal value class NavHostBackPreviewId(
    val value: Long,
)

/**
 * predictive back 手势开始的屏幕边缘。
 * Screen edge where a predictive-back gesture began.
 */
internal enum class NavHostBackSwipeEdge {
    Left,
    Right,
    None,
}

/**
 * Android back dispatcher 上报的手势采样点。
 * Gesture sample reported by the Android back dispatcher.
 */
internal data class NavHostBackEvent(
    val touchX: Float,
    val touchY: Float,
    val progress: Float,
    val swipeEdge: NavHostBackSwipeEdge,
    val frameTimeMillis: Long,
) {
    init {
        require(progress.isFinite() && progress in 0f..1f) {
            "Back progress must be finite and within 0..1; value=$progress."
        }
    }
}

/**
 * predictive back 期间用于展示上一目的地的只读预览模型。
 * Read-only preview model used to reveal the previous destination during predictive back.
 */
internal data class NavHostBackPreview(
    val id: NavHostBackPreviewId,
    val command: NavCommand,
    val snapshot: NavBackStackSnapshot,
    val outgoingEntry: NavEntry,
    val incomingEntry: NavEntry,
    val beforeScene: NavPaneScene,
    val afterScene: NavPaneScene,
    val retainedEntries: List<NavEntry>,
    val visibleEntryIds: Set<NavEntryId>,
    val layerOrder: List<NavEntryId>,
)

/**
 * 运行中转场的控制句柄。
 * Control handle for an in-flight transition.
 */
internal fun interface NavHostTransitionHandle {
    fun cancel()

    /**
     * 停止当前 animator，同时保留视觉属性以衔接下一次转场。
     * Stops the current animator while preserving its visual properties for the next transition.
     */
    fun redirect() {
        cancel()
    }
}

/**
 * 运行中 predictive back 预览的控制句柄。
 * Control handle for an in-flight predictive-back preview.
 */
internal interface NavHostBackPreviewHandle {
    fun update(event: NavHostBackEvent)

    fun cancel()

    fun redirect() {
        cancel()
    }

    fun dispose() {
        cancel()
    }

    fun commit(
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ): NavHostTransitionHandle
}

/**
 * 抽象实际执行 native View 转场和 predictive back 预览的驱动器。
 * Abstraction that performs native View transitions and predictive-back previews.
 */
internal interface NavHostTransitionDriver {
    /**
     * 启动 [transition] 对应的视觉工作。
     * Starts visual work for [transition].
     *
     * [onCompleted] 必须在 Android 主线程调用。返回句柄会在新导航命令重定向本转场或宿主销毁时
     * 被取消。
     * [onCompleted] must be invoked on the Android main thread. The returned handle is cancelled
     * when a newer navigation command redirects this transition or when the host is destroyed.
     */
    fun start(
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ): NavHostTransitionHandle

    fun startBackPreview(
        preview: NavHostBackPreview,
        initialEvent: NavHostBackEvent,
    ): NavHostBackPreviewHandle

    fun destroy() = Unit
}

/**
 * 无动画驱动器，供测试、关闭动效或不可用宿主场景直接完成事务。
 * No-animation driver for tests, disabled motion, or hosts that cannot animate.
 */
internal object ImmediateNavHostTransitionDriver : NavHostTransitionDriver {
    override fun start(
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ): NavHostTransitionHandle {
        onCompleted()
        return NavHostTransitionHandle {}
    }

    override fun startBackPreview(
        preview: NavHostBackPreview,
        initialEvent: NavHostBackEvent,
    ): NavHostBackPreviewHandle {
        return object : NavHostBackPreviewHandle {
            override fun update(event: NavHostBackEvent) = Unit

            override fun cancel() = Unit

            override fun commit(
                transition: NavHostTransition,
                onCompleted: () -> Unit,
            ): NavHostTransitionHandle {
                onCompleted()
                return NavHostTransitionHandle {}
            }
        }
    }
}
