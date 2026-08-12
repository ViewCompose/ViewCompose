package com.viewcompose.ui.foundation

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

/**
 * Current host-scoped resource invalidation identity.
 *
 * Android hosts advance this value after resource-affecting configuration or imperative refresh
 * events. It participates in emitted VNode environment equality so resource IDs can be rebound
 * even when their integer values remain unchanged. The value is not a persisted version and has
 * no ordering meaning across independent render hosts.
 */
val LocalResourceRevision = uiLocalOf(
    debugName = "Environment.ResourceRevision",
    defaultFactory = { 0L },
)

/** Exposes density, locales, layout direction, and resource revision for the current scope. */
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

    /**
     * Current host-scoped resource invalidation identity.
     *
     * A changed value means resource-backed output must be resolved again. It does not identify a
     * particular Android `Configuration` and must not be persisted or compared across hosts.
     */
    val resourceRevision: Long
        get() = UiLocals.current(LocalResourceRevision)

    /** Immutable aggregate snapshot of the current environment values. */
    val values: UiEnvironmentValues
        get() = UiEnvironmentValues(
            density = density,
            locales = locales,
            layoutDirection = layoutDirection,
            resourceRevision = resourceRevision,
        )
}

/**
 * Provides one environment snapshot while building [content].
 *
 * When [values] is absent, the deterministic [UiEnvironmentDefaults] snapshot is used. Android
 * hosts map platform resources into [UiEnvironmentValues] before entering this boundary. Nested
 * providers restore all previous locals after [content] returns.
 *
 * @param values explicit platform-neutral environment snapshot
 */
fun UiTreeBuilder.UiEnvironment(
    values: UiEnvironmentValues? = null,
    content: UiTreeBuilder.() -> Unit,
) {
    val resolvedValues = values ?: UiEnvironmentDefaults.values()
    ProvideLocals(
        LocalDensity provides resolvedValues.density,
        LocalLocales provides resolvedValues.locales,
        LocalLayoutDirection provides resolvedValues.layoutDirection,
        LocalResourceRevision provides resolvedValues.resourceRevision,
    ) {
        content()
    }
}
