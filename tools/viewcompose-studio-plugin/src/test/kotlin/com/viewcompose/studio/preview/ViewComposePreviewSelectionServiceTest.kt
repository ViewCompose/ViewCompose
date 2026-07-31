package com.viewcompose.studio.preview

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ViewComposePreviewSelectionServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `refreshes only when the selected preview source changes`() {
        val source = temporaryFolder.newFile("SamplePreview.kt").toPath()
        val sibling = temporaryFolder.newFile("Other.kt").toPath()
        val selection = PreviewSourceSelection(
            filePath = source.toString(),
            symbolName = "SamplePreview",
            line = 12,
        )

        assertTrue(
            savedSourceMatches(
                selection = selection,
                changedPaths = listOf(source.toString()),
            ),
        )
        assertFalse(
            savedSourceMatches(
                selection = selection,
                changedPaths = listOf(sibling.toString()),
            ),
        )
        assertFalse(
            savedSourceMatches(
                selection = selection,
                changedPaths = listOf(
                    Files.createDirectories(
                        temporaryFolder.root.toPath().resolve("build/generated"),
                    ).resolve("SamplePreview.kt").toString(),
                ),
            ),
        )
    }
}
