package com.viewcompose.ui.foundation

/*
 * 测试职责：验证设计系统组件 Recipe 可独立于主题 Token 传播，并锁定嵌套、快照与延迟内容的值语义。
 * Test responsibility: proves that design-system component recipes can propagate independently
 * from theme tokens and locks down nesting, snapshot, and delayed-content value semantics.
 */

import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DesignSystemRecipeContextCharacterizationTest {
    private enum class TestActionShape {
        Capsule,
        CutCorner,
    }

    private data class TestComponentRecipes(
        val identity: String,
        val actionShape: TestActionShape,
        val actionHorizontalPadding: Int,
    )

    private val neutralRecipes = TestComponentRecipes(
        identity = "neutral",
        actionShape = TestActionShape.Capsule,
        actionHorizontalPadding = 16,
    )

    private val localComponentRecipes = uiLocalOf(
        debugName = "TestComponentRecipes",
        debugValueFormatter = TestComponentRecipes::identity,
    ) { neutralRecipes }

    @Test
    fun `recipe providers nest and restore without replacing foundation tokens`() {
        val tokens = UiThemeDefaults.light()
        val outerRecipes = TestComponentRecipes(
            identity = "outer-cut",
            actionShape = TestActionShape.CutCorner,
            actionHorizontalPadding = 20,
        )
        val innerRecipes = TestComponentRecipes(
            identity = "inner-capsule",
            actionShape = TestActionShape.Capsule,
            actionHorizontalPadding = 28,
        )
        val observedRecipes = mutableListOf<TestComponentRecipes>()
        val observedThemes = mutableListOf<UiThemeTokens>()

        buildVNodeTree {
            UiTheme(tokens) {
                ProvideLocal(localComponentRecipes, outerRecipes) {
                    observedRecipes += UiLocals.current(localComponentRecipes)
                    observedThemes += Theme.current
                    ProvideLocal(localComponentRecipes, innerRecipes) {
                        observedRecipes += UiLocals.current(localComponentRecipes)
                        observedThemes += Theme.current
                    }
                    observedRecipes += UiLocals.current(localComponentRecipes)
                    observedThemes += Theme.current
                }
            }
        }

        assertEquals(listOf(outerRecipes, innerRecipes, outerRecipes), observedRecipes)
        observedThemes.forEach { observed -> assertSame(tokens, observed) }
        assertEquals(neutralRecipes, UiLocals.current(localComponentRecipes))
    }

    @Test
    fun `captured recipe and theme snapshots restore as one coherent context`() {
        val tokens = UiThemeDefaults.dark()
        val recipes = TestComponentRecipes(
            identity = "captured-cut",
            actionShape = TestActionShape.CutCorner,
            actionHorizontalPadding = 24,
        )
        lateinit var snapshot: UiLocalSnapshot

        buildVNodeTree {
            UiTheme(tokens) {
                ProvideLocal(localComponentRecipes, recipes) {
                    snapshot = captureUiLocalSnapshot()
                }
            }
        }

        withUiLocalSnapshot(snapshot) {
            assertSame(tokens, Theme.current)
            assertEquals(recipes, UiLocals.current(localComponentRecipes))
        }
        assertEquals(neutralRecipes, UiLocals.current(localComponentRecipes))
    }

    @Test
    fun `delayed content identity follows immutable recipe values`() {
        val first = TestComponentRecipes(
            identity = "stable",
            actionShape = TestActionShape.Capsule,
            actionHorizontalPadding = 18,
        )
        val equalValue = first.copy()
        val changed = first.copy(actionHorizontalPadding = 30)

        val firstToken = delayedContentRevision(first)
        val equalToken = delayedContentRevision(equalValue)
        val changedToken = delayedContentRevision(changed)

        assertEquals(firstToken, equalToken)
        assertNotEquals(firstToken, changedToken)
    }

    private fun delayedContentRevision(recipes: TestComponentRecipes): Any? {
        val tree = buildVNodeTree {
            ProvideLocal(localComponentRecipes, recipes) {
                LazyColumn {
                    item(
                        key = "recipe-item",
                        contentRevision = "stable-content",
                    ) {
                        Text(UiLocals.current(localComponentRecipes).identity)
                    }
                }
            }
        }

        return (tree.single().spec as LazyColumnNodeProps).items.single().environmentRevision
    }
}
