package com.viewcompose.ui.foundation

import com.viewcompose.ui.modifier.SemanticsCollectionSelectionMode
import com.viewcompose.ui.modifier.SemanticsModifierElement
import com.viewcompose.ui.modifier.SemanticsRole
import com.viewcompose.ui.node.spec.TabRowNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabRowAppearanceTest {
    @Test
    fun `tab row merges scoped appearance and instance overrides`() {
        val tree = buildVNodeTree {
            ProvideTabRowOverrides(
                TabRowOverrides(
                    containerColor = 101,
                    indicatorColor = 102,
                    itemSpacing = 3.dp,
                ),
            ) {
                ProvideTabRowOverrides(
                    TabRowOverrides(itemPaddingHorizontal = 11.dp),
                ) {
                    TabRow(
                        selectedIndex = 0,
                        onTabSelected = {},
                        overrides = TabRowOverrides(indicatorColor = 201),
                    ) {
                        Tab(key = "first", contentRevision = StaticContentRevision) { Text("First") }
                        Tab(key = "second", contentRevision = StaticContentRevision) { Text("Second") }
                    }
                }
            }
        }

        val spec = tree.single().spec as TabRowNodeProps

        assertEquals(101, spec.containerColor)
        assertEquals(201, spec.indicatorColor)
        assertEquals(3.dp, spec.itemSpacing)
        assertEquals(11.dp, spec.itemPaddingHorizontal)
        val collection = tree.single().modifier.elements
            .filterIsInstance<SemanticsModifierElement>()
            .single()
            .configuration
        assertEquals(2, collection.collectionInfo?.columnCount)
        assertEquals(SemanticsCollectionSelectionMode.Single, collection.collectionInfo?.selectionMode)
        val first = tree.single().children[0].modifier.elements
            .filterIsInstance<SemanticsModifierElement>()
            .single()
            .configuration
        val second = tree.single().children[1].modifier.elements
            .filterIsInstance<SemanticsModifierElement>()
            .single()
            .configuration
        assertEquals(SemanticsRole.Tab, first.role)
        assertEquals(0, first.collectionItemInfo?.columnIndex)
        assertTrue(first.selected == true)
        assertEquals(1, second.collectionItemInfo?.columnIndex)
        assertFalse(second.selected == true)
    }
}
