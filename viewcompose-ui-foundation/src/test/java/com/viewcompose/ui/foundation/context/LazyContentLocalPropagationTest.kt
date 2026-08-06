package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core context 中的 Lazy Content Local Propagation 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Lazy Content Local Propagation behavior in widget-core context and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.node.spec.HorizontalPagerNodeProps
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.TabRowNodeProps
import com.viewcompose.ui.node.spec.VerticalPagerNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LazyContentLocalPropagationTest {
    private val testLocal = uiLocalOf(debugName = "LazyContentTestLocal") { "default" }

    @Test
    fun `delayed content token changes when captured locals change`() {
        val first = delayedContentTokens(localValue = "first")
        val second = delayedContentTokens(localValue = "second")

        assertEquals(first.size, second.size)
        first.zip(second).forEach { (previous, next) ->
            assertNotEquals(previous, next)
        }
    }

    @Test
    fun `delayed content token stays stable when captured locals stay equal`() {
        val first = delayedContentTokens(localValue = "stable")
        val second = delayedContentTokens(localValue = "stable")

        assertEquals(first, second)
    }

    private fun delayedContentTokens(
        localValue: String,
    ): List<Any?> {
        val tree = buildVNodeTree {
            ProvideLocal(testLocal, localValue) {
                LazyColumn {
                    item(
                        key = "lazy-item",
                        contentToken = "stable-content",
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
                        contentToken = "stable-content",
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
                        contentToken = "stable-content",
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
            (tree[0].spec as LazyColumnNodeProps).items.single().contentToken,
            (tree[1].spec as HorizontalPagerNodeProps).pages.single().contentToken,
            (tree[2].spec as VerticalPagerNodeProps).pages.single().contentToken,
            (tree[3].spec as TabRowNodeProps).tabs.single().item.contentToken,
        )
    }
}
