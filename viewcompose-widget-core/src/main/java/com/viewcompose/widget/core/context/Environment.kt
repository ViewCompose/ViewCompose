package com.viewcompose.widget.core

import android.content.Context
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.environment.UiLocaleList
import com.viewcompose.ui.unit.UiDensity

/**
 * 无平台环境时使用的默认值。
 * Defaults used when no platform environment is available.
 */
object UiEnvironmentDefaults {
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

val LocalLocales = uiLocalOf(
    debugName = "Environment.Locales",
    debugValueFormatter = UiLocaleList::toString,
    defaultFactory = { UiLocaleList.Undetermined },
)

val LocalLayoutDirection = uiLocalOf(
    debugName = "Environment.LayoutDirection",
    defaultFactory = { UiLayoutDirection.Ltr },
)

/**
 * 当前 composition 可读取的环境信息。
 * Environment information readable from the current composition.
 */
object Environment {
    val density: UiDensity
        get() = UiLocals.current(LocalDensity)

    val localeTags: List<String>
        get() = UiLocals.current(LocalLocales).tags

    val locales: UiLocaleList
        get() = UiLocals.current(LocalLocales)

    val layoutDirection: UiLayoutDirection
        get() = UiLocals.current(LocalLayoutDirection)

    val values: UiEnvironmentValues
        get() = UiEnvironmentValues(
            density = density,
            locales = locales,
            layoutDirection = layoutDirection,
        )
}

/**
 * 在 content 范围内提供 UI 环境；未传 values 时可从 Android Context 解析。
 * Provides UI environment values within content; when values are absent, Android Context can be used.
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
