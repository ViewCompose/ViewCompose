package com.viewcompose.widget.core

import com.viewcompose.runtime.frame.FallbackMonotonicFrameClock
import com.viewcompose.runtime.frame.MonotonicFrameClock

private val LocalMonotonicFrameClockValue = uiLocalOf<MonotonicFrameClock> { FallbackMonotonicFrameClock }

/**
 * 当前 composition 使用的单调帧时钟。
 * Monotonic frame clock used by the current composition.
 */
object LocalMonotonicFrameClock {
    val current: MonotonicFrameClock
        get() = UiLocals.current(LocalMonotonicFrameClockValue)
}

/**
 * 在 content 范围内提供帧时钟。
 * Provides a frame clock within the content scope.
 */
fun UiTreeBuilder.ProvideMonotonicFrameClock(
    clock: MonotonicFrameClock,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        local = LocalMonotonicFrameClockValue,
        value = clock,
        content = content,
    )
}
