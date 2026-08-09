package com.viewcompose.oneui7

import com.viewcompose.graphics.core.Brush
import com.viewcompose.text.TextFieldState
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.DesignSystemDiagnostics
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiDesignSystemAttribution
import com.viewcompose.ui.foundation.UiDesignConformance
import com.viewcompose.ui.foundation.UiStateColor
import com.viewcompose.ui.foundation.buildVNodeTree
import com.viewcompose.ui.modifier.SemanticsCollectionSelectionMode
import com.viewcompose.ui.modifier.SemanticsModifierElement
import com.viewcompose.ui.modifier.SizeModifierElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.SurfaceNodeProps
import com.viewcompose.ui.node.spec.TextFieldNodeProps
import com.viewcompose.ui.shape.UiCornerFamily
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDimension
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneUi7ComponentsTest {
    @Test
    fun themeDefaultsExposePinnedLightAndDarkSnapshots() {
        val light = OneUi7ThemeDefaults.light()
        val dark = OneUi7ThemeDefaults.dark()

        assertEquals(false, light.metadata.isDark)
        assertEquals(true, dark.metadata.isDark)
        assertEquals(0xFF0072DE.toInt(), light.colors.primary)
        assertEquals(0xFF3E91FF.toInt(), dark.colors.primary)
        assertEquals(0xFF3E91FF.toInt(), light.stateColors.controlActivated.checkedColor)
        assertEquals(48f, light.controls.button.mediumHeight.value)
        assertEquals(36f, light.controls.button.mediumVisualHeight.value)
        assertEquals(18f, light.shapes.medium.uniformAbsoluteSizeOrNull?.value)
        assertEquals("viewcompose-oneui7/static", light.metadata.provenance.sourceId)
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
    fun themeExportsFiveFamilyAttribution() {
        var attribution: UiDesignSystemAttribution? = null

        buildVNodeTree {
            OneUi7Theme {
                attribution = DesignSystemDiagnostics.current
            }
        }

        assertEquals("viewcompose-oneui7", attribution?.designSystemId)
        assertEquals(OneUi7Reference.componentSet, attribution?.recipeSetId)
        assertEquals(
            listOf("button", "navigation-bar", "surface-card", "switch", "text-field"),
            attribution?.components?.map { it.familyId }?.sorted(),
        )
        assertEquals(
            UiDesignConformance.Unsupported,
            attribution?.integration("overlay.modal-bottom-sheet")?.conformance,
        )
        assertEquals(
            "install-viewcompose-overlay-oneui7-android",
            attribution?.integration("overlay.snackbar")?.fallback,
        )
    }

    @Test
    fun staticComponentsEmitWithoutNativeButtonNode() {
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
        assertTrue(nodes.count { node -> node.type == NodeType.Surface } >= 4)
        assertEquals(1, nodes.count { node -> node.type == NodeType.TextField })
        assertTrue(
            nodes.filter { node -> node.type == NodeType.Surface }
                .map { node -> node.spec as SurfaceNodeProps }
                .any { props -> props.minimumHeight.value == 68f },
        )
        val semantics = nodes.flatMap { node ->
            node.modifier.elements.filterIsInstance<SemanticsModifierElement>()
                .map(SemanticsModifierElement::configuration)
        }
        val collection = semantics.single { configuration -> configuration.collectionInfo != null }
            .collectionInfo
        assertEquals(1, collection?.rowCount)
        assertEquals(2, collection?.columnCount)
        assertEquals(SemanticsCollectionSelectionMode.Single, collection?.selectionMode)
        assertEquals(
            listOf(0, 1),
            semantics.mapNotNull { configuration ->
                configuration.collectionItemInfo?.columnIndex
            }.sorted(),
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
    fun navigationSelectionRekeysAndRecolorsItsIndicator() {
        fun indicators(selectedIndex: Int): List<VNode> = buildVNodeTree {
            OneUi7Theme {
                OneUi7NavigationBar(
                    items = listOf(
                        OneUi7NavigationItem(key = "home", label = "Home"),
                        OneUi7NavigationItem(key = "search", label = "Search"),
                    ),
                    selectedIndex = selectedIndex,
                    onItemSelected = {},
                )
            }
        }.flatten().filter { node ->
            val size = node.modifier.elements.filterIsInstance<SizeModifierElement>().singleOrNull()
            size?.width == UiDimension.Exact(32.dp) && size.height == UiDimension.Exact(2.dp)
        }

        val homeSelected = indicators(selectedIndex = 0)
        val searchSelected = indicators(selectedIndex = 1)
        val primary = OneUi7ThemeDefaults.light().colors.primary

        assertEquals(2, homeSelected.size)
        assertEquals(2, searchSelected.size)
        assertNotEquals(homeSelected[0].key, searchSelected[0].key)
        assertNotEquals(homeSelected[1].key, searchSelected[1].key)
        assertEquals(
            listOf(primary, 0x00000000),
            homeSelected.map { node ->
                ((node.spec as SurfaceNodeProps).fill as Brush.SolidColor).color
            },
        )
        assertEquals(
            listOf(0x00000000, primary),
            searchSelected.map { node ->
                ((node.spec as SurfaceNodeProps).fill as Brush.SolidColor).color
            },
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

    @Test
    fun componentsConsumeOverriddenShapeSizingAndActivatedColorTokens() {
        val defaults = OneUi7ThemeDefaults.light()
        val activated = 0xFF22AA66.toInt()
        val tokens = defaults.copy(
            shapes = defaults.shapes.copy(medium = UiShape.rounded(7.dp)),
            controls = defaults.controls.copy(
                button = defaults.controls.button.copy(
                    mediumHeight = 60.dp,
                    mediumVisualHeight = 42.dp,
                    mediumHorizontalPadding = 31.dp,
                    mediumVerticalPadding = 9.dp,
                ),
                textField = defaults.controls.textField.copy(
                    mediumHeight = 62.dp,
                    mediumHorizontalPadding = 23.dp,
                    mediumVerticalPadding = 15.dp,
                ),
                navigationBar = defaults.controls.navigationBar.copy(height = 74.dp),
            ),
            stateColors = defaults.stateColors.copy(
                controlActivated = UiStateColor(
                    defaultColor = activated,
                    checkedColor = activated,
                ),
            ),
        )

        val button = buildVNodeTree {
            OneUi7Theme(tokens) {
                OneUi7Button(text = "Action", onClick = {})
            }
        }.single().spec as SurfaceNodeProps
        assertEquals(60f, button.minimumHeight.value)
        assertEquals(42f, button.visualHeight?.value)
        assertEquals(7f, button.shape.uniformAbsoluteSizeOrNull?.value)

        val textField = buildVNodeTree {
            OneUi7Theme(tokens) {
                OneUi7TextField(state = TextFieldState(), label = "")
            }
        }.flatten().single { it.type == NodeType.TextField }.spec as TextFieldNodeProps
        assertEquals(62f, textField.minHeight.value)
        assertEquals(23f, textField.paddingHorizontal.value)
        assertEquals(15f, textField.paddingVertical.value)
        assertEquals(7f, textField.shape.uniformAbsoluteSizeOrNull?.value)

        val navigation = buildVNodeTree {
            OneUi7Theme(tokens) {
                OneUi7NavigationBar(
                    items = listOf(
                        OneUi7NavigationItem("home", "Home"),
                        OneUi7NavigationItem("search", "Search"),
                    ),
                    selectedIndex = 0,
                    onItemSelected = {},
                )
            }
        }.single().spec as SurfaceNodeProps
        assertEquals(74f, navigation.minimumHeight.value)

        assertEquals(activated, OneUi7Recipes.from(tokens).activatedControlColor)
    }
}

private fun List<VNode>.flatten(): List<VNode> = buildList {
    fun visit(node: VNode) {
        add(node)
        node.children.forEach(::visit)
    }
    this@flatten.forEach(::visit)
}
