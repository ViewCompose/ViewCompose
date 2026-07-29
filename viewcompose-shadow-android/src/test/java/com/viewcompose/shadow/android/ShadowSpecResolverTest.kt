package com.viewcompose.shadow.android

import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.modifier.DropShadowModifierElement
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ShadowSpecResolverTest {
    private val density = UiDensity(
        density = 2f,
        fontScale = 1f,
    )

    @Test
    fun `empty modifier list returns stable empty spec`() {
        val resolved = ShadowSpecResolver.resolve(
            elements = emptyList(),
            defaultShape = null,
            density = density,
        )

        assertSame(ResolvedShadowSpec.Empty, resolved)
    }

    @Test
    fun `resolves every layer in declaration order at the renderer density`() {
        val first = UiShadow(
            color = 0x22000000,
            blurRadius = 4.dp,
            offsetY = 2.dp,
        )
        val second = UiShadow(
            color = 0x33000000,
            blurRadius = 12.dp,
            spreadRadius = (-1).dp,
            offsetX = 3.dp,
        )

        val resolved = ShadowSpecResolver.resolve(
            elements = listOf(
                DropShadowModifierElement(
                    shadows = listOf(first, second),
                ),
            ),
            defaultShape = UiShape.rounded(8.dp),
            density = density,
        )

        assertEquals(2, resolved.layerCount)
        assertEquals(UiShape.rounded(8.dp), resolved.groups.single().shape)
        assertEquals(8f, resolved.groups.single().shadows[0].blurRadiusPx)
        assertEquals(4f, resolved.groups.single().shadows[0].offsetYPx)
        assertEquals(24f, resolved.groups.single().shadows[1].blurRadiusPx)
        assertEquals(-2f, resolved.groups.single().shadows[1].spreadRadiusPx)
        assertEquals(6f, resolved.groups.single().shadows[1].offsetXPx)
    }

    @Test
    fun `explicit group shape wins over the node shape`() {
        val explicitShape = UiShape.cut(6.dp)

        val resolved = ShadowSpecResolver.resolve(
            elements = listOf(
                DropShadowModifierElement(
                    shadows = listOf(UiShadow(blurRadius = 4.dp)),
                    shape = explicitShape,
                ),
            ),
            defaultShape = UiShape.rounded(20.dp),
            density = density,
        )

        assertEquals(explicitShape, resolved.groups.single().shape)
    }
}
