package com.viewcompose.studio.preview

import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Test

class PresentableProjectPathTest {
    @Test
    fun `presents a source inside the project relative to its root`() {
        val projectRoot = Path.of("/workspace/ViewCompose")
        val source = projectRoot.resolve(
            "app/src/debug/java/com/viewcompose/StaticDemoPreviewEntrypoints.kt",
        )

        assertEquals(
            "app/src/debug/java/com/viewcompose/StaticDemoPreviewEntrypoints.kt",
            presentableProjectPath(projectRoot, source.toString()),
        )
    }

    @Test
    fun `keeps an external source absolute so its origin remains unambiguous`() {
        val externalSource = Path.of("/tmp/GeneratedPreview.kt").toAbsolutePath().normalize()

        assertEquals(
            externalSource.toString(),
            presentableProjectPath(
                projectRoot = Path.of("/workspace/ViewCompose"),
                filePath = externalSource.toString(),
            ),
        )
    }
}
