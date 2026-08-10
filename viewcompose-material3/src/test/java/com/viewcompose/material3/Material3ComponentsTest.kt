package com.viewcompose.material3

import com.viewcompose.text.TextFieldState
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.DesignSystemDiagnostics
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiDesignSystemAttribution
import com.viewcompose.ui.foundation.buildVNodeTree
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.NavigationBarNodeProps
import com.viewcompose.ui.node.spec.SurfaceNodeProps
import com.viewcompose.ui.node.spec.ToggleNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Material3ComponentsTest {
    @Test
    fun `named pressure slice resolves recipes above neutral renderer nodes`() {
        var attribution: UiDesignSystemAttribution? = null
        val tree = buildVNodeTree {
            Material3Theme {
                attribution = DesignSystemDiagnostics.current
                Column {
                    Material3Surface { }
                    Material3Card(variant = Material3CardVariant.Outlined) { }
                    Material3Button(text = "Continue", onClick = {})
                    Material3Switch(text = "Enabled", checked = true, onCheckedChange = {})
                    Material3TextField(state = TextFieldState(), label = "Account")
                    Material3NavigationBar(selectedIndex = 0, onItemSelected = {}) {
                        Item("Home", ImageSource.Resource(android.R.drawable.ic_menu_view))
                        Item("Settings", ImageSource.Resource(android.R.drawable.ic_menu_preferences))
                    }
                }
            }
        }
        val nodes = tree.flatten()

        assertEquals(Material3Reference.designSystem, attribution?.designSystemId)
        assertEquals(Material3Reference.recipeSet, attribution?.recipeSetId)
        assertEquals(
            listOf("button", "navigation-bar", "surface-card", "switch", "text-field"),
            attribution?.components?.map { it.familyId }?.sorted(),
        )
        assertEquals(
            "material-components/snackbar",
            attribution?.integration("overlay.snackbar")?.presenterId,
        )
        assertEquals(
            "material-components/bottom-sheet-dialog",
            attribution?.integration("overlay.modal-bottom-sheet")?.presenterId,
        )
        assertFalse(nodes.any { node -> node.type == NodeType.Button })
        assertTrue(nodes.count { node -> node.type == NodeType.Surface } >= 3)
        assertEquals(1, nodes.count { node -> node.type == NodeType.Switch })
        assertEquals(1, nodes.count { node -> node.type == NodeType.TextField })
        assertEquals(1, nodes.count { node -> node.type == NodeType.NavigationBar })
    }

    @Test
    fun `pressure components use Material semantic roles and geometry`() {
        val tokens = Material3ThemeDefaults.light()
        val nodes = buildVNodeTree {
            Material3Theme(tokens) {
                Material3Button(text = "Tonal", onClick = {}, variant = Material3ButtonVariant.FilledTonal)
                Material3Switch(text = "Enabled", checked = true, onCheckedChange = {})
                Material3NavigationBar(selectedIndex = 0, onItemSelected = {}) {
                    Item("Home", ImageSource.Resource(android.R.drawable.ic_menu_view))
                    Item("Settings", ImageSource.Resource(android.R.drawable.ic_menu_preferences))
                }
            }
        }

        val button = nodes[0].spec as SurfaceNodeProps
        val switch = nodes[1].spec as ToggleNodeProps
        val navigation = nodes[2].spec as NavigationBarNodeProps
        assertEquals(48f, button.minimumHeight.value)
        assertEquals(40f, button.visualHeight?.value)
        assertEquals(tokens.colors.primary, switch.trackColor)
        assertEquals(tokens.colors.onPrimary, switch.thumbColor)
        assertEquals(tokens.colors.secondaryContainer, navigation.indicatorColor)
        assertEquals(tokens.colors.onSecondaryContainer, navigation.selectedIconColor)
    }

    @Test
    fun `components require an explicit Material theme`() {
        val failure = runCatching {
            buildVNodeTree {
                Material3Button(text = "Continue", onClick = {})
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("Material3Theme"))
    }

    @Test
    fun `static theme exports stable token producer identity`() {
        var sourceId = ""
        buildVNodeTree {
            Material3Theme {
                sourceId = Theme.current.metadata.provenance.sourceId
            }
        }

        assertEquals("viewcompose-material3/static", sourceId)
    }
}

private fun List<VNode>.flatten(): List<VNode> = buildList {
    fun visit(node: VNode) {
        add(node)
        node.children.forEach(::visit)
    }
    this@flatten.forEach(::visit)
}
