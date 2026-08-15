package com.viewcompose

/** Stable item identity and visible value used by collection fixtures. */
internal data class DemoListItem(
    val id: String,
    val title: String,
)

/** Label and color pair used by theme swatch fixtures. */
internal data class ThemeSwatch(
    val label: String,
    val color: Int,
)

/** One generated or runtime-derived diagnostic fact. */
internal data class DiagnosticFact(
    val label: String,
    val value: String,
)
