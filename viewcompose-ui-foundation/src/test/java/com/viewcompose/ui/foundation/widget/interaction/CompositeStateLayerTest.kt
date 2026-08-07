package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.RowNodeProps
import com.viewcompose.ui.node.spec.SurfaceNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompositeStateLayerTest {
    @Test
    fun `fab and extended fab resolve interaction colors from actual content color`() {
        val contentColor = 0xFF123456.toInt()
        val tree = buildVNodeTree {
            FloatingActionButton(
                onClick = {},
                contentColor = contentColor,
            ) {
                Icon(source = ImageSource.Resource(1))
            }
            ExtendedFloatingActionButton(
                text = "Create",
                onClick = {},
                contentColor = contentColor,
            )
        }

        val fab = tree[0].spec as BoxNodeProps
        val extendedFab = tree[1].spec as RowNodeProps

        assertEquals(FabDefaults.pressedColor(), fab.rippleColor)
        assertEquals(stateLayerColorsFor(contentColor), fab.stateLayerColors)
        assertEquals(FabDefaults.pressedColor(), extendedFab.rippleColor)
        assertEquals(stateLayerColorsFor(contentColor), extendedFab.stateLayerColors)
    }

    @Test
    fun `clickable surfaces and cards emit state layers while passive variants do not`() {
        val tree = buildVNodeTree {
            Surface(onClick = {}) {}
            Surface(onClick = null) {}
            Card(onClick = {}) {}
            Card(onClick = null) {}
        }

        val clickableSurface = tree[0].spec as SurfaceNodeProps
        val passiveSurface = tree[1].spec as SurfaceNodeProps
        val clickableCard = tree[2].spec as BoxNodeProps
        val passiveCard = tree[3].spec as BoxNodeProps

        assertEquals(
            stateLayerColorsFor(SurfaceDefaults.contentColor()),
            clickableSurface.stateLayerColors,
        )
        assertNull(passiveSurface.stateLayerColors)
        assertEquals(
            stateLayerColorsFor(CardDefaults.contentColor()),
            clickableCard.stateLayerColors,
        )
        assertNull(passiveCard.stateLayerColors)
    }

    @Test
    fun `clickable list and menu items resolve row state layers`() {
        val tree = buildVNodeTree {
            ListItem(headlineText = "Inbox", onClick = {})
            DropdownMenuItem(text = "Rename", onClick = {})
        }

        val listItem = tree[0].spec as RowNodeProps
        val menuItem = tree[1].spec as RowNodeProps

        assertEquals(stateLayerColorsFor(Theme.colors.onSurface), listItem.stateLayerColors)
        assertEquals(
            stateLayerColorsFor(DropdownMenuDefaults.contentColor()),
            menuItem.stateLayerColors,
        )
    }
}
