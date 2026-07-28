package com.viewcompose.host.android

import com.viewcompose.ui.UiContract

/**
 * Android host 模块标记，用于暴露渲染会话和宿主桥接 API。
 * Android host marker for render session and host bridge APIs.
 */
object UiHostAndroid {
    /**
     * 运行时依赖链标识，供诊断输出确认 host-android 已接入。
     * Runtime dependency-chain marker used by diagnostics to confirm host-android is installed.
     */
    val dependencyChain: List<String> = UiContract.dependencyChain + "host-android"
}
