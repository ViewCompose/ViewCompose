package com.viewcompose.performance

import android.content.Intent

const val EXTRA_PERFORMANCE_ENGINE: String = "performance_engine"
const val EXTRA_PERFORMANCE_SCENARIO: String = "performance_scenario"

internal enum class PerformanceEngine(
    val wireValue: String,
    val displayName: String,
) {
    ViewCompose(
        wireValue = "viewcompose",
        displayName = "ViewCompose",
    ),
    Compose(
        wireValue = "compose",
        displayName = "Compose",
    ),
    ;

    companion object {
        fun fromIntent(intent: Intent): PerformanceEngine {
            val value = intent.getStringExtra(EXTRA_PERFORMANCE_ENGINE)
            return entries.firstOrNull { it.wireValue == value }
                ?: error("Unknown performance engine: $value")
        }
    }
}

internal enum class PerformanceScenario(
    val wireValue: String,
) {
    List(
        wireValue = "list",
    ),
    ComplexLayout(
        wireValue = "complex_layout",
    ),
    ;

    companion object {
        fun fromIntent(intent: Intent): PerformanceScenario {
            val value = intent.getStringExtra(EXTRA_PERFORMANCE_SCENARIO)
            return entries.firstOrNull { it.wireValue == value }
                ?: error("Unknown performance scenario: $value")
        }
    }
}
