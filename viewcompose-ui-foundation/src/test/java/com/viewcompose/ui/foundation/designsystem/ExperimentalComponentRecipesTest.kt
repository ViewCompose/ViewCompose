package com.viewcompose.ui.foundation

/*
 * 测试职责：验证内部非 Material Recipe 可在相同主题下生成不同的中立 NodeSpec，且不影响现有组件路径。
 * Test responsibility: proves that internal non-Material recipes can emit different neutral
 * NodeSpecs over the same theme without affecting the existing component path.
 */

import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.modifier.SemanticsModifierElement
import com.viewcompose.ui.modifier.SemanticsRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalComponentRecipesTest {
    @Test
    fun `same foundation tokens and different recipes emit different neutral specs`() {
        val tokens = UiThemeDefaults.light()
        val rounded = roundedRecipes()
        val cut = cutCornerRecipes()

        val roundedTree = recipeTree(tokens, rounded)
        val cutTree = recipeTree(tokens, cut)
        val roundedAction = roundedTree[0].spec as ButtonNodeProps
        val cutAction = cutTree[0].spec as ButtonNodeProps
        val roundedSurface = roundedTree[1].spec as BoxNodeProps
        val cutSurface = cutTree[1].spec as BoxNodeProps

        assertEquals(NodeType.Button, roundedTree[0].type)
        assertEquals(NodeType.Surface, roundedTree[1].type)
        assertNotEquals(roundedAction.backgroundColor, cutAction.backgroundColor)
        assertNotEquals(roundedAction.shape, cutAction.shape)
        assertNotEquals(roundedAction.paddingHorizontal, cutAction.paddingHorizontal)
        assertNotEquals(roundedTree[1].modifier, cutTree[1].modifier)
        assertEquals(rounded.action.stateLayerColors, roundedAction.stateLayerColors)
        assertEquals(cut.action.stateLayerColors, cutAction.stateLayerColors)
        assertEquals(rounded.surface.stateLayerColors, roundedSurface.stateLayerColors)
        assertEquals(cut.surface.stateLayerColors, cutSurface.stateLayerColors)

        val renderedContracts = roundedTree.map { node -> node.spec.toString() } +
            cutTree.map { node -> node.spec.toString() }
        renderedContracts.forEach { contract ->
            assertEquals(false, contract.contains(rounded.identity.value))
            assertEquals(false, contract.contains(cut.identity.value))
        }
    }

    @Test
    fun `disabled action resolves disabled values and removes interaction state layers`() {
        val recipes = cutCornerRecipes()
        val tree = buildVNodeTree {
            ProvideExperimentalComponentRecipes(recipes) {
                ExperimentalRecipeAction(text = "Disabled", enabled = false)
            }
        }
        val spec = tree.single().spec as ButtonNodeProps

        assertEquals(recipes.action.disabledContainerColor, spec.backgroundColor)
        assertEquals(recipes.action.disabledContentColor, spec.textColor)
        assertEquals(recipes.action.disabledBorderColor, spec.borderColor)
        assertEquals(0x00000000, spec.rippleColor)
        assertNull(spec.stateLayerColors)
    }

    @Test
    fun `experimental provider does not alter existing Button or Surface defaults`() {
        val baseline = existingComponentTree()
        val provided = buildVNodeTree {
            ProvideExperimentalComponentRecipes(cutCornerRecipes()) {
                Button(text = "Existing")
                Surface { Text("Content") }
            }
        }

        assertEquals(baseline[0].spec, provided[0].spec)
        assertEquals(baseline[0].modifier, provided[0].modifier)
        assertEquals(baseline[1].spec, provided[1].spec)
        assertEquals(baseline[1].modifier, provided[1].modifier)
    }

    @Test
    fun `switch recipe owns generic geometry order and checked semantics`() {
        val rounded = switchTree(recipes = roundedRecipes(), checked = true)
        val cut = switchTree(recipes = cutCornerRecipes(), checked = false)

        assertEquals(NodeType.Row, rounded.type)
        assertEquals(NodeType.Row, cut.type)
        assertFalse(rounded.flatten().any { node -> node.type == NodeType.Switch })
        assertFalse(cut.flatten().any { node -> node.type == NodeType.Switch })
        assertEquals(NodeType.Box, rounded.children.first().type)
        assertEquals(NodeType.Text, rounded.children.last().type)
        assertEquals(NodeType.Text, cut.children.first().type)
        assertEquals(NodeType.Box, cut.children.last().type)

        val roundedSemantics = rounded.modifier.elements
            .filterIsInstance<SemanticsModifierElement>()
            .single()
            .configuration
        val cutSemantics = cut.modifier.elements
            .filterIsInstance<SemanticsModifierElement>()
            .single()
            .configuration
        assertEquals(SemanticsRole.Switch, roundedSemantics.role)
        assertEquals(true, roundedSemantics.checked)
        assertEquals(SemanticsRole.Switch, cutSemantics.role)
        assertEquals(false, cutSemantics.checked)
        assertTrue(rounded.modifier.toString().contains("ClickableModifierElement"))
    }

    @Test
    fun `disabled recipe switch removes click handling and retains disabled semantics`() {
        val node = switchTree(
            recipes = cutCornerRecipes(),
            checked = true,
            enabled = false,
        )
        val semantics = node.modifier.elements
            .filterIsInstance<SemanticsModifierElement>()
            .single()
            .configuration

        assertEquals(false, semantics.enabled)
        assertEquals(true, semantics.checked)
        assertFalse(node.modifier.toString().contains("ClickableModifierElement"))
        assertNull((node.spec as com.viewcompose.ui.node.spec.RowNodeProps).stateLayerColors)
    }

    @Test
    fun `native switch transport cannot express high fidelity geometry or motion`() {
        val fieldNames = com.viewcompose.ui.node.spec.ToggleNodeProps::class.java.declaredFields
            .map { field -> field.name }
            .toSet()

        assertFalse("ToggleNodeProps unexpectedly owns shape", "shape" in fieldNames)
        assertFalse("ToggleNodeProps unexpectedly owns track geometry", "trackWidth" in fieldNames)
        assertFalse("ToggleNodeProps unexpectedly owns thumb geometry", "thumbSize" in fieldNames)
        assertFalse("ToggleNodeProps unexpectedly owns motion", "motion" in fieldNames)
    }

    private fun recipeTree(
        tokens: UiThemeTokens,
        recipes: ExperimentalComponentRecipes,
    ) = buildVNodeTree {
        UiTheme(tokens) {
            ProvideExperimentalComponentRecipes(recipes) {
                ExperimentalRecipeAction(text = "Action")
                ExperimentalRecipeSurface(onClick = {}) {
                    Text("Content")
                }
            }
        }
    }

    private fun existingComponentTree() = buildVNodeTree {
        Button(text = "Existing")
        Surface { Text("Content") }
    }

    private fun switchTree(
        recipes: ExperimentalComponentRecipes,
        checked: Boolean,
        enabled: Boolean = true,
    ) = buildVNodeTree {
        ProvideExperimentalComponentRecipes(recipes) {
            ExperimentalRecipeSwitch(
                text = "Sync",
                checked = checked,
                enabled = enabled,
                onCheckedChange = {},
            )
        }
    }.single()

    private fun com.viewcompose.ui.node.VNode.flatten(): List<com.viewcompose.ui.node.VNode> {
        return listOf(this) + children.flatMap { child -> child.flatten() }
    }

    private fun roundedRecipes(): ExperimentalComponentRecipes {
        return ExperimentalComponentRecipes(
            identity = ExperimentalComponentRecipeIdentity("rounded-reference"),
            action = ExperimentalActionRecipe(
                enabledContainerColor = 0xFF214E34.toInt(),
                disabledContainerColor = 0x33214E34,
                enabledContentColor = 0xFFFFFFFF.toInt(),
                disabledContentColor = 0x66FFFFFF,
                enabledBorderColor = 0x00000000,
                disabledBorderColor = 0x00000000,
                borderWidth = 0.dp,
                shape = UiShape.roundedRelative(0.5f),
                minHeight = 48.dp,
                visualHeight = 40.dp,
                horizontalPadding = 24.dp,
                verticalPadding = 8.dp,
                iconSize = 18.dp,
                iconSpacing = 8.dp,
                textStyle = UiTextStyle(fontSizeSp = 14.sp, fontWeight = 600),
                stateLayerColors = UiStateLayerColors(
                    pressedColor = 0x33FFFFFF,
                    focusedColor = 0x29FFFFFF,
                    hoveredColor = 0x1FFFFFFF,
                ),
            ),
            surface = ExperimentalSurfaceRecipe(
                containerColor = 0xFFF4FBF6.toInt(),
                contentColor = 0xFF142019.toInt(),
                shape = UiShape.rounded(20.dp),
                elevation = 2.dp,
                stateLayerColors = UiStateLayerColors(
                    pressedColor = 0x1F142019,
                    focusedColor = 0x29142019,
                    hoveredColor = 0x14142019,
                ),
            ),
            switch = ExperimentalSwitchRecipe(
                controlPlacement = ExperimentalControlPlacement.Leading,
                checkedTrackColor = 0xFF214E34.toInt(),
                uncheckedTrackColor = 0xFFCAD8CE.toInt(),
                disabledTrackColor = 0x6677867B,
                checkedThumbColor = 0xFFFFFFFF.toInt(),
                uncheckedThumbColor = 0xFFF5FBF6.toInt(),
                disabledThumbColor = 0x99FFFFFF.toInt(),
                enabledLabelColor = 0xFF142019.toInt(),
                disabledLabelColor = 0x66142019,
                trackShape = UiShape.roundedRelative(0.5f),
                thumbShape = UiShape.roundedRelative(0.5f),
                trackWidth = 44.dp,
                trackHeight = 26.dp,
                trackPadding = 3.dp,
                thumbSize = 20.dp,
                minimumInteractiveHeight = 48.dp,
                labelSpacing = 12.dp,
                labelStyle = UiTextStyle(fontSizeSp = 14.sp, fontWeight = 500),
                stateLayerColors = UiStateLayerColors(
                    pressedColor = 0x1F142019,
                    focusedColor = 0x29142019,
                    hoveredColor = 0x14142019,
                ),
            ),
        )
    }

    private fun cutCornerRecipes(): ExperimentalComponentRecipes {
        return ExperimentalComponentRecipes(
            identity = ExperimentalComponentRecipeIdentity("cut-contrast"),
            action = ExperimentalActionRecipe(
                enabledContainerColor = 0xFF6A2B18.toInt(),
                disabledContainerColor = 0x336A2B18,
                enabledContentColor = 0xFFFFF4EF.toInt(),
                disabledContentColor = 0x66FFF4EF,
                enabledBorderColor = 0xFFFFB59D.toInt(),
                disabledBorderColor = 0x66FFB59D,
                borderWidth = 2.dp,
                shape = UiShape.cut(10.dp),
                minHeight = 52.dp,
                visualHeight = 52.dp,
                horizontalPadding = 30.dp,
                verticalPadding = 10.dp,
                iconSize = 20.dp,
                iconSpacing = 10.dp,
                textStyle = UiTextStyle(fontSizeSp = 16.sp, fontWeight = 700),
                stateLayerColors = UiStateLayerColors(
                    pressedColor = 0x33FFF4EF,
                    focusedColor = 0x29FFF4EF,
                    hoveredColor = 0x1FFFF4EF,
                ),
            ),
            surface = ExperimentalSurfaceRecipe(
                containerColor = 0xFFFFF0E8.toInt(),
                contentColor = 0xFF32150C.toInt(),
                shape = UiShape.cut(18.dp),
                borderWidth = 2.dp,
                borderColor = 0xFF6A2B18.toInt(),
                disabledAlpha = 0.48f,
                stateLayerColors = UiStateLayerColors(
                    pressedColor = 0x1F32150C,
                    focusedColor = 0x2932150C,
                    hoveredColor = 0x1432150C,
                ),
            ),
            switch = ExperimentalSwitchRecipe(
                controlPlacement = ExperimentalControlPlacement.Trailing,
                checkedTrackColor = 0xFF6A2B18.toInt(),
                uncheckedTrackColor = 0xFFFFD8C9.toInt(),
                disabledTrackColor = 0x66B89A8E,
                checkedThumbColor = 0xFFFFF4EF.toInt(),
                uncheckedThumbColor = 0xFF6A2B18.toInt(),
                disabledThumbColor = 0x99FFF4EF.toInt(),
                enabledLabelColor = 0xFF32150C.toInt(),
                disabledLabelColor = 0x6632150C,
                trackShape = UiShape.cut(6.dp),
                thumbShape = UiShape.cut(3.dp),
                trackWidth = 52.dp,
                trackHeight = 28.dp,
                trackPadding = 4.dp,
                thumbSize = 20.dp,
                minimumInteractiveHeight = 52.dp,
                labelSpacing = 16.dp,
                labelStyle = UiTextStyle(fontSizeSp = 16.sp, fontWeight = 650),
                stateLayerColors = UiStateLayerColors(
                    pressedColor = 0x1F32150C,
                    focusedColor = 0x2932150C,
                    hoveredColor = 0x1432150C,
                ),
            ),
        )
    }
}
