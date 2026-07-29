package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core context 中的 Android Environment Bridge 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Android Environment Bridge behavior in widget-core context and guards DSL, state, or theme contracts against regressions.
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
