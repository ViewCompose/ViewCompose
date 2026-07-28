package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core context 中的 Business Local Api 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Business Local Api behavior in widget-core context and guards DSL, state, or theme contracts against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessLocalApiTest {
    data class BizTokens(
        val accentRadius: Int,
        val warningLabel: String,
    )

    @Test
    fun `business local supports default nested and restore semantics`() {
        val localBizTokens = uiLocalOf {
            BizTokens(
                accentRadius = 8,
                warningLabel = "default",
            )
        }
        var outside: BizTokens? = null
        var outer: BizTokens? = null
        var inner: BizTokens? = null
        var restored: BizTokens? = null
        var after: BizTokens? = null

        buildVNodeTree {
            outside = UiLocals.current(localBizTokens)
            ProvideLocal(
                local = localBizTokens,
                value = BizTokens(accentRadius = 16, warningLabel = "outer"),
            ) {
                outer = UiLocals.current(localBizTokens)
                ProvideLocal(
                    local = localBizTokens,
                    value = BizTokens(accentRadius = 24, warningLabel = "inner"),
                ) {
                    inner = UiLocals.current(localBizTokens)
                }
                restored = UiLocals.current(localBizTokens)
            }
            after = UiLocals.current(localBizTokens)
        }

        assertEquals(BizTokens(8, "default"), outside)
        assertEquals(BizTokens(16, "outer"), outer)
        assertEquals(BizTokens(24, "inner"), inner)
        assertEquals(BizTokens(16, "outer"), restored)
        assertEquals(BizTokens(8, "default"), after)
    }

    @Test
    fun `provideLocals can apply multiple business locals together`() {
        val localBizTokens = uiLocalOf { BizTokens(accentRadius = 8, warningLabel = "default") }
        val localFeatureFlag = uiLocalOf { false }
        var resolvedTokens: BizTokens? = null
        var resolvedFlag = false

        buildVNodeTree {
            ProvideLocals(
                localBizTokens provides BizTokens(accentRadius = 18, warningLabel = "biz"),
                localFeatureFlag provides true,
            ) {
                resolvedTokens = UiLocals.current(localBizTokens)
                resolvedFlag = UiLocals.current(localFeatureFlag)
            }
        }

        assertEquals(BizTokens(18, "biz"), resolvedTokens)
        assertTrue(resolvedFlag)
        assertFalse(UiLocals.current(localFeatureFlag))
    }

    @Test
    fun `business local values are preserved in snapshot restore path`() {
        val localBizTokens = uiLocalOf { BizTokens(accentRadius = 8, warningLabel = "default") }
        var snapshot: LocalSnapshot? = null
        var restored: BizTokens? = null
        var outside: BizTokens? = null

        buildVNodeTree {
            ProvideLocal(
                local = localBizTokens,
                value = BizTokens(accentRadius = 32, warningLabel = "deferred"),
            ) {
                snapshot = LocalContext.snapshot()
            }
            outside = UiLocals.current(localBizTokens)
        }

        LocalContext.withSnapshot(snapshot!!) {
            restored = UiLocals.current(localBizTokens)
        }

        assertEquals(BizTokens(32, "deferred"), restored)
        assertEquals(BizTokens(8, "default"), outside)
    }
}
