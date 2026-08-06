package com.viewcompose.ui.foundation

import com.viewcompose.runtime.frame.FallbackMonotonicFrameClock
import com.viewcompose.runtime.frame.MonotonicFrameClock

private val LocalMonotonicFrameClockValue = uiLocalOf<MonotonicFrameClock> { FallbackMonotonicFrameClock }

/** Exposes the monotonic frame clock used by the current composition scope. */
object LocalMonotonicFrameClock {
    /** Current clock, falling back to [FallbackMonotonicFrameClock] when no host provides one. */
    val current: MonotonicFrameClock
        get() = UiLocals.current(LocalMonotonicFrameClockValue)
}

/** Provides [clock] to frame-aware APIs invoked while building [content]. */
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
