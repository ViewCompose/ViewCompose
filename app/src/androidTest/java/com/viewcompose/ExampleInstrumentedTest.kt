package com.viewcompose

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Android 设备上运行的基础 instrumentation 模板测试。
 * Instrumented test, which will execute on an Android device.
 *
 * 仅保留包名级 sanity check，确认测试 APK 指向正确目标应用。
 * Keeps only a package-name sanity check to confirm the test APK targets the expected app.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // 被测应用的 Context。
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.gzq.uiframework", appContext.packageName)
    }
}
