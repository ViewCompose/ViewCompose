package com.viewcompose.ui.node

import com.viewcompose.text.TextDocument
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.node.spec.ToggleNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NodeSpecTextContractTest {
    @Test
    fun `text node retains one canonical document payload`() {
        assertEquals(
            TextDocument::class.java,
            TextNodeProps::class.java.getMethod("getDocument").returnType,
        )
        assertFalse(
            TextNodeProps::class.java.methods.any { method ->
                method.name == "getText" && method.parameterCount == 0
            },
        )
    }

    @Test
    fun `button and toggle labels are immutable strings`() {
        assertEquals(
            String::class.java,
            ButtonNodeProps::class.java.getMethod("getText").returnType,
        )
        assertEquals(
            String::class.java,
            ToggleNodeProps::class.java.getMethod("getText").returnType,
        )
    }
}
