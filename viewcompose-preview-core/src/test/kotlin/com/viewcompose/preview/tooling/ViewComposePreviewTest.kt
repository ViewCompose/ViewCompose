package com.viewcompose.preview.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ViewComposePreviewTest {
    @Test
    fun `repeatable annotations retain independent configurations at runtime`() {
        val annotations = PreviewFixtures::class.java
            .getDeclaredMethod("catalog")
            .getAnnotationsByType(ViewComposePreview::class.java)

        assertEquals(2, annotations.size)
        assertEquals(PreviewTheme.Light, annotations[0].theme)
        assertEquals(PreviewTheme.Dark, annotations[1].theme)
        assertEquals(PreviewLayoutDirection.Rtl, annotations[1].layoutDirection)
    }

    @Test
    fun `annotation converts unspecified API level to resolved null`() {
        val annotation = PreviewFixtures::class.java
            .getDeclaredMethod("defaultConfiguration")
            .getAnnotation(ViewComposePreview::class.java)

        assertEquals(
            PreviewConfiguration(),
            annotation.toPreviewConfiguration(),
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
