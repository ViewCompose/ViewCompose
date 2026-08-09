package com.viewcompose.oneui7

import com.viewcompose.text.TextFieldState
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.buildVNodeTree
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.SurfaceNodeProps
import com.viewcompose.ui.shape.UiCornerFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OneUi7ComponentsTest {
    @Test
    fun themeDefaultsExposePinnedLightAndDarkSnapshots() {
        val light = OneUi7ThemeDefaults.light()
        val dark = OneUi7ThemeDefaults.dark()

        assertEquals(false, light.metadata.isDark)
        assertEquals(true, dark.metadata.isDark)
        assertEquals(0xFF006FFD.toInt(), light.colors.primary)
        assertEquals(0xFF5FA2FF.toInt(), dark.colors.primary)
        assertEquals(48f, light.controls.button.mediumHeight.value)
        assertTrue(
            listOf(
                light.shapes.large.topStart,
                light.shapes.large.topEnd,
                light.shapes.large.bottomEnd,
                light.shapes.large.bottomStart,
            ).all { corner -> corner.family == UiCornerFamily.Rounded },
        )
    }

    @Test
    fun sampleEmitsFiveComponentsWithoutNativeButtonNode() {
        val tree = buildVNodeTree {
            OneUi7Theme {
                Column {
                    OneUi7Button(text = "Continue", onClick = {})
                    OneUi7Surface {
                        Text(
                            text = "Surface",
                            color = Theme.colors.onSurface,
                            style = Theme.typography.bodyMedium,
                        )
                    }
                    OneUi7Switch(text = "Sync", checked = true, onCheckedChange = {})
                    OneUi7TextField(state = TextFieldState(), label = "Account")
                    OneUi7NavigationBar(
                        items = listOf(
                            OneUi7NavigationItem("home", "Home"),
                            OneUi7NavigationItem("search", "Search"),
                        ),
                        selectedIndex = 0,
                        onItemSelected = {},
                    )
                }
            }
        }
        val nodes = tree.flatten()

        assertFalse(nodes.any { node -> node.type == NodeType.Button })
        assertTrue(nodes.count { node -> node.type == NodeType.Surface } >= 8)
        assertEquals(1, nodes.count { node -> node.type == NodeType.TextField })
        assertTrue(
            nodes.filter { node -> node.type == NodeType.Surface }
                .map { node -> node.spec as SurfaceNodeProps }
                .any { props -> props.minimumHeight.value == 68f },
        )
    }

    @Test
    fun componentsRequireExplicitThemeIntegration() {
        val failure = runCatching {
            buildVNodeTree {
                OneUi7Button(text = "Continue", onClick = {})
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("OneUi7Theme"))
    }

    @Test
    fun navigationRejectsInvalidItemCountsAndSelection() {
        val oneItem = listOf(OneUi7NavigationItem(key = "only", label = "Only"))
        val twoItems = listOf(
            OneUi7NavigationItem(key = "first", label = "First"),
            OneUi7NavigationItem(key = "second", label = "Second"),
        )

        assertTrue(
            runCatching {
                buildVNodeTree {
                    OneUi7Theme {
                        OneUi7NavigationBar(oneItem, selectedIndex = 0, onItemSelected = {})
                    }
                }
            }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching {
                buildVNodeTree {
                    OneUi7Theme {
                        OneUi7NavigationBar(twoItems, selectedIndex = 2, onItemSelected = {})
                    }
                }
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun textFieldKeepsCallerOwnedNativeEditingState() {
        val state = TextFieldState()
        val nodes = buildVNodeTree {
            OneUi7Theme {
                OneUi7TextField(state = state, label = "Account")
            }
        }.flatten()

        assertEquals(1, nodes.count { node -> node.type == NodeType.TextField })
    }
}

private fun List<VNode>.flatten(): List<VNode> = buildList {
    fun visit(node: VNode) {
        add(node)
        node.children.forEach(::visit)
    }
    this@flatten.forEach(::visit)
}
