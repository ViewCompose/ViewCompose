package com.viewcompose.preview.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ViewComposePreviewTest {
    @Test
    fun `preview annotations stay out of runtime reflection`() {
        val annotations = PreviewFixtures::class.java
            .getDeclaredMethod("catalog")
            .getAnnotationsByType(ViewComposePreview::class.java)

        assertEquals(0, annotations.size)
    }

    @Test
    fun `annotation converts unspecified API level to resolved null`() {
        assertEquals(
            PreviewConfiguration(),
            ViewComposePreview().toPreviewConfiguration(),
        )
    }

    @Test
    fun `configuration rejects nondeterministic invalid dimensions`() {
        assertThrows(IllegalArgumentException::class.java) {
            PreviewConfiguration(widthDp = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PreviewConfiguration(fontScale = Float.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PreviewConfiguration(localeTags = emptyList())
        }
    }

    private class PreviewFixtures {
        @ViewComposePreview(name = "Catalog Light")
        @ViewComposePreview(
            name = "Catalog Dark RTL",
            theme = PreviewTheme.Dark,
            layoutDirection = PreviewLayoutDirection.Rtl,
        )
        fun catalog() = Unit

        @ViewComposePreview
        fun defaultConfiguration() = Unit
    }
}
