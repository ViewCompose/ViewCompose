package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class StudioPreviewSourceResolverTest {
    @Test
    fun `prefers application call site over framework implementation`() {
        val framework = callSite(
            className = "com.viewcompose.widget.material.TextKt",
            methodName = "Text",
            fileName = "Text.kt",
            lineNumber = 42,
        )
        val application = callSite(
            className = "com.example.pages.AboutPageKt",
            methodName = "AboutPage",
            fileName = "AboutPage.kt",
            lineNumber = 73,
        )

        val source = resolveRuntimeSource(listOf(framework, application)) { fileName ->
            when (fileName) {
                "Text.kt" -> listOf(
                    "/project/viewcompose-ui-foundation/src/main/java/com/viewcompose/widget/material/Text.kt",
                )
                "AboutPage.kt" -> listOf(
                    "/project/app/src/main/java/com/example/pages/AboutPage.kt",
                )
                else -> emptyList()
            }
        }

        assertEquals("/project/app/src/main/java/com/example/pages/AboutPage.kt", source?.filePath)
        assertEquals(73, source?.line)
        assertEquals("AboutPage", source?.symbolName)
    }

    @Test
    fun `framework package match cannot outrank application source layout`() {
        val source = resolveRuntimeSource(
            callSites = listOf(
                callSite(
                    className = "com.viewcompose.host.android.runtime.AndroidFrameAlignedRenderSessionRuntime",
                    methodName = "render",
                    fileName = "AndroidFrameAlignedRenderSessionRuntime.kt",
                    lineNumber = 50,
                ),
                callSite(
                    className = "com.viewcompose.DemoAboutPageKt",
                    methodName = "AboutPage",
                    fileName = "DemoAboutPage.kt",
                    lineNumber = 53,
                ),
            ),
            findCandidatePaths = { fileName ->
                when (fileName) {
                    "AndroidFrameAlignedRenderSessionRuntime.kt" -> listOf(
                        "/project/viewcompose-host-android/src/main/java/com/viewcompose/host/android/" +
                            "runtime/AndroidFrameAlignedRenderSessionRuntime.kt",
                    )
                    "DemoAboutPage.kt" -> listOf(
                        "/project/app/src/main/java/com/viewcompose/demo/pages/about/DemoAboutPage.kt",
                    )
                    else -> emptyList()
                }
            },
        )

        assertEquals(
            "/project/app/src/main/java/com/viewcompose/demo/pages/about/DemoAboutPage.kt",
            source?.filePath,
        )
        assertEquals(53, source?.line)
        assertEquals("AboutPage", source?.symbolName)
    }

    @Test
    fun `falls back to framework source when no application source exists`() {
        val source = resolveRuntimeSource(
            callSites = listOf(
                callSite(
                    className = "com.viewcompose.widget.material.TextKt",
                    methodName = "Text",
                    fileName = "Text.kt",
                    lineNumber = 42,
                ),
            ),
            findCandidatePaths = {
                listOf(
                    "/project/viewcompose-ui-foundation/src/main/java/com/viewcompose/widget/material/Text.kt",
                )
            },
        )

        assertEquals(
            "/project/viewcompose-ui-foundation/src/main/java/com/viewcompose/widget/material/Text.kt",
            source?.filePath,
        )
        assertEquals(42, source?.line)
    }

    @Test
    fun `prefers preview DSL source over runtime observation wrapper`() {
        val runtime = callSite(
            className = "com.viewcompose.runtime.observation.RuntimeObservation",
            methodName = "observeReads",
            fileName = "RuntimeObservation.kt",
            lineNumber = 73,
        )
        val application = callSite(
            className = "com.viewcompose.StaticDemoPreviewEntrypointsKt",
            methodName = "StaticDemoPreview",
            fileName = "StaticDemoPreviewEntrypoints.kt",
            lineNumber = 41,
        )

        val source = resolveRuntimeSource(listOf(runtime, application)) { fileName ->
            when (fileName) {
                "RuntimeObservation.kt" -> listOf(
                    "/project/viewcompose-runtime/src/main/java/com/viewcompose/runtime/" +
                        "observation/RuntimeObservation.kt",
                )
                "StaticDemoPreviewEntrypoints.kt" -> listOf(
                    "/project/app/src/debug/java/com/viewcompose/StaticDemoPreviewEntrypoints.kt",
                )
                else -> emptyList()
            }
        }

        assertEquals(
            "/project/app/src/debug/java/com/viewcompose/StaticDemoPreviewEntrypoints.kt",
            source?.filePath,
        )
        assertEquals(41, source?.line)
        assertEquals("StaticDemoPreview", source?.symbolName)
    }

    @Test
    fun `uses package match to disambiguate duplicate file names`() {
        val source = resolveRuntimeSource(
            callSites = listOf(
                callSite(
                    className = "com.example.feature.CardKt",
                    methodName = "Card",
                    fileName = "Card.kt",
                    lineNumber = 19,
                ),
            ),
            findCandidatePaths = {
                listOf(
                    "/project/app/src/main/java/com/other/feature/Card.kt",
                    "/project/feature/src/main/java/com/example/feature/Card.kt",
                )
            },
        )

        assertEquals("/project/feature/src/main/java/com/example/feature/Card.kt", source?.filePath)
    }

    @Test
    fun `deprioritizes generated sources`() {
        val source = resolveRuntimeSource(
            callSites = listOf(
                callSite(
                    className = "com.example.GeneratedPreviewKt",
                    methodName = "Preview",
                    fileName = "GeneratedPreview.kt",
                    lineNumber = 11,
                ),
            ),
            findCandidatePaths = {
                listOf(
                    "/project/app/build/generated/com/example/GeneratedPreview.kt",
                    "/project/app/src/main/java/com/example/GeneratedPreview.kt",
                )
            },
        )

        assertEquals("/project/app/src/main/java/com/example/GeneratedPreview.kt", source?.filePath)
    }

    @Test
    fun `candidate resolution removes shared scaffold wrapping authored content`() {
        val scaffold = callSite(
            className = "com.example.demo.DemoSubPageScaffoldKt",
            methodName = "DemoSubPageScaffold",
            fileName = "DemoSubPageScaffold.kt",
            lineNumber = 61,
        )
        val settings = callSite(
            className = "com.example.demo.DemoSettingsPageKt",
            methodName = "SettingsPage",
            fileName = "DemoSettingsPage.kt",
            lineNumber = 57,
        )

        val sources = resolveRuntimeSourceCandidates(
            sourceCandidates = listOf(
                listOf(scaffold),
                listOf(settings, scaffold),
                listOf(scaffold.copy(lineNumber = 76)),
            ),
            findCandidatePaths = { fileName ->
                when (fileName) {
                    "DemoSubPageScaffold.kt" -> listOf(
                        "/project/app/src/main/java/com/example/demo/DemoSubPageScaffold.kt",
                    )
                    "DemoSettingsPage.kt" -> listOf(
                        "/project/app/src/main/java/com/example/demo/DemoSettingsPage.kt",
                    )
                    else -> emptyList()
                }
            },
        )

        assertEquals(1, sources.size)
        assertEquals(
            "/project/app/src/main/java/com/example/demo/DemoSettingsPage.kt",
            sources.single().filePath,
        )
        assertEquals(57, sources.single().line)
        assertEquals("SettingsPage", sources.single().symbolName)
    }

    @Test
    fun `candidate resolution preserves independent content sources for explicit choice`() {
        val first = callSite(
            className = "com.example.demo.FirstPaneKt",
            methodName = "FirstPane",
            fileName = "FirstPane.kt",
            lineNumber = 20,
        )
        val second = callSite(
            className = "com.example.demo.SecondPaneKt",
            methodName = "SecondPane",
            fileName = "SecondPane.kt",
            lineNumber = 30,
        )

        val sources = resolveRuntimeSourceCandidates(
            sourceCandidates = listOf(listOf(first), listOf(second)),
            findCandidatePaths = { fileName ->
                listOf("/project/app/src/main/java/com/example/demo/$fileName")
            },
        )

        assertEquals(listOf("FirstPane", "SecondPane"), sources.map { it.symbolName })
    }

    private fun callSite(
        className: String,
        methodName: String,
        fileName: String,
        lineNumber: Int,
    ): StudioPreviewSourceCallSite {
        return StudioPreviewSourceCallSite(
            className = className,
            methodName = methodName,
            fileName = fileName,
            lineNumber = lineNumber,
        )
    }
}
