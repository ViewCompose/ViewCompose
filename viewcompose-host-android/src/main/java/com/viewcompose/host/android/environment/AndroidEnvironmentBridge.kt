package com.viewcompose.host.android.environment

import android.content.Context
import android.os.LocaleList
import android.view.View
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.environment.UiLocaleList
import com.viewcompose.ui.unit.UiDensity
import java.util.Locale

/**
 * Bridge from Android Context to UiEnvironmentValues.
 */
object AndroidEnvironmentBridge {
    /**
     * Reads density, locale, and layout direction from Android resources/configuration.
     */
    fun fromContext(context: Context): UiEnvironmentValues {
        val configuration = context.resources.configuration
        val displayMetrics = context.resources.displayMetrics
        return UiEnvironmentValues(
            density = UiDensity(
                density = displayMetrics.density,
                fontScale = configuration.fontScale,
            ),
            locales = UiLocaleList.from(EnvironmentValueMapper.localeTags(configuration.locales)),
            layoutDirection = EnvironmentValueMapper.layoutDirection(configuration.layoutDirection),
        )
    }
}

/**
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
