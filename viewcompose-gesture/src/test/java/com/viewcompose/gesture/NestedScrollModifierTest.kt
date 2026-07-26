package com.viewcompose.gesture

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
