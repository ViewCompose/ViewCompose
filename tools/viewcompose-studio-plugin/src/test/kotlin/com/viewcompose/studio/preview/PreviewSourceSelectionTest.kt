package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewSourceSelectionTest {
    @Test
    fun `preserves a valid source location`() {
        val selection = PreviewSourceSelection(
            filePath = "/project/src/Sample.kt",
            symbolName = "SamplePreview",
            line = 42,
        )

        assertEquals("/project/src/Sample.kt", selection.filePath)
        assertEquals("SamplePreview", selection.symbolName)
        assertEquals(42, selection.line)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects an invalid source line`() {
        PreviewSourceSelection(
            filePath = "/project/src/Sample.kt",
            symbolName = "SamplePreview",
            line = 0,
        )
    }
}
