package com.viewcompose.preview.catalog

/**
 * Paparazzi 覆盖率测试要求必须存在的代表性 Preview 用例。
 * Representative Preview specs that Paparazzi coverage tests require to exist.
 */
internal object PreviewCoverageTargets {
    /**
     * 每个核心领域至少保留一个稳定 id，防止重命名或删用例时悄悄丢覆盖。
     * Keeps at least one stable id per core domain so renames or deletions cannot silently drop coverage.
     */
    val requiredSpecIds: Set<String> = setOf(
        "content-text-image",
        "content-badge-surface",
        "input-controls",
        "input-text-fields",
        "container-box-row-column",
        "container-flow-scroll",
        "container-constraint-layout",
        "collection-lazy-column",
        "collection-grid-pagers-tabs",
        "navigation-app-bars",
        "feedback-progress",
        "feedback-overlay-static",
        "modifier-style-anchor",
        "animation-core-transitions",
        "animation-seekable-transition",
        "gesture-tap-drag-swipe-transform",
        "graphics-canvas-draw-pipeline",
    )
}
