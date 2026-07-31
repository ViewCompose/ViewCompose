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
                    "/project/viewcompose-widget-core/src/main/java/com/viewcompose/widget/material/Text.kt",
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
