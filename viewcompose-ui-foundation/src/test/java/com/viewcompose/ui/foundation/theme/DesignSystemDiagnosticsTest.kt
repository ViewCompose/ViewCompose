package com.viewcompose.ui.foundation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesignSystemDiagnosticsTest {
    @Test
    fun `token provenance resolves exact family and default sources`() {
        val provenance = UiTokenProvenance(
            sourceId = "viewcompose-material3/android-xml",
            defaultOrigin = UiThemeOrigin.Custom,
            tokenOrigins = mapOf(
                "colors" to UiThemeOrigin.AndroidTheme,
                "colors.primary" to UiThemeOrigin.AndroidDynamicColor,
            ),
        )

        assertEquals(UiThemeOrigin.AndroidDynamicColor, provenance.originOf("colors.primary"))
        assertEquals(UiThemeOrigin.AndroidTheme, provenance.originOf("colors.surface"))
        assertEquals(UiThemeOrigin.Custom, provenance.originOf("shapes.medium"))
    }

    @Test
    fun `theme override marks only replaced token families`() {
        val base = UiThemeDefaults.light()
        val overridden = base.override(
            colors = base.colors.copy(primary = 0xFF123456.toInt()),
            shapes = base.shapes,
        )

        assertEquals(UiThemeOrigin.Override, overridden.metadata.origin)
        assertEquals(UiThemeOrigin.Override, overridden.metadata.provenance.originOf("colors.primary"))
        assertEquals(UiThemeOrigin.Override, overridden.metadata.provenance.originOf("stateColors.control"))
        assertEquals(UiThemeOrigin.Override, overridden.metadata.provenance.originOf("shapes.medium"))
        assertEquals(
            UiThemeOrigin.FrameworkDefault,
            overridden.metadata.provenance.originOf("typography.bodyMedium"),
        )
    }

    @Test
    fun `attribution provider scopes and restores one immutable snapshot`() {
        val attribution = UiDesignSystemAttribution(
            designSystemId = "test-system",
            recipeSetId = "pressure-v1",
            components = listOf(
                UiComponentAttribution(
                    familyId = "switch",
                    recipeId = "test-switch-v1",
                    backend = UiComponentBackend.NativeBehavioralCore,
                    conformance = UiDesignConformance.Equivalent,
                    capabilityPath = "android-switch",
                ),
            ),
        )
        var inside: UiDesignSystemAttribution? = null
        var outside: UiDesignSystemAttribution? = attribution

        buildVNodeTree {
            DesignSystemAttributionProvider(attribution) {
                inside = DesignSystemDiagnostics.current
            }
            outside = DesignSystemDiagnostics.current
        }

        assertEquals(attribution, inside)
        assertNull(outside)
    }
}
