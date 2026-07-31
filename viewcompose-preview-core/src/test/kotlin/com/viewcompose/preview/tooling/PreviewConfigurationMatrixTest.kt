package com.viewcompose.preview.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewConfigurationMatrixTest {
    @Test
    fun `preset matrix produces deterministic cartesian variants`() {
        val variants = PreviewConfigurationMatrix(
            axes = listOf(
                PreviewConfigurationPresets.Theme,
                PreviewConfigurationPresets.LayoutDirection,
                PreviewConfigurationPresets.Device,
                PreviewConfigurationPresets.FontScale,
            ),
        ).variants()

        assertEquals(24, variants.size)
        assertEquals(variants.size, variants.map(PreviewVariant::id).distinct().size)
        assertEquals(
            "theme-light__layout-direction-ltr__device-phone__font-scale-font-default",
            variants.first().id,
        )
        assertEquals(
            "Light / LTR · en-US / Phone / Font 100%",
            variants.first().displayName,
        )
        val last = variants.last().configuration
        val first = variants.first().configuration
        assertEquals(411, first.widthDp)
        assertEquals(891, first.heightDp)
        assertEquals(2.625f, first.density)
        assertEquals(PreviewTheme.Dark, last.theme)
        assertEquals(PreviewLayoutDirection.Rtl, last.layoutDirection)
        assertEquals(listOf("ar-EG"), last.localeTags)
        assertEquals(800, last.widthDp)
        assertEquals(1280, last.heightDp)
        assertEquals(1.5f, last.density)
        assertEquals(2f, last.fontScale)
    }

    @Test
    fun `later matrix axes override earlier configuration fields`() {
        val matrix = PreviewConfigurationMatrix(
            base = PreviewConfiguration(
                theme = PreviewTheme.Dark,
                widthDp = 320,
            ),
            axes = listOf(
                PreviewConfigurationPresets.Theme,
                PreviewConfigurationAxis(
                    id = "custom-size",
                    displayName = "Custom size",
                    options = listOf(
                        PreviewConfigurationOption(
                            id = "wide",
                            displayName = "Wide",
                            override = PreviewConfigurationOverride(widthDp = 600),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(matrix.variants().all { variant -> variant.configuration.widthDp == 600 })
        assertEquals(PreviewTheme.Light, matrix.variants().first().configuration.theme)
        assertEquals(PreviewTheme.Dark, matrix.variants().last().configuration.theme)
    }

    @Test
    fun `matrix and protocol reject unstable or ambiguous identifiers`() {
        assertThrows(IllegalArgumentException::class.java) {
            PreviewConfigurationOption(
                id = "../phone",
                displayName = "Unsafe",
                override = PreviewConfigurationOverride(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PreviewConfigurationMatrix(
                axes = listOf(
                    PreviewConfigurationPresets.Theme,
                    PreviewConfigurationPresets.Theme,
                ),
            )
        }
        assertEquals(
            "sample-preview/theme-dark__device-tablet",
            PreviewArtifactLayout.relativeDirectory(
                previewId = "sample-preview",
                variantId = "theme-dark__device-tablet",
            ),
        )
    }

    @Test
    fun `built in multi previews expose runtime meta annotations`() {
        val lightDark = PreviewLightDark::class.java
            .getAnnotationsByType(ViewComposePreview::class.java)
        val directions = PreviewLtrRtl::class.java
            .getAnnotationsByType(ViewComposePreview::class.java)
        val fontScales = PreviewFontScales::class.java
            .getAnnotationsByType(ViewComposePreview::class.java)

        assertEquals(listOf(PreviewTheme.Light, PreviewTheme.Dark), lightDark.map { it.theme })
        assertEquals(
            listOf(PreviewLayoutDirection.Ltr, PreviewLayoutDirection.Rtl),
            directions.map { it.layoutDirection },
        )
        assertEquals(listOf(1f, 1.3f, 2f), fontScales.map { it.fontScale })
    }
}
