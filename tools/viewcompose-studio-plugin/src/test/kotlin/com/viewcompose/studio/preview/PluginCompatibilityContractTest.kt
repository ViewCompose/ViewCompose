package com.viewcompose.studio.preview

import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCompatibilityContractTest {
    @Test
    fun `plugin is restricted to Android Studio`() {
        val pluginXml = checkNotNull(
            javaClass.classLoader.getResourceAsStream("META-INF/plugin.xml"),
        ).bufferedReader().use { reader -> reader.readText() }

        assertTrue(
            "The Marketplace plugin must declare the Android Studio platform module.",
            Regex(
                """<depends>\s*com\.intellij\.modules\.androidstudio\s*</depends>""",
            ).containsMatchIn(pluginXml),
        )
    }
}
