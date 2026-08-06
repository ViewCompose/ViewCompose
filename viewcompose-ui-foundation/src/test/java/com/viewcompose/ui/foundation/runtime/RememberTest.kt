package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core runtime 中的 Remember 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Remember behavior in widget-core runtime and guards DSL, state, or theme contracts against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RememberTest {
    @Test
    fun `remember reuses slot values across renders`() {
        val harness = ComposerRuntimeHarness()
        var first: Any? = null
        var second: Any? = null

        harness.render {
            first = remember { Any() }
        }
        harness.render {
            second = remember { Any() }
        }

        assertSame(first, second)
        harness.dispose()
    }

    @Test
    fun `remember keeps slot ordering stable`() {
        val harness = ComposerRuntimeHarness()
        val firstPass = mutableListOf<Any>()
        val secondPass = mutableListOf<Any>()

        harness.render {
            firstPass += remember { Any() }
            firstPass += remember { Any() }
        }
        harness.render {
            secondPass += remember { Any() }
            secondPass += remember { Any() }
        }

        assertEquals(2, secondPass.size)
        assertSame(firstPass[0], secondPass[0])
        assertSame(firstPass[1], secondPass[1])
        harness.dispose()
    }

    @Test
    fun `remember outside composition fails fast`() {
        val error = runCatching {
            remember { Any() }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("remember"))
        assertTrue(error?.message.orEmpty().contains("active ViewCompose composition"))
    }

    @Test
    fun `remember with same key reuses slot value`() {
        val harness = ComposerRuntimeHarness()
        var first: Any? = null
        var second: Any? = null

        harness.render {
            first = remember("stable-key") { Any() }
        }
        harness.render {
            second = remember("stable-key") { Any() }
        }

        assertSame(first, second)
        harness.dispose()
    }

    @Test
    fun `remember with changed key recreates slot value`() {
        val harness = ComposerRuntimeHarness()
        var first: Any? = null
        var second: Any? = null

        harness.render {
            first = remember("first-key") { Any() }
        }
        harness.render {
            second = remember("second-key") { Any() }
        }

        assertNotSame(first, second)
        harness.dispose()
    }

    @Test
    fun `key scope prevents conditional remember slot from leaking into sibling`() {
        val harness = ComposerRuntimeHarness()
        lateinit var alwaysWhenBranchShown: Any
        lateinit var alwaysWhenBranchHidden: Any

        harness.render {
            key("conditional-branch") {
                remember { Any() }
            }
            alwaysWhenBranchShown = remember { Any() }
        }
        harness.render {
            alwaysWhenBranchHidden = remember { Any() }
        }

        assertNotSame(alwaysWhenBranchShown, alwaysWhenBranchHidden)
        harness.dispose()
    }

    @Test
    fun `key outside composition fails fast`() {
        val error = runCatching {
            key("outside") { Unit }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("key"))
        assertTrue(error?.message.orEmpty().contains("active ViewCompose composition"))
    }
}
