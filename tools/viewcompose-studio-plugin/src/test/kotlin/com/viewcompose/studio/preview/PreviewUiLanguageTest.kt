package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewUiLanguageTest {
    @Test
    fun `defaults to English for missing or unknown persisted values`() {
        assertEquals(
            PreviewUiLanguage.English,
            PreviewUiLanguage.fromSettingValue(null),
        )
        assertEquals(
            PreviewUiLanguage.English,
            PreviewUiLanguage.fromSettingValue("unsupported"),
        )
    }

    @Test
    fun `loads English and Chinese interface messages independently`() {
        val english = PreviewUiMessages.forLanguage(PreviewUiLanguage.English)
        val chinese = PreviewUiMessages.forLanguage(PreviewUiLanguage.SimplifiedChinese)

        assertEquals("Preview", english.text("tab.preview"))
        assertEquals("预览", chinese.text("tab.preview"))
        assertEquals(
            "Rendering StaticDemoPreview…",
            english.loadingMessage("Rendering StaticDemoPreview…"),
        )
        assertEquals(
            "正在渲染 StaticDemoPreview…",
            chinese.loadingMessage("Rendering StaticDemoPreview…"),
        )
        assertEquals("Locate Device DSL", english.text("action.locateDeviceDsl"))
        assertEquals("定位设备当前 DSL", chinese.text("action.locateDeviceDsl"))
        assertEquals("Physical", english.text("deviceDsl.device.physical"))
        assertEquals("真机", chinese.text("deviceDsl.device.physical"))
    }
}
