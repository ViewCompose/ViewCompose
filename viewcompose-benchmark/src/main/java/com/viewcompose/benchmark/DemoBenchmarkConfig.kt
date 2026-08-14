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
 * 正式物理基准使用的洁净迭代次数。
 * Clean iteration count used by formal physical benchmarks.
 *
 * 五次独立测量足以执行 run-P50 稳定性门禁，同时避免参考真机在单个方法内进入严重热降频。
 * Five independent measurements support the run-P50 stability gate without pushing the reference
 * device into severe thermal throttling inside one benchmark method.
 */
internal const val RELEASE_BASELINE_ITERATIONS = 5

/**
 * UiAutomator 等待关键文本出现的统一超时时间。
 * Shared timeout for UiAutomator waits on required text.
 */
internal const val UI_WAIT_TIMEOUT_MS = 5_000L
