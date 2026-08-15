package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core context 中的 Lazy Content Local Propagation 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Lazy Content Local Propagation behavior in widget-core context and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.node.spec.HorizontalPagerNodeProps
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.node.spec.VerticalPagerNodeProps
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.runtime.composition.ComposerLite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LazyContentLocalPropagationTest {
    private val testLocal = uiLocalOf(debugName = "LazyContentTestLocal") { "default" }

    @Test
    fun `delayed environment revision and eager tab content change with captured locals`() {
        val first = delayedContentRevisions(localValue = "first")
        val second = delayedContentRevisions(localValue = "second")

        assertEquals(first.size, second.size)
        first.zip(second).forEach { (previous, next) ->
            assertNotEquals(previous, next)
        }
    }

    @Test
    fun `delayed environment revision and eager tab content stay stable when locals stay equal`() {
        val first = delayedContentRevisions(localValue = "stable")
        val second = delayedContentRevisions(localValue = "stable")

        assertEquals(first, second)
    }

    @Test
    fun `delayed environment revision and eager tab environment change with resources`() {
        val first = delayedResourceTokens(resourceRevision = 1L)
        val second = delayedResourceTokens(resourceRevision = 2L)

        assertEquals(first.size, second.size)
        first.zip(second).forEach { (previous, next) ->
            assertNotEquals(previous, next)
        }
    }

    @Test
    fun `delayed sessions receive a new environment revision after direction changes`() {
        val ltr = delayedDirectionTokens(UiLayoutDirection.Ltr)
        val rtl = delayedDirectionTokens(UiLayoutDirection.Rtl)

        assertEquals(ltr.size, rtl.size)
        ltr.zip(rtl).forEach { (previous, next) ->
            assertNotEquals(previous, next)
        }
    }

    @Test
    fun `environment change recomposes unselected eager tab content`() {
        val composer = ComposerLite()
        var localValue = "first"
        var tabRuns = 0

        fun compose(): String = ComposerContext.withComposer(composer) {
            composer.requestRootRecompose()
            val root = composer.composeRoot {
                buildVNodeTree {
                    ProvideLocal(testLocal, localValue) {
                        TabRow(selectedIndex = 0, onTabSelected = {}) {
                            Tab(key = "selected") { Text("selected") }
                            Tab(key = "unselected", contentRevision = "stable") {
                                tabRuns += 1
                                Text(UiLocals.current(testLocal))
                            }
                        }
                    }
                }.single()
            }
            (root.children[1].children.single().spec as TextNodeProps).document.text
        }

        assertEquals("first", compose())
        localValue = "second"
        assertEquals("second", compose())
        assertEquals(2, tabRuns)
    }

    private fun delayedContentRevisions(
        localValue: String,
    ): List<Any?> {
        val tree = buildVNodeTree {
            ProvideLocal(testLocal, localValue) {
                LazyColumn {
                    item(
                        key = "lazy-item",
                        contentRevision = "stable-content",
                    ) {
                        Text(UiLocals.current(testLocal))
                    }
                }
                HorizontalPager(
                    currentPage = 0,
                    onPageChanged = {},
                ) {
                    Page(
                        key = "horizontal-page",
                        contentRevision = "stable-content",
                    ) {
                        Text(UiLocals.current(testLocal))
                    }
                }
                VerticalPager(
                    currentPage = 0,
                    onPageChanged = {},
                ) {
                    Page(
                        key = "vertical-page",
                        contentRevision = "stable-content",
                    ) {
                        Text(UiLocals.current(testLocal))
                    }
                }
                TabRow(
                    selectedIndex = 0,
                    onTabSelected = {},
                ) {
                    Tab(key = "tab") {
                        Text(UiLocals.current(testLocal))
                    }
                }
            }
        }

        return listOf(
            (tree[0].spec as LazyColumnNodeProps).items.single().environmentRevision,
            (tree[1].spec as HorizontalPagerNodeProps).pages.single().environmentRevision,
            (tree[2].spec as VerticalPagerNodeProps).pages.single().environmentRevision,
            ((tree[3].children.single().children.single().spec as TextNodeProps).document.text),
        )
    }

    private fun delayedResourceTokens(resourceRevision: Long): List<Any?> {
        val tree = buildVNodeTree {
            UiEnvironment(
                values = UiEnvironmentValues(resourceRevision = resourceRevision),
            ) {
                LazyColumn {
                    item(key = "lazy-item", contentRevision = "stable-content") {
                        Text("lazy")
                    }
                }
                HorizontalPager(currentPage = 0, onPageChanged = {}) {
                    Page(key = "horizontal-page", contentRevision = "stable-content") {
                        Text("pager")
                    }
                }
                VerticalPager(currentPage = 0, onPageChanged = {}) {
                    Page(key = "vertical-page", contentRevision = "stable-content") {
                        Text("pager")
                    }
                }
                TabRow(selectedIndex = 0, onTabSelected = {}) {
                    Tab(key = "tab") {
                        Text("tab")
                    }
                }
            }
        }

        return listOf(
            (tree[0].spec as LazyColumnNodeProps).items.single().environmentRevision,
            (tree[1].spec as HorizontalPagerNodeProps).pages.single().environmentRevision,
            (tree[2].spec as VerticalPagerNodeProps).pages.single().environmentRevision,
            tree[3].children.single().children.single().environment.resourceRevision,
        )
    }

    private fun delayedDirectionTokens(layoutDirection: UiLayoutDirection): List<Any?> {
        val tree = buildVNodeTree {
            UiEnvironment(
                values = UiEnvironmentValues(layoutDirection = layoutDirection),
            ) {
                LazyColumn {
                    item(key = "lazy-item", contentRevision = "stable-content") {
                        Text("lazy")
                    }
                }
                HorizontalPager(currentPage = 0, onPageChanged = {}) {
                    Page(key = "horizontal-page", contentRevision = "stable-content") {
                        Text("pager")
                    }
                }
                VerticalPager(currentPage = 0, onPageChanged = {}) {
                    Page(key = "vertical-page", contentRevision = "stable-content") {
                        Text("pager")
                    }
                }
            }
        }

        return listOf(
            (tree[0].spec as LazyColumnNodeProps).items.single().environmentRevision,
            (tree[1].spec as HorizontalPagerNodeProps).pages.single().environmentRevision,
            (tree[2].spec as VerticalPagerNodeProps).pages.single().environmentRevision,
        )
    }
}
