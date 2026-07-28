package com.viewcompose.runtime

/*
 * 测试职责：覆盖 runtime 中的 Snapshot Mutation Policy 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Snapshot Mutation Policy behavior in runtime and guards the contract against regressions.
 */

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotMutationPolicyTest {
    @Test
    fun `structural policy uses equals and does not merge`() {
        val policy = structuralEqualityPolicy<String>()

        assertTrue(policy.equivalent("a", "a"))
        assertFalse(policy.equivalent("a", "b"))
        assertNull(policy.merge(previous = "a", current = "b", applied = "c"))
    }

    @Test
    fun `referential policy uses identity and does not merge`() {
        val policy = referentialEqualityPolicy<String>()
        val first = String(charArrayOf('x'))
        val second = String(charArrayOf('x'))

        assertTrue(policy.equivalent(first, first))
        assertFalse(policy.equivalent(first, second))
        assertNull(policy.merge(previous = first, current = second, applied = first))
    }

    @Test
    fun `never equal policy always invalidates and does not merge`() {
        val policy = neverEqualPolicy<Int>()

        assertFalse(policy.equivalent(1, 1))
        assertFalse(policy.equivalent(1, 2))
        assertNull(policy.merge(previous = 1, current = 2, applied = 3))
    }
}
