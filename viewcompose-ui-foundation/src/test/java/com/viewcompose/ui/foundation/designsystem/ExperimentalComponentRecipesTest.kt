package com.viewcompose.ui.foundation

import com.viewcompose.text.TextFieldState
import com.viewcompose.text.TextFieldValue
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NavigationBarItem
/*
 * 测试职责：验证内部非 Material Recipe 可在相同主题下生成不同的中立 NodeSpec，且不影响现有组件路径。
 * Test responsibility: proves that internal non-Material recipes can emit different neutral
 * NodeSpecs over the same theme without affecting the existing component path.
 */

import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.node.spec.TextFieldNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.node.spec.NavigationBarNodeProps
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

    @Test
    fun `text field recipes share native editing core but own decoration structure`() {
        val rounded = textFieldTree(
            recipes = roundedRecipes(),
            isError = false,
        )
        val cut = textFieldTree(
            recipes = cutCornerRecipes(),
            isError = false,
        )
        val roundedField = rounded.flatten().single { node -> node.type == NodeType.TextField }
        val cutField = cut.flatten().single { node -> node.type == NodeType.TextField }
        val roundedSpec = roundedField.spec as TextFieldNodeProps
        val cutSpec = cutField.spec as TextFieldNodeProps

        assertEquals(NodeType.Column, rounded.type)
        assertEquals(NodeType.Column, cut.type)
        assertEquals(listOf(NodeType.Text, NodeType.TextField, NodeType.Text), rounded.children.map { it.type })
        assertEquals(listOf(NodeType.TextField, NodeType.Text), cut.children.map { it.type })
        assertTrue(rounded.flatten().filter { it.type == NodeType.Text }
            .map { (it.spec as TextNodeProps).text.toString() }
            .contains("Account"))
        assertFalse(cut.flatten().filter { it.type == NodeType.Text }
            .map { (it.spec as TextNodeProps).text.toString() }
            .contains("Account"))
        assertEquals("Name", roundedSpec.placeholder)
        assertEquals("Name", cutSpec.placeholder)
        assertEquals(TextFieldValue("Ada"), roundedSpec.value)
        assertEquals(TextFieldValue("Ada"), cutSpec.value)
        assertNotEquals(roundedSpec.shape, cutSpec.shape)
        assertNotEquals(roundedSpec.backgroundColor, cutSpec.backgroundColor)
        assertNotEquals(roundedSpec.paddingHorizontal, cutSpec.paddingHorizontal)
    }

    @Test
    fun `text field error recipe resolves decoration and semantic error without replacing state`() {
        val root = textFieldTree(
            recipes = cutCornerRecipes(),
            isError = true,
        )
        val field = root.flatten().single { node -> node.type == NodeType.TextField }
        val spec = field.spec as TextFieldNodeProps
        val semantics = field.modifier.elements
            .filterIsInstance<SemanticsModifierElement>()
            .single()
            .configuration
        val recipe = cutCornerRecipes().textField

        assertEquals(recipe.errorContainerColor, spec.backgroundColor)
        assertEquals(recipe.errorBorderColor, spec.borderColor)
        assertEquals(recipe.errorBorderWidth, spec.borderWidth)
        assertEquals("Required", semantics.error)
        assertEquals(TextFieldValue("Ada"), spec.value)
    }

    @Test
    fun `navigation recipe may retain shared node or own composite structure`() {
        val shared = navigationTree(roundedRecipes())
        val composed = navigationTree(cutCornerRecipes())

        assertEquals(NodeType.NavigationBar, shared.type)
        assertTrue(shared.spec is NavigationBarNodeProps)
        assertEquals(NodeType.Row, composed.type)
        assertFalse(composed.flatten().any { node -> node.type == NodeType.NavigationBar })
        assertEquals(3, composed.children.size)
        composed.children.forEachIndexed { index, destination ->
            assertEquals(NodeType.Box, destination.type)
            val semantics = destination.modifier.elements
                .filterIsInstance<SemanticsModifierElement>()
                .single()
                .configuration
            assertEquals(SemanticsRole.Tab, semantics.role)
            assertEquals(index == 1, semantics.selected)
        }
        val visibleLabels = composed.flatten()
            .filter { node -> node.type == NodeType.Text }
            .map { node -> (node.spec as TextNodeProps).text.toString() }
        assertEquals(listOf("Search"), visibleLabels)
    }

    @Test
    fun `fixed navigation transport cannot express structural recipe decisions`() {
        val fieldNames = NavigationBarNodeProps::class.java.declaredFields
            .map { field -> field.name }
            .toSet()

        assertFalse("NavigationBarNodeProps unexpectedly owns indicator shape", "indicatorShape" in fieldNames)
        assertFalse("NavigationBarNodeProps unexpectedly owns label policy", "labelPolicy" in fieldNames)
        assertFalse("NavigationBarNodeProps unexpectedly owns destination layout", "destinationLayout" in fieldNames)
    }

    @Test
    fun `five component contrast fixture emits no design-system identity`() {
        listOf(roundedRecipes(), cutCornerRecipes()).forEach { recipes ->
            val tree = fiveComponentTree(recipes)
            val flattened = tree.flatMap { root -> root.flatten() }

            assertEquals(5, tree.size)
            assertTrue(flattened.any { node -> node.type == NodeType.Button })
            assertTrue(flattened.any { node -> node.type == NodeType.Surface })
            assertTrue(flattened.any { node -> node.type == NodeType.TextField })
            flattened.forEach { node ->
                assertFalse(node.spec.toString().contains(recipes.identity.value))
            }
        }
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

    private fun textFieldTree(
        recipes: ExperimentalComponentRecipes,
        isError: Boolean,
    ) = buildVNodeTree {
        ProvideExperimentalComponentRecipes(recipes) {
            ExperimentalRecipeTextField(
                state = TextFieldState(TextFieldValue("Ada")),
                label = "Account",
                placeholder = "Name",
                supportingText = if (isError) "Required" else "Visible to teammates",
                isError = isError,
            )
        }
    }.single()

    private fun navigationTree(
        recipes: ExperimentalComponentRecipes,
    ) = buildVNodeTree {
        ProvideExperimentalComponentRecipes(recipes) {
            ExperimentalRecipeNavigationBar(
                items = navigationItems(),
                selectedIndex = 1,
                onItemSelected = {},
            )
        }
    }.single()

    private fun fiveComponentTree(
        recipes: ExperimentalComponentRecipes,
    ) = buildVNodeTree {
        ProvideExperimentalComponentRecipes(recipes) {
            ExperimentalRecipeAction(text = "Action")
            ExperimentalRecipeSurface(onClick = {}) { Text("Surface") }
            ExperimentalRecipeSwitch(
                text = "Sync",
                checked = true,
                onCheckedChange = {},
            )
            ExperimentalRecipeTextField(
                state = TextFieldState(TextFieldValue("Ada")),
                label = "Account",
                placeholder = "Name",
            )
            ExperimentalRecipeNavigationBar(
                items = navigationItems(),
                selectedIndex = 1,
                onItemSelected = {},
            )
        }
    }

    private fun navigationItems(): List<NavigationBarItem> {
        return listOf(
            NavigationBarItem(label = "Home", icon = ImageSource.Resource(1)),
            NavigationBarItem(label = "Search", icon = ImageSource.Resource(2)),
            NavigationBarItem(label = "Profile", icon = ImageSource.Resource(3)),
        )
    }

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
            textField = ExperimentalTextFieldRecipe(
                decoration = ExperimentalTextFieldDecoration.StackedLabel,
                enabledContainerColor = 0xFFF4FBF6.toInt(),
                disabledContainerColor = 0x66F4FBF6,
                errorContainerColor = 0xFFFFEDEA.toInt(),
                enabledTextColor = 0xFF142019.toInt(),
                disabledTextColor = 0x66142019,
                enabledHintColor = 0xFF53645A.toInt(),
                disabledHintColor = 0x6653645A,
                enabledLabelColor = 0xFF214E34.toInt(),
                disabledLabelColor = 0x66214E34,
                errorLabelColor = 0xFFBA1A1A.toInt(),
                supportingTextColor = 0xFF53645A.toInt(),
                errorSupportingTextColor = 0xFFBA1A1A.toInt(),
                enabledBorderColor = 0xFF819085.toInt(),
                disabledBorderColor = 0x66819085,
                errorBorderColor = 0xFFBA1A1A.toInt(),
                borderWidth = 1.dp,
                errorBorderWidth = 2.dp,
                shape = UiShape.rounded(12.dp),
                minimumHeight = 52.dp,
                horizontalPadding = 16.dp,
                verticalPadding = 12.dp,
                decorationSpacing = 6.dp,
                cursorColor = 0xFF214E34.toInt(),
                textStyle = UiTextStyle(fontSizeSp = 16.sp, fontWeight = 450),
                labelStyle = UiTextStyle(fontSizeSp = 13.sp, fontWeight = 600),
                supportingTextStyle = UiTextStyle(fontSizeSp = 12.sp, fontWeight = 400),
            ),
            navigation = ExperimentalNavigationRecipe(
                structure = ExperimentalNavigationStructure.SharedBarNode,
                labelPolicy = ExperimentalNavigationLabelPolicy.Always,
                containerColor = 0xFFF4FBF6.toInt(),
                containerShape = UiShape.rounded(0.dp),
                selectedIconColor = 0xFF214E34.toInt(),
                unselectedIconColor = 0xFF53645A.toInt(),
                selectedLabelColor = 0xFF214E34.toInt(),
                unselectedLabelColor = 0xFF53645A.toInt(),
                indicatorColor = 0xFFC8E8D0.toInt(),
                indicatorShape = UiShape.roundedRelative(0.5f),
                selectedStateLayerColors = UiStateLayerColors(
                    pressedColor = 0x1F214E34,
                    focusedColor = 0x29214E34,
                    hoveredColor = 0x14214E34,
                ),
                unselectedStateLayerColors = UiStateLayerColors(
                    pressedColor = 0x1F53645A,
                    focusedColor = 0x2953645A,
                    hoveredColor = 0x1453645A,
                ),
                height = 80.dp,
                iconSize = 24.dp,
                indicatorHorizontalPadding = 18.dp,
                indicatorVerticalPadding = 4.dp,
                itemSpacing = 4.dp,
                labelStyle = UiTextStyle(fontSizeSp = 12.sp, fontWeight = 600),
                badgeColor = 0xFFBA1A1A.toInt(),
                badgeTextColor = 0xFFFFFFFF.toInt(),
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
            textField = ExperimentalTextFieldRecipe(
                decoration = ExperimentalTextFieldDecoration.PlaceholderLabel,
                enabledContainerColor = 0xFFFFF0E8.toInt(),
                disabledContainerColor = 0x66FFF0E8,
                errorContainerColor = 0xFFFFDAD6.toInt(),
                enabledTextColor = 0xFF32150C.toInt(),
                disabledTextColor = 0x6632150C,
                enabledHintColor = 0xFF77574B.toInt(),
                disabledHintColor = 0x6677574B,
                enabledLabelColor = 0xFF6A2B18.toInt(),
                disabledLabelColor = 0x666A2B18,
                errorLabelColor = 0xFFBA1A1A.toInt(),
                supportingTextColor = 0xFF77574B.toInt(),
                errorSupportingTextColor = 0xFFBA1A1A.toInt(),
                enabledBorderColor = 0xFF6A2B18.toInt(),
                disabledBorderColor = 0x666A2B18,
                errorBorderColor = 0xFFBA1A1A.toInt(),
                borderWidth = 2.dp,
                errorBorderWidth = 3.dp,
                shape = UiShape.cut(10.dp),
                minimumHeight = 56.dp,
                horizontalPadding = 20.dp,
                verticalPadding = 14.dp,
                decorationSpacing = 8.dp,
                cursorColor = 0xFF6A2B18.toInt(),
                textStyle = UiTextStyle(fontSizeSp = 17.sp, fontWeight = 500),
                labelStyle = UiTextStyle(fontSizeSp = 14.sp, fontWeight = 650),
                supportingTextStyle = UiTextStyle(fontSizeSp = 12.sp, fontWeight = 500),
            ),
            navigation = ExperimentalNavigationRecipe(
                structure = ExperimentalNavigationStructure.ComposedDestinations,
                labelPolicy = ExperimentalNavigationLabelPolicy.SelectedOnly,
                containerColor = 0xFFFFF0E8.toInt(),
                containerShape = UiShape.cut(12.dp),
                selectedIconColor = 0xFFFFF4EF.toInt(),
                unselectedIconColor = 0xFF77574B.toInt(),
                selectedLabelColor = 0xFF6A2B18.toInt(),
                unselectedLabelColor = 0xFF77574B.toInt(),
                indicatorColor = 0xFF6A2B18.toInt(),
                indicatorShape = UiShape.cut(5.dp),
                selectedStateLayerColors = UiStateLayerColors(
                    pressedColor = 0x1FFFF4EF,
                    focusedColor = 0x29FFF4EF,
                    hoveredColor = 0x14FFF4EF,
                ),
                unselectedStateLayerColors = UiStateLayerColors(
                    pressedColor = 0x1F77574B,
                    focusedColor = 0x2977574B,
                    hoveredColor = 0x1477574B,
                ),
                height = 72.dp,
                iconSize = 22.dp,
                indicatorHorizontalPadding = 16.dp,
                indicatorVerticalPadding = 6.dp,
                itemSpacing = 6.dp,
                labelStyle = UiTextStyle(fontSizeSp = 13.sp, fontWeight = 650),
                badgeColor = 0xFFBA1A1A.toInt(),
                badgeTextColor = 0xFFFFFFFF.toInt(),
            ),
        )
    }
}
