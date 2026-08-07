package com.viewcompose

import com.viewcompose.animation.core.MotionRole
import com.viewcompose.animation.core.SnapSpec
import com.viewcompose.ui.shape.UiCornerFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDesignSystemBundleTest {
    @Test
    fun `contrast fixture differs in palette shape structure and sizing`() {
        val rounded = DemoDesignSystemBundles.resolve(
            kind = DemoDesignSystemKind.RoundedReference,
            dark = false,
            reducedMotionEnabled = false,
        )
        val cut = DemoDesignSystemBundles.resolve(
            kind = DemoDesignSystemKind.CutContrast,
            dark = false,
            reducedMotionEnabled = false,
        )

        assertNotEquals(rounded.tokens.colors.primary, cut.tokens.colors.primary)
        assertEquals(UiCornerFamily.Rounded, rounded.recipes.action.shape.topStart.family)
        assertEquals(UiCornerFamily.Cut, cut.recipes.action.shape.topStart.family)
        assertNotEquals(rounded.recipes.action.minimumHeight, cut.recipes.action.minimumHeight)
        assertNotEquals(rounded.recipes.switch.placement, cut.recipes.switch.placement)
        assertNotEquals(rounded.recipes.navigation.selectedOnlyLabels, cut.recipes.navigation.selectedOnlyLabels)
    }

    @Test
    fun `dark bundles keep identity while resolving separate tokens`() {
        DemoDesignSystemKind.entries.forEach { kind ->
            val light = DemoDesignSystemBundles.resolve(kind, dark = false, reducedMotionEnabled = false)
            val dark = DemoDesignSystemBundles.resolve(kind, dark = true, reducedMotionEnabled = false)

            assertEquals(kind, light.kind)
            assertEquals(kind, dark.kind)
            assertEquals(false, light.tokens.metadata.isDark)
            assertEquals(true, dark.tokens.metadata.isDark)
            assertNotEquals(light.tokens.colors.background, dark.tokens.colors.background)
        }
    }

    @Test
    fun `reduced motion resolves non essential effects without changing recipes`() {
        val normal = DemoDesignSystemBundles.resolve(
            DemoDesignSystemKind.CutContrast,
            dark = false,
            reducedMotionEnabled = false,
        )
        val reduced = DemoDesignSystemBundles.resolve(
            DemoDesignSystemKind.CutContrast,
            dark = false,
            reducedMotionEnabled = true,
        )

        assertEquals(normal.recipes, reduced.recipes)
        assertSame(
            SnapSpec,
            reduced.motion.resolve(
                role = MotionRole.DefaultSpatial,
                reducedMotionEnabled = true,
                essential = false,
            ),
        )
    }

    @Test
    fun `every five component outcome is attributable`() {
        val bundle = DemoDesignSystemBundles.resolve(
            DemoDesignSystemKind.CutContrast,
            dark = false,
            reducedMotionEnabled = false,
        )

        assertEquals(
            setOf("Button", "Surface/Card", "Switch", "TextField", "NavigationBar"),
            bundle.conformance.take(5).map { it.component }.toSet(),
        )
        assertTrue(bundle.conformance.all { it.implementation.isNotBlank() })
        assertEquals(DemoConformanceOutcome.Degraded, bundle.conformance.last().outcome)
        assertEquals("tinted surface", bundle.conformance.last().fallback)
    }
}
