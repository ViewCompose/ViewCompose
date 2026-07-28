package com.viewcompose.widget.core

import android.content.Context
import kotlin.math.roundToInt

/**
 * UI 密度信息，density 用于 dp，scaledDensity 用于 sp。
 * UI density values; density is used for dp and scaledDensity is used for sp.
 */
data class UiDensity(
    val density: Float,
    val scaledDensity: Float,
) {
    /**
     * 将 dp 整数转换为当前密度下的像素整数。
     * Converts an integer dp value to pixels under the current density.
     */
    fun dp(value: Int): Int = (value * density).roundToInt()

    /**
     * 将 sp 整数转换为当前字体缩放下的像素整数。
     * Converts an integer sp value to pixels under the current font scale.
     */
    fun sp(value: Int): Int = (value * scaledDensity).roundToInt()
}

/**
 * UI 布局方向。
 * UI layout direction.
 */
enum class UiLayoutDirection {
    Ltr,
    Rtl,
}

/**
 * 当前 UI 环境快照。
 * Snapshot of the current UI environment.
 */
data class UiEnvironmentValues(
    val density: UiDensity,
    val localeTags: List<String>,
    val layoutDirection: UiLayoutDirection,
)

/**
 * 无平台环境时使用的默认值。
 * Defaults used when no platform environment is available.
 */
object UiEnvironmentDefaults {
    fun values(): UiEnvironmentValues {
        return UiEnvironmentValues(
            density = UiDensity(
                density = 1f,
                scaledDensity = 1f,
            ),
            localeTags = listOf("und"),
            layoutDirection = UiLayoutDirection.Ltr,
        )
    }
}

private val LocalEnvironment = uiLocalOf(
    debugName = "Environment",
    debugValueFormatter = { values ->
        "density=${values.density.density}, locale=${values.localeTags.joinToString()}, direction=${values.layoutDirection}"
    },
    defaultFactory = UiEnvironmentDefaults::values,
)

/**
 * 当前 composition 可读取的环境信息。
 * Environment information readable from the current composition.
 */
object Environment {
    val density: UiDensity
        get() = UiLocals.current(LocalEnvironment).density

    val localeTags: List<String>
        get() = UiLocals.current(LocalEnvironment).localeTags

    val layoutDirection: UiLayoutDirection
        get() = UiLocals.current(LocalEnvironment).layoutDirection
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
    ProvideLocal(LocalEnvironment, resolvedValues) {
        content()
    }
}
