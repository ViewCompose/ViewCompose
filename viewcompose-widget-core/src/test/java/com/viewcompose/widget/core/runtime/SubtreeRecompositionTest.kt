package com.viewcompose.widget.core

import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.TextAlign
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class SubtreeRecompositionTest {
    @Test
    fun `emit reuses clean sibling vnode reference`() {
        val leading = mutableStateOf("A")
        val trailing = mutableStateOf("B")
        val composer = ComposerLite()

        fun compose(): List<VNode> =
            ComposerContext.withComposer(composer) {
                composer.composeRoot {
                    buildVNodeTree {
                        emit(
                            type = NodeType.Text,
                            key = "leading",
                            spec = textSpec(leading.value),
                        )
                        emit(
                            type = NodeType.Text,
                            key = "trailing",
                            spec = textSpec(trailing.value),
                        )
                    }
                }
            }

        val first = compose()
        trailing.value = "B2"
        val second = compose()

        assertSame(first[0], second[0])
        assertNotSame(first[1], second[1])
    }

    @Test
    fun `local snapshot stays stable during partial recomposition`() {
        val dynamicLabel = mutableStateOf("R1")
        val localLabel = LocalValue { "root" }
        val composer = ComposerLite()

        fun compose(): List<VNode> =
            ComposerContext.withComposer(composer) {
                composer.composeRoot {
                    buildVNodeTree {
                        LocalContext.provide(localLabel, "left") {
                            emit(
                                type = NodeType.Text,
                                key = "left",
                                spec = textSpec("${LocalContext.current(localLabel)}-fixed"),
                            )
                        }
                        emit(
                            type = NodeType.Text,
                            key = "right",
                            spec = textSpec("${LocalContext.current(localLabel)}-${dynamicLabel.value}"),
                        )
                    }
                }
            }

        compose()
        dynamicLabel.value = "R2"
        val updated = compose()
        val rightSpec = updated[1].spec as TextNodeProps

        assertEquals("root-R2", rightSpec.text)
    }

    @Test
    fun `dirty group reuses equivalent vnode result`() {
        val invalidatingState = mutableStateOf(0)
        val composer = ComposerLite()

        fun compose(): VNode =
            ComposerContext.withComposer(composer) {
                composer.composeRoot {
                    buildVNodeTree {
                        emit(
                            type = NodeType.Box,
                            spec = BoxNodeProps(
                                contentAlignment = BoxAlignment.TopStart,
                            ),
                        ) {
                            invalidatingState.value
                            emit(
                                type = NodeType.Text,
                                spec = textSpec("stable"),
                            )
                        }
                    }.single()
                }
            }

        val first = compose()
        invalidatingState.value = 1
        val second = compose()

        assertSame(first, second)
    }

    @Test
    fun `session backed vnode refreshes changed content closures`() {
        val contentVersion = mutableStateOf(0)
        val composer = ComposerLite()

        fun compose(): VNode =
            ComposerContext.withComposer(composer) {
                composer.composeRoot {
                    buildVNodeTree {
                        val version = contentVersion.value
                        LazyColumn(
                            items = listOf("item"),
                        ) { item ->
                            Text("$item:$version")
                        }
                    }.single()
                }
            }

        val first = compose()
        contentVersion.value = 1
        val second = compose()

        assertNotSame(first, second)
    }

    private fun textSpec(text: String): TextNodeProps {
        return TextNodeProps(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Start,
            textColor = 0xFF000000.toInt(),
            textSizeSp = 14,
        )
    }
}
