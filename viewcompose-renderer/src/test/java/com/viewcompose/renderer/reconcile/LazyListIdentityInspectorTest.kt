package com.viewcompose.renderer.reconcile

/*
 * 测试职责：覆盖 renderer reconcile 中的 Lazy List Identity Inspector 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Lazy List Identity Inspector behavior in renderer reconcile and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.LazyListItemSessionFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyListIdentityInspectorTest {
    @Test
    fun `supports keyed diff when all keys are distinct`() {
        val analysis = LazyListIdentityInspector.analyze(
            listOf(item("A"), item("B"), item("C")),
        )

        assertTrue(analysis.supportsKeyedDiff)
        assertEquals(emptyList<Int>(), analysis.missingKeyIndexes)
        assertEquals(emptyList<Any>(), analysis.duplicateKeys)
        assertNull(analysis.warningMessage("items"))
    }

    @Test
    fun `reports missing key indexes`() {
        val analysis = LazyListIdentityInspector.analyze(
            listOf(item("A"), item(null), item("C"), item(null)),
        )

        assertFalse(analysis.supportsKeyedDiff)
        assertEquals(listOf(1, 3), analysis.missingKeyIndexes)
        assertEquals(
            "LazyColumn items cannot use keyed diff: missing keys at indexes [1, 3]",
            analysis.warningMessage("items"),
        )
    }

    @Test
    fun `reports duplicate keys`() {
        val analysis = LazyListIdentityInspector.analyze(
            listOf(item("A"), item("B"), item("A"), item("B")),
        )

        assertFalse(analysis.supportsKeyedDiff)
        assertEquals(listOf("A", "B"), analysis.duplicateKeys)
        assertEquals(
            "LazyColumn items cannot use keyed diff: duplicate keys [A, B]",
            analysis.warningMessage("items"),
        )
    }

    @Test
    fun `combines missing and duplicate key warnings`() {
        val analysis = LazyListIdentityInspector.analyze(
            listOf(item(null), item("A"), item("A")),
        )

        assertEquals(
            "LazyColumn next items cannot use keyed diff: missing keys at indexes [0], duplicate keys [A]",
            analysis.warningMessage("next items"),
        )
    }

    private fun item(key: String?): LazyListItem {
        return LazyListItem(
            key = key,
            contentToken = key,
            sessionFactory = LazyListItemSessionFactory {
                object : LazyListItemSession {
                    override fun render() = Unit

                    override fun dispose() = Unit
                }
            },
        )
    }
}
