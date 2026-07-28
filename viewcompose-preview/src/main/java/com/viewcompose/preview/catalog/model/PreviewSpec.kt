package com.viewcompose.preview.catalog.model

import com.viewcompose.widget.core.UiTreeBuilder

/**
 * Preview 用例的领域分组，保持与 demo 模块和快照报告的分类一致。
 * Domain grouping for Preview specs, aligned with demo modules and snapshot reports.
 */
internal enum class PreviewDomain(
    val title: String,
) {
    Content("Content"),
    Input("Input"),
    Container("Container"),
    Collection("Collection"),
    Navigation("Navigation"),
    Feedback("Feedback"),
    Modifier("Modifier"),
    Animation("Animation"),
    Gesture("Gesture"),
    Graphics("Graphics"),
}

/**
 * 一个可在 Compose Preview、Paparazzi 和目录 UI 中复用的静态预览用例。
 * Static preview case reusable by Compose Preview, Paparazzi, and catalog UI.
 */
internal data class PreviewSpec(
    val id: String,
    val title: String,
    val domain: PreviewDomain,
    val content: UiTreeBuilder.() -> Unit,
)

/**
 * PreviewParameterProvider 传递的轻量引用，避免把 DSL lambda 直接交给 Preview 参数系统。
 * Lightweight reference passed through PreviewParameterProvider so DSL lambdas are not used as parameters.
 */
internal data class PreviewSpecRef(
    val id: String,
)
