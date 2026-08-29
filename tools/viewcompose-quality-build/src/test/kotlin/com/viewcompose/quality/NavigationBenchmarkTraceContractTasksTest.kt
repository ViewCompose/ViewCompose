package com.viewcompose.quality

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NavigationBenchmarkTraceContractTasksTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `accepts synchronized runtime sections and benchmark labels`() {
        val repository = temporaryFolder.newFolder("accepted")
        val runtime = repository.resolve("Runtime.kt").apply {
            writeText(
                """
                trace("VC.Nav.PrepareCommand")
                trace("VC.Nav.PreparePresentations")
                trace("VC.FrameRender")
                trace("VC.RenderTree")
                trace("VC.Nav.MotionFrame")
                """.trimIndent(),
            )
        }
        val benchmark = repository.resolve("Benchmark.kt").apply {
            writeText(acceptedBenchmarkSource)
        }

        val outcome = NavigationBenchmarkTraceContractVerifier.verify(
            repository = repository,
            benchmarkSource = benchmark,
            runtimeSources = setOf(runtime),
        )

        assertTrue(outcome.diagnostics.joinToString(), outcome.succeeded)
    }

    @Test
    fun `rejects stale section and missing presentation collector`() {
        val repository = temporaryFolder.newFolder("rejected")
        val runtime = repository.resolve("Runtime.kt").apply {
            writeText(
                acceptedRuntimeSource.replace(
                    "trace(\"VC.Nav.PreparePresentations\")",
                    "",
                ),
            )
        }
        val benchmark = repository.resolve("Benchmark.kt").apply {
            writeText(
                acceptedBenchmarkSource
                    .replace(
                        "sectionName = \"VC.Nav.PreparePresentations\", " +
                            "label = \"navPreparePresentations\"",
                        "sectionName = \"VC.Nav.PrepareDestination\", " +
                            "label = \"navPreparePresentations\"",
                    ),
            )
        }

        val outcome = NavigationBenchmarkTraceContractVerifier.verify(
            repository = repository,
            benchmarkSource = benchmark,
            runtimeSources = setOf(runtime),
        )

        assertFalse(outcome.succeeded)
        assertTrue(
            outcome.diagnostics.any { diagnostic ->
                diagnostic.contains("runtime does not emit VC.Nav.PreparePresentations")
            },
        )
        assertTrue(
            outcome.diagnostics.any { diagnostic ->
                diagnostic.contains("benchmark does not collect VC.Nav.PreparePresentations")
            },
        )
        assertTrue(
            outcome.diagnostics.any { diagnostic ->
                diagnostic.contains("obsolete section VC.Nav.PrepareDestination")
            },
        )
    }

    private companion object {
        val acceptedRuntimeSource = """
            trace("VC.Nav.PrepareCommand")
            trace("VC.Nav.PreparePresentations")
            trace("VC.FrameRender")
            trace("VC.RenderTree")
            trace("VC.Nav.MotionFrame")
        """.trimIndent()

        val acceptedBenchmarkSource = """
            sectionName = "VC.Nav.PrepareCommand", label = "navPrepareCommand"
            sectionName = "VC.Nav.PreparePresentations", label = "navPreparePresentations"
            sectionName = "VC.FrameRender", label = "frameRenderCount"
            sectionName = "VC.RenderTree", label = "renderTreeMax"
            sectionName = "VC.Nav.MotionFrame", label = "navMotionFrameMax"
        """.trimIndent()
    }
}
