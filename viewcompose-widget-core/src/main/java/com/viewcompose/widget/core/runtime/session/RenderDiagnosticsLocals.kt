package com.viewcompose.widget.core

/**
 * 通过 locals 传递的可选回调，让子 render session 报告渲染诊断。
 * Optional callback propagated through locals so child render sessions
 * (lazy/pager/overlay session hosts) can report render diagnostics.
 */
val LocalRenderResultListener = uiLocalOf<((RenderTreeResult) -> Unit)?>(
    debugName = "RenderResultListener",
    debugValueFormatter = { listener -> if (listener == null) "none" else "installed" },
) { null }
