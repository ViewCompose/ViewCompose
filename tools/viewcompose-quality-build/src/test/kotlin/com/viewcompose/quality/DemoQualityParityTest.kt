package com.viewcompose.quality

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DemoQualityParityTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `development-tooling isolation preserves all legacy diagnostics and selected paths`() {
        val repository = fixtureRepository(
            label = "development-tooling",
            "viewcompose-runtime/src/main/Bad.kt" to "DeviceDslSource\n",
            "viewcompose-preview/src/main/Bad.kt" to "addOnDrawListener\n",
            "app/build.gradle.kts" to
                "releaseImplementation(project(\":viewcompose-preview\"))\n",
        )
        assertFrozenParity(
            label = "verifyDevelopmentToolingIsolation",
            repository = repository,
            expected = failedOutcome(
                diagnostic = buildString {
                    appendLine("Development-tooling isolation verification failed:")
                    appendLine(
                        "- app releaseRuntimeClasspath -> forbidden tooling component " +
                            "'project :viewcompose-preview'",
                    )
                    appendLine(
                        "- app/build.gradle.kts -> viewcompose-preview must be debug/test scoped, " +
                            "found releaseImplementation",
                    )
                    appendLine(
                        "- viewcompose-preview/src/main/Bad.kt -> tooling hot-path listener " +
                            "'addOnDrawListener' requires an ADR-backed allowlist and benchmark",
                    )
                    appendLine(
                        "- viewcompose-runtime/src/main/Bad.kt -> concrete tooling marker " +
                            "'DeviceDslSource' is forbidden in runtime production source",
                    )
                }.trimEnd(),
                "app/build.gradle.kts",
                "viewcompose-preview/src/main/Bad.kt",
                "viewcompose-runtime/src/main/Bad.kt",
            ),
        ) { fixture ->
            DemoQualityVerifiers.verifyDevelopmentToolingIsolation(
                repository = fixture,
                runtimeSourceDirectories = setOf(fixture.resolve("viewcompose-runtime/src/main")),
                toolingSourceDirectories = setOf(fixture.resolve("viewcompose-preview/src/main")),
                appBuildFile = fixture.resolve("app/build.gradle.kts"),
                toolingModules = setOf("viewcompose-preview", "viewcompose-benchmark"),
                releaseRuntimeComponents = listOf("project :viewcompose-preview"),
            )
        }
    }

    @Test
    fun `release APK isolation preserves marker diagnostic and archive input`() {
        val repository = fixtureRepository(label = "release-apk")
        val apk = repository.resolve("app/build/outputs/apk/release/app-release-unsigned.apk")
        apk.parentFile.mkdirs()
        ZipOutputStream(apk.outputStream()).use { archive ->
            archive.putNextEntry(ZipEntry("classes.dex"))
            archive.write("DeviceDslSourceRequestReceiver".toByteArray())
            archive.closeEntry()
        }
        assertFrozenParity(
            label = "verifyDemoReleaseToolingApk",
            repository = repository,
            expected = failedOutcome(
                diagnostic =
                    "Release APK tooling-isolation verification failed:\n" +
                        "- classes.dex -> forbidden tooling marker 'DeviceDslSourceRequestReceiver'",
                "app/build/outputs/apk/release/app-release-unsigned.apk",
            ),
        ) { fixture ->
            DemoQualityVerifiers.verifyDemoReleaseToolingApk(
                repository = fixture,
                releaseApk = fixture.resolve(
                    "app/build/outputs/apk/release/app-release-unsigned.apk",
                ),
            )
        }
    }

    @Test
    fun `automation selector failure preserves usage count and source input`() {
        val repository = fixtureRepository(
            label = "automation-selectors",
            "app/src/androidTest/kotlin/BadTest.kt" to
                "By.text(\"Hello\")\nwaitForText(\"World\")\n",
        )
        assertFrozenParity(
            label = "verifyDemoAutomationSelectors",
            repository = repository,
            expected = failedOutcome(
                diagnostic =
                    "Demo automation selector verification failed:\n" +
                        "- app/src/androidTest/kotlin/BadTest.kt -> found 2 visible-text selector usages\n" +
                        "Use scenario-owned Android resource IDs.",
                "app/src/androidTest/kotlin/BadTest.kt",
            ),
        ) { fixture ->
            DemoQualityVerifiers.verifyDemoAutomationSelectors(
                repository = fixture,
                sourceDirectories = setOf(fixture.resolve("app/src/androidTest")),
            )
        }
    }

    @Test
    fun `localization failure preserves resource and format diagnostics`() {
        val repository = fixtureRepository(
            label = "localization",
            "app/src/main/res/values/strings.xml" to
                """<resources>
                    <string name="only_default">Default</string>
                    <string name="shared">Hello %1${'$'}s</string>
                </resources>
                """.trimIndent(),
            "app/src/main/res/values-zh-rCN/strings.xml" to
                """<resources>
                    <string name="only_chinese">中文</string>
                    <string name="shared">你好 %1${'$'}d</string>
                </resources>
                """.trimIndent(),
        )
        assertFrozenParity(
            label = "verifyDemoLocalizationResources",
            repository = repository,
            expected = failedOutcome(
                diagnostic =
                    "Demo localization resource verification failed:\n" +
                        "- string:only_default is missing from values-zh-rCN\n" +
                        "- string:only_chinese has no canonical default-English resource\n" +
                        "- string:shared[value] format differs: default=[1:s], zh-rCN=[1:d]",
                "app/src/main/res/values-zh-rCN/strings.xml",
                "app/src/main/res/values/strings.xml",
            ),
        ) { fixture ->
            DemoQualityVerifiers.verifyDemoLocalizationResources(
                repository = fixture,
                defaultResourcesDirectory = fixture.resolve("app/src/main/res/values"),
                chineseResourcesDirectory = fixture.resolve("app/src/main/res/values-zh-rCN"),
            )
        }
    }

    @Test
    fun `localized visible-copy failure preserves line diagnostic and source input`() {
        val repository = fixtureRepository(
            label = "visible-copy",
            "app/src/main/java/com/viewcompose/demo/pages/Bad.kt" to
                "fun Content() {\n    Text(\"Hello\")\n}\n",
        )
        assertFrozenParity(
            label = "verifyDemoLocalizedVisibleCopy",
            repository = repository,
            expected = failedOutcome(
                diagnostic =
                    "Demo localized visible-copy verification failed:\n" +
                        "- app/src/main/java/com/viewcompose/demo/pages/Bad.kt:2 -> Text(\"Hello\")\n" +
                        "Resolve visible copy through Android resources in migrated domains.",
                "app/src/main/java/com/viewcompose/demo/pages/Bad.kt",
            ),
        ) { fixture ->
            DemoQualityVerifiers.verifyDemoLocalizedVisibleCopy(
                repository = fixture,
                migratedSources = setOf(
                    fixture.resolve("app/src/main/java/com/viewcompose/demo/pages/Bad.kt"),
                ),
            )
        }
    }

    private fun fixtureRepository(
        label: String,
        vararg files: Pair<String, String>,
    ): File {
        val repository = temporaryFolder.newFolder(label)
        files.forEach { (path, content) ->
            repository.resolve(path).apply {
                parentFile.mkdirs()
                writeText(content)
            }
        }
        return repository
    }

    private fun failedOutcome(diagnostic: String, vararg paths: String): QualityGateOutcome =
        QualityGateOutcome(
            succeeded = false,
            diagnostics = listOf(diagnostic),
            selectedPaths = paths.toList().sorted(),
        )

    private fun assertFrozenParity(
        label: String,
        repository: File,
        expected: QualityGateOutcome,
        candidate: QualityGateImplementation,
    ) {
        val result = QualityGateParityHarness().compare(
            fixtureRepository = repository,
            legacy = QualityGateImplementation { expected },
            candidate = candidate,
        )
        assertTrue("$label: ${result.differences.joinToString("; ")}", result.isEquivalent)
        result.assertEquivalent()
    }
}
