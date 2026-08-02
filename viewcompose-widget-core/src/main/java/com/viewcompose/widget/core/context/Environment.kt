package com.viewcompose.widget.core

import android.content.Context
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.environment.UiLocaleList
import com.viewcompose.ui.unit.UiDensity

/** Supplies deterministic environment defaults when no platform configuration is available. */
object UiEnvironmentDefaults {
    /** Returns the platform-neutral default density, undetermined locale, and LTR direction. */
    fun values(): UiEnvironmentValues = UiEnvironmentValues.Default
}

/**
 * The current density. Kept separate from locale and direction so scoped overrides are explicit.
 */
val LocalDensity = uiLocalOf(
    debugName = "Environment.Density",
    debugValueFormatter = { density ->
        "density=${density.density}, fontScale=${density.fontScale}"
    },
    defaultFactory = { UiDensity.Default },
)

/** Current ordered locale preferences for resource and formatting decisions. */
val LocalLocales = uiLocalOf(
    debugName = "Environment.Locales",
    debugValueFormatter = UiLocaleList::toString,
    defaultFactory = { UiLocaleList.Undetermined },
)

/** Current logical layout direction used to resolve start and end edges. */
val LocalLayoutDirection = uiLocalOf(
    debugName = "Environment.LayoutDirection",
    defaultFactory = { UiLayoutDirection.Ltr },
)

/** Exposes density, locales, and layout direction for the current composition scope. */
object Environment {
    /** Current logical density and font scale. */
    val density: UiDensity
        get() = UiLocals.current(LocalDensity)

    /** Current locale tags in preference order. */
    val localeTags: List<String>
        get() = UiLocals.current(LocalLocales).tags

    /** Current immutable locale list. */
    val locales: UiLocaleList
        get() = UiLocals.current(LocalLocales)

    /** Current logical layout direction. */
    val layoutDirection: UiLayoutDirection
        get() = UiLocals.current(LocalLayoutDirection)

    /** Immutable aggregate snapshot of the current environment values. */
    val values: UiEnvironmentValues
        get() = UiEnvironmentValues(
            density = density,
            locales = locales,
            layoutDirection = layoutDirection,
        )
}

/**
 * Provides one environment snapshot while building [content].
 *
 * Explicit [values] take precedence over [androidContext]. When both are absent, the deterministic
 * [UiEnvironmentDefaults] snapshot is used. Nested providers restore all three previous locals
 * after [content] returns.
 *
 * @param values explicit platform-neutral environment snapshot
 * @param androidContext Android resources used only when [values] is absent
 */
fun UiTreeBuilder.UiEnvironment(
    values: UiEnvironmentValues? = null,
    androidContext: Context? = null,
    content: UiTreeBuilder.() -> Unit,
) {
    val resolvedValues = values
        ?: androidContext?.let(AndroidEnvironmentBridge::fromContext)
        ?: UiEnvironmentDefaults.values()
    ProvideLocals(
        LocalDensity provides resolvedValues.density,
        LocalLocales provides resolvedValues.locales,
        LocalLayoutDirection provides resolvedValues.layoutDirection,
    ) {
        content()
    }
}
