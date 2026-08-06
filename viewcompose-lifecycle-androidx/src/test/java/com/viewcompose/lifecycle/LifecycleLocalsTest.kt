package com.viewcompose.lifecycle

/*
 * 测试职责：覆盖 lifecycle integration 中的 Lifecycle Locals 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Lifecycle Locals behavior in lifecycle integration and guards the contract against regressions.
 */

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.viewcompose.ui.foundation.buildVNodeTree
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LifecycleLocalsTest {
    @Test
    fun `local lifecycle owner defaults to null`() {
        assertNull(LocalLifecycleOwner.current)
    }

    @Test
    fun `provide lifecycle owner publishes value and restores after scope`() {
        val owner = TestLifecycleOwner()
        var inside: LifecycleOwner? = null

        buildVNodeTree {
            ProvideLifecycleOwner(owner) {
                inside = LocalLifecycleOwner.current
            }
        }

        assertSame(owner, inside)
        assertNull(LocalLifecycleOwner.current)
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)

        override val lifecycle: Lifecycle
            get() = registry
    }
}
