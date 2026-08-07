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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
        )
    }
}
