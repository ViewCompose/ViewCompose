package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.spec.TabRowNodeProps
import org.junit.Assert.assertEquals
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
                        Tab(key = "first") { Text("First") }
                        Tab(key = "second") { Text("Second") }
                    }
                }
            }
        }

        val spec = tree.single().spec as TabRowNodeProps

        assertEquals(101, spec.containerColor)
        assertEquals(201, spec.indicatorColor)
        assertEquals(3.dp, spec.itemSpacing)
        assertEquals(11.dp, spec.itemPaddingHorizontal)
    }
}
