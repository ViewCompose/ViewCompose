package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core runtime 中的 Subtree Recomposition 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Subtree Recomposition behavior in widget-core runtime and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.environment.UiLocaleList
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.TextAlign
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.UiImageLoadHandle
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.UiImageRequest
import com.viewcompose.ui.node.UiImageTarget
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.ImageNodeSpec
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.unit.UiDensity
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
    fun `environment change invalidates an otherwise stable node group`() {
        val composer = ComposerLite()
        var environment = UiEnvironmentValues.Default

        fun compose(): VNode =
            ComposerContext.withComposer(composer) {
                composer.requestRootRecompose()
                composer.composeRoot {
                    buildVNodeTree {
                        UiEnvironment(environment) {
                            Text("stable")
                        }
                    }.single()
                }
            }

        val first = compose()
        environment = UiEnvironmentValues(
            density = UiDensity(
                density = 1.25f,
                fontScale = 1.2f,
            ),
            locales = UiLocaleList.of("ar"),
            layoutDirection = UiLayoutDirection.Rtl,
        )
        val second = compose()

        assertNotSame(first, second)
        assertEquals(environment, second.environment)
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
                        LazyColumn {
                            items(
                                items = listOf("item"),
                                key = { item -> item },
                            ) { item ->
                                Text("$item:$version")
                            }
                        }
                    }.single()
                }
            }

        val first = compose()
        contentVersion.value = 1
        val second = compose()

        assertNotSame(first, second)
    }

    @Test
    fun `image vnode refreshes when equal loaders have different identities`() {
        val composer = ComposerLite()
        var loader: UiImageLoader = EqualLoader("first")

        fun compose(): VNode =
            ComposerContext.withComposer(composer) {
                composer.requestRootRecompose()
                composer.composeRoot {
                    buildVNodeTree {
                        ProvideImageLoader(loader) {
                            Image(source = ImageSource.Resource(1))
                        }
                    }.single()
                }
            }

        val first = compose()
        loader = EqualLoader("second")
        val second = compose()

        assertNotSame(first, second)
        assertSame(loader, (second.spec as ImageNodeSpec).imageLoader)
    }

    @Test
    fun `explicit boundary skips stable multi-node component`() {
        val inside = mutableStateOf("inside-0")
        val outside = mutableStateOf("outside-0")
        val composer = ComposerLite()
        var boundaryRuns = 0

        fun compose(): List<VNode> =
            ComposerContext.withComposer(composer) {
                composer.composeRoot {
                    buildVNodeTree {
                        RecomposeBoundary(key = "section") {
                            boundaryRuns += 1
                            Text(inside.value)
                            Text("stable")
                        }
                        Text(outside.value)
                    }
                }
            }

        val first = compose()
        outside.value = "outside-1"
        val outsideUpdate = compose()

        assertEquals(1, boundaryRuns)
        assertSame(first[0], outsideUpdate[0])
        assertSame(first[1], outsideUpdate[1])

        inside.value = "inside-1"
        val insideUpdate = compose()

        assertEquals(2, boundaryRuns)
        assertNotSame(outsideUpdate[0], insideUpdate[0])
        assertSame(outsideUpdate[1], insideUpdate[1])
    }

    @Test
    fun `explicit boundary refreshes ordinary captures declared as inputs`() {
        val composer = ComposerLite()
        var label = "first"

        fun compose(): VNode =
            ComposerContext.withComposer(composer) {
                composer.requestRootRecompose()
                composer.composeRoot {
                    buildVNodeTree {
                        RecomposeBoundary(inputs = listOf(label)) {
                            Text(label)
                        }
                    }.single()
                }
            }

        val first = compose()
        label = "second"
        val second = compose()

        assertNotSame(first, second)
        assertEquals("second", (second.spec as TextNodeProps).text)
    }

    private fun textSpec(text: String): TextNodeProps {
        return TextNodeProps(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Start,
            textColor = 0xFF000000.toInt(),
            textSizeSp = 14.sp,
        )
    }

    private class EqualLoader(
        private val label: String,
    ) : UiImageLoader {
        override fun load(
            target: UiImageTarget,
            request: UiImageRequest,
        ): UiImageLoadHandle = UiImageLoadHandle {}

        override fun equals(other: Any?): Boolean = other is EqualLoader

        override fun hashCode(): Int = 0

        override fun toString(): String = "EqualLoader($label)"
    }
}
