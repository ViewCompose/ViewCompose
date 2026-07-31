package com.viewcompose.preview.gradle

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreviewBatchTargetsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `reads ordered unique preview and variant targets`() {
        val file = temporaryFolder.newFile("targets.tsv").apply {
            writeText("first-card\tdefault\nfirst-card\tdark\nsecond-card\tdefault\n")
        }

        assertEquals(
            listOf(
                PreviewBatchTarget("first-card", "default"),
                PreviewBatchTarget("first-card", "dark"),
                PreviewBatchTarget("second-card", "default"),
            ),
            file.readPreviewBatchTargets(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects duplicate targets`() {
        val file = temporaryFolder.newFile("duplicates.tsv").apply {
            writeText("first-card\tdefault\nfirst-card\tdefault\n")
        }

        file.readPreviewBatchTargets()
    }
}
