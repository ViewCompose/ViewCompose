package com.viewcompose.ui.node

import com.viewcompose.text.TextDocument
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.node.spec.GenericUiFontFamily
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.node.spec.ToggleNodeProps
import com.viewcompose.ui.node.spec.uiFontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `font wrappers preserve identity equality across declarative snapshots`() {
        val font = Any()

        assertEquals(uiFontFamily(font), uiFontFamily(font))
        assertEquals(
            GenericUiFontFamily(font).hashCode(),
            GenericUiFontFamily(font).hashCode(),
        )
        assertNull(uiFontFamily(null))
    }

    @Test
    fun `font wrappers do not infer value equality for opaque platform fonts`() {
        class ValueEqualFont(private val value: String) {
            override fun equals(other: Any?): Boolean =
                other is ValueEqualFont && value == other.value

            override fun hashCode(): Int = value.hashCode()
        }

        assertNotEquals(
            uiFontFamily(ValueEqualFont("body")),
            uiFontFamily(ValueEqualFont("body")),
        )
    }
}
