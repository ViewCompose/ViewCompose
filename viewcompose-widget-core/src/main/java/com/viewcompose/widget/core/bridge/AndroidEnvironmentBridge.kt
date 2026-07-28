package com.viewcompose.widget.core

import android.content.Context
import android.os.LocaleList
import android.view.View
import java.util.Locale

/**
 * Android Context 到 UiEnvironmentValues 的桥接器。
 * Bridge from Android Context to UiEnvironmentValues.
 */
object AndroidEnvironmentBridge {
    /**
     * 从 Android resources/configuration 读取密度、locale 和布局方向。
     * Reads density, locale, and layout direction from Android resources/configuration.
     */
    fun fromContext(context: Context): UiEnvironmentValues {
        val configuration = context.resources.configuration
        val displayMetrics = context.resources.displayMetrics
        return UiEnvironmentValues(
            density = UiDensity(
                density = displayMetrics.density,
                scaledDensity = displayMetrics.density * configuration.fontScale,
            ),
            localeTags = EnvironmentValueMapper.localeTags(configuration.locales),
            layoutDirection = EnvironmentValueMapper.layoutDirection(configuration.layoutDirection),
        )
    }
}

/**
 * Android environment 原始值到 ViewCompose 环境枚举的映射器。
 * Mapper from raw Android environment values to ViewCompose environment enums.
 */
internal object EnvironmentValueMapper {
    fun localeTags(localeList: LocaleList?): List<String> {
        if (localeList == null || localeList.isEmpty) {
            return listOf(Locale.getDefault().toLanguageTag())
        }
        return buildList {
            for (index in 0 until localeList.size()) {
                add(localeList[index].toLanguageTag())
            }
        }
    }

    fun layoutDirection(direction: Int): UiLayoutDirection {
        return when (direction) {
            View.LAYOUT_DIRECTION_RTL -> UiLayoutDirection.Rtl
            else -> UiLayoutDirection.Ltr
        }
    }
}
