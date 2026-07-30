package com.viewcompose.preview.gradle

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreviewAndroidEnvironmentTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `compile SDK is derived from Android boot classpath`() {
        val androidJar = File(
            temporaryFolder.root,
            "sdk/platforms/android-35/android.jar",
        )

        assertEquals(35, resolveCompileSdk(listOf(androidJar)))
    }

    @Test
    fun `compile SDK rejects ambiguous boot classpath`() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveCompileSdk(
                listOf(
                    File("/sdk/platforms/android-34/android.jar"),
                    File("/sdk/platforms/android-35/android.jar"),
                ),
            )
        }
    }

    @Test
    fun `resource package names include namespace and dependency symbols deterministically`() {
        val dependencySymbols = temporaryFolder.newFile("dependency-r.txt").apply {
            writeText(
                """
                sample.library
                int string library_name 0x7f010001
                """.trimIndent(),
            )
        }
        val duplicateSymbols = temporaryFolder.newFile("duplicate-r.txt").apply {
            writeText("sample.fixture\n")
        }

        assertEquals(
            listOf("sample.fixture", "sample.library"),
            buildResourcePackageNames(
                namespace = "sample.fixture",
                packageFiles = listOf(dependencySymbols, duplicateSymbols),
            ),
        )
    }
}
