package com.viewcompose.host.android.environment

/*
 * 测试职责：覆盖 Host Android 环境桥接，防止平台密度、语言和布局方向映射在重构中回退。
 * Test responsibility: covers the Host Android environment bridge and guards platform density, locale, and layout-direction mapping.
 */

import android.view.View
import com.viewcompose.ui.environment.UiLayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AndroidEnvironmentBridgeTest {
    @Test
    fun `layout direction maps rtl and ltr`() {
        assertEquals(UiLayoutDirection.Rtl, EnvironmentValueMapper.layoutDirection(View.LAYOUT_DIRECTION_RTL))
        assertEquals(UiLayoutDirection.Ltr, EnvironmentValueMapper.layoutDirection(View.LAYOUT_DIRECTION_LTR))
    }

    @Test
    fun `locale mapper falls back to default locale when list is absent`() {
        val locale = Locale.getDefault().toLanguageTag()

        assertEquals(listOf(locale), EnvironmentValueMapper.localeTags(null))
    }
}
