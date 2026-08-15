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
                overrides = FloatingActionButtonOverrides(contentColor = contentColor),
            ) {
                Icon(source = ImageSource.Resource(1))
            }
            ExtendedFloatingActionButton(
                text = "Create",
                onClick = {},
                overrides = ExtendedFloatingActionButtonOverrides(contentColor = contentColor),
            )
        }

        assertEquals(stateLayerColorsFor(contentColor), tree[0].requireStateLayerColors())
        assertEquals(stateLayerColorsFor(contentColor), tree[1].requireStateLayerColors())
    }

    @Test
    fun `clickable surfaces and cards emit state layers while passive variants do not`() {
        val tree = buildVNodeTree {
            Surface(onClick = {}) {}
            Surface(onClick = null) {}
            Card(onClick = {}) {}
            Card(onClick = null) {}
        }

        assertEquals(
            stateLayerColorsFor(SurfaceDefaults.contentColor()),
            tree[0].requireStateLayerColors(),
        )
        assertNull(tree[1].stateLayerColorsOrNull())
        assertEquals(
            stateLayerColorsFor(CardDefaults.contentColor()),
            tree[2].requireStateLayerColors(),
        )
        assertNull(tree[3].stateLayerColorsOrNull())
    }

    @Test
    fun `clickable list and menu items resolve row state layers`() {
        val tree = buildVNodeTree {
            ListItem(headlineText = "Inbox", onClick = {})
            DropdownMenuItem(text = "Rename", onClick = {})
        }

        assertEquals(stateLayerColorsFor(Theme.colors.onSurface), tree[0].requireStateLayerColors())
        assertEquals(
            stateLayerColorsFor(DropdownMenuDefaults.contentColor()),
            tree[1].requireStateLayerColors(),
        )
    }
}
