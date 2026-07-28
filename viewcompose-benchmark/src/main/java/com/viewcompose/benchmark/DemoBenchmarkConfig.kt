package com.viewcompose.benchmark

/**
 * Macrobenchmark 运行时要测量的目标应用包名。
 * Target app package measured by Macrobenchmark.
 */
internal const val TARGET_PACKAGE = "com.gzq.uiframework"

/**
 * 交互类 benchmark 的默认迭代次数。
 * Default iteration count for interaction benchmarks.
 */
internal const val DEFAULT_ITERATIONS = 5

/**
 * release baseline benchmark 使用的迭代次数。
 * Iteration count used by release baseline benchmarks.
 */
internal const val RELEASE_BASELINE_ITERATIONS = 10

/**
 * UiAutomator 等待关键文本出现的统一超时时间。
 * Shared timeout for UiAutomator waits on required text.
 */
internal const val UI_WAIT_TIMEOUT_MS = 5_000L
