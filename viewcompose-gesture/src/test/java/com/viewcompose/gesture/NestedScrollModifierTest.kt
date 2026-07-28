package com.viewcompose.gesture

/*
 * 测试职责：覆盖 gesture DSL 中的 Nested Scroll Modifier 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Nested Scroll Modifier behavior in gesture DSL and guards the contract against regressions.
 */

import com.viewcompose.ui.gesture.NestedScrollConnection
import com.viewcompose.ui.gesture.NestedScrollDispatcher
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.NestedScrollModifierElement
import org.junit.Assert.assertSame
import org.junit.Test

class NestedScrollModifierTest {
    @Test
    fun `nestedScroll appends connection and dispatcher`() {
        val connection = object : NestedScrollConnection {}
        val dispatcher = NestedScrollDispatcher()

        val element = Modifier
            .nestedScroll(
                connection = connection,
                dispatcher = dispatcher,
            ).elements
            .single() as NestedScrollModifierElement

        assertSame(connection, element.connection)
        assertSame(dispatcher, element.dispatcher)
    }
}
