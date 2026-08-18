package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core runtime 中的 Subtree Recomposition 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Subtree Recomposition behavior in widget-core runtime and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.text.TextDocument
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.environment.UiLocaleList
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.TextAlign
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.LazyListItemSessionStrategy
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.UiImageLoadHandle
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.UiImageRequest
import com.viewcompose.ui.node.UiImageTarget
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.ImageNodeSpec
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.node.policy.LazyContentPadding
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

        assertEquals("root-R2", rightSpec.document.text)
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
    fun `session backed vnode skips ordinary captures absent an explicit revision`() {
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

        assertSame(first, second)
    }

    @Test
    fun `lazy collector reuses committed items by key across structural reorder`() {
        data class Row(
            val id: String,
            val revision: Int,
        )

        val composer = ComposerLite()
        var rows = listOf(
            Row(id = "A", revision = 1),
            Row(id = "B", revision = 1),
            Row(id = "C", revision = 1),
        )

        fun compose(): List<LazyListItem> =
            ComposerContext.withComposer(composer) {
                composer.requestRootRecompose()
                composer.composeRoot {
                    buildVNodeTree {
                        LazyColumn(
                            items = rows,
                            key = Row::id,
                            contentType = { "row" },
                            contentRevision = Row::revision,
                        ) { row ->
                            Text(row.id)
                        }
                    }.single()
                }.also {
                    composer.commitSideEffects()
                }
            }.let { node ->
                (node.spec as LazyColumnNodeProps).items
            }

        val first = compose().associateBy(LazyListItem::key)
        rows = listOf(
            Row(id = "C", revision = 1),
            Row(id = "A", revision = 2),
            Row(id = "B", revision = 1),
        )
        val second = compose().associateBy(LazyListItem::key)
        rows = listOf(
            Row(id = "A", revision = 1),
            Row(id = "B", revision = 1),
            Row(id = "C", revision = 1),
        )
        val reset = compose().associateBy(LazyListItem::key)

        assertSame(first.getValue("B"), second.getValue("B"))
        assertSame(first.getValue("C"), second.getValue("C"))
        assertNotSame(first.getValue("A"), second.getValue("A"))
        assertSame(first.getValue("A"), reset.getValue("A"))
        assertSame(first.getValue("B"), reset.getValue("B"))
        assertSame(first.getValue("C"), reset.getValue("C"))
    }

    @Test
    fun `aborted lazy candidate never advances the committed reuse cache`() {
        val composer = ComposerLite()

        val first = composer.commitLazyItems(
            listOf(CacheRow(id = "item", revision = 0)),
        ).single()
        val aborted = composer.prepareLazyItems(
            listOf(CacheRow(id = "item", revision = 1)),
        )
        val abortedItem = aborted.value.lazyItems().single()

        aborted.abort()

        val third = composer.commitLazyItems(
            listOf(CacheRow(id = "item", revision = 2)),
        ).single()
        val retried = composer.commitLazyItems(
            listOf(CacheRow(id = "item", revision = 1)),
        ).single()

        assertNotSame(first, abortedItem)
        assertNotSame(abortedItem, third)
        assertNotSame(abortedItem, retried)
    }

    @Test
    fun `lazy canonical cache evicts a variant after two newer commits`() {
        val composer = ComposerLite()

        val first = composer.commitLazyItems(
            listOf(CacheRow(id = "item", revision = 0)),
        ).single()
        val second = composer.commitLazyItems(
            listOf(CacheRow(id = "item", revision = 1)),
        ).single()
        val third = composer.commitLazyItems(
            listOf(CacheRow(id = "item", revision = 2)),
        ).single()
        val firstRevisionAgain = composer.commitLazyItems(
            listOf(CacheRow(id = "item", revision = 0)),
        ).single()

        assertNotSame(first, second)
        assertNotSame(second, third)
        assertNotSame(first, firstRevisionAgain)
    }

    @Test
    fun `monotonic lazy revisions reuse only semantically unchanged keys`() {
        val composer = ComposerLite()

        val first = composer.commitLazyItems(
            listOf(
                CacheRow(id = "changing", revision = 0),
                CacheRow(id = "stable", revision = 0),
            ),
        ).associateBy(LazyListItem::key)
        val second = composer.commitLazyItems(
            listOf(
                CacheRow(id = "changing", revision = 1),
                CacheRow(id = "stable", revision = 0),
            ),
        ).associateBy(LazyListItem::key)
        val third = composer.commitLazyItems(
            listOf(
                CacheRow(id = "changing", revision = 2),
                CacheRow(id = "stable", revision = 0),
            ),
        ).associateBy(LazyListItem::key)

        assertNotSame(first.getValue("changing"), second.getValue("changing"))
        assertNotSame(second.getValue("changing"), third.getValue("changing"))
        assertSame(first.getValue("stable"), second.getValue("stable"))
        assertSame(first.getValue("stable"), third.getValue("stable"))
    }

    @Test
    fun `lazy collector invalidates retained items when environment changes`() {
        val composer = ComposerLite()
        val local = LocalValue { "default" }
        var environment = "first"

        fun compose(): LazyListItem =
            ComposerContext.withComposer(composer) {
                composer.requestRootRecompose()
                composer.composeRoot {
                    buildVNodeTree {
                        LocalContext.provide(local, environment) {
                            LazyColumn(
                                items = listOf("item"),
                                key = { item -> item },
                            ) { item ->
                                Text(item)
                            }
                        }
                    }.single()
                }.also {
                    composer.commitSideEffects()
                }
            }.let { node ->
                (node.spec as LazyColumnNodeProps).items.single()
            }

        val first = compose()
        environment = "second"
        val second = compose()

        assertNotSame(first, second)
    }

    @Test
    fun `new value equal collection snapshot reuses the committed node and item`() {
        val composer = ComposerLite()
        val strategy = object : LazyListItemSessionStrategy {
            override fun create(
                container: RenderContainerHandle,
                item: LazyListItem,
            ) = object : LazyListItemSession {
                override fun render() = true

                override fun dispose() = Unit
            }

            override fun update(
                session: LazyListItemSession,
                item: LazyListItem,
            ) = Unit
        }
        var item = LazyListItem(
            key = "item",
            contentRevision = "stable",
            sessionStrategy = strategy,
        )

        fun compose(): VNode =
            ComposerContext.withComposer(composer) {
                composer.requestRootRecompose()
                composer.composeRoot {
                    buildVNodeTree {
                        emit(
                            type = NodeType.LazyColumn,
                            spec = LazyColumnNodeProps(
                                contentPadding = LazyContentPadding(),
                                spacing = com.viewcompose.ui.unit.UiDp.Zero,
                                items = listOf(item),
                            ),
                        )
                    }.single()
                }
            }

        val first = compose()
        item = LazyListItem(
            key = "item",
            contentRevision = "stable",
            sessionStrategy = strategy,
        )
        val second = compose()

        assertSame(first, second)
        assertSame(
            (first.spec as LazyColumnNodeProps).items.single(),
            (second.spec as LazyColumnNodeProps).items.single(),
        )
    }

    @Test
    fun `changed emitted content closure refreshes ordinary captures and side effect once`() {
        val composer = ComposerLite()
        var label = "first"
        var sideEffectRuns = 0

        fun compose(): VNode =
            ComposerContext.withComposer(composer) {
                composer.requestRootRecompose()
                composer.composeRoot {
                    buildVNodeTree {
                        emit(
                            type = NodeType.Box,
                            spec = BoxNodeProps(
                                contentAlignment = BoxAlignment.TopStart,
                            ),
                        ) {
                            Text(label)
                            SideEffect { sideEffectRuns += 1 }
                        }
                    }.single()
                }.also {
                    composer.commitSideEffects()
                }
            }

        val first = compose()
        label = "second"
        val second = compose()

        assertEquals("first", (first.children.single().spec as TextNodeProps).document.text)
        assertEquals("second", (second.children.single().spec as TextNodeProps).document.text)
        assertEquals(2, sideEffectRuns)
        assertNotSame(first, second)
    }

    @Test
    fun `stable emitted content closure keeps container subtree reusable`() {
        val composer = ComposerLite()
        val stableContent: UiTreeBuilder.() -> Unit = {
            Text("stable")
        }

        fun compose(): VNode =
            ComposerContext.withComposer(composer) {
                composer.requestRootRecompose()
                composer.composeRoot {
                    buildVNodeTree {
                        emit(
                            type = NodeType.Box,
                            spec = BoxNodeProps(
                                contentAlignment = BoxAlignment.TopStart,
                            ),
                            content = stableContent,
                        )
                    }.single()
                }
            }

        val first = compose()
        val second = compose()

        assertSame(first, second)
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
        assertEquals("second", (second.spec as TextNodeProps).document.text)
    }

    @Test
    fun `tab selection recomposes only the previously and newly selected eager children`() {
        val selectedIndex = mutableStateOf(0)
        val composer = ComposerLite()
        val tabRuns = IntArray(3)

        fun compose(): VNode =
            ComposerContext.withComposer(composer) {
                composer.composeRoot {
                    buildVNodeTree {
                        TabRow(
                            selectedIndex = selectedIndex.value,
                            onTabSelected = {},
                        ) {
                            repeat(3) { index ->
                                Tab(
                                    key = "tab-$index",
                                    contentRevision = "stable",
                                ) { selected ->
                                    tabRuns[index] += 1
                                    Text("$index:$selected")
                                }
                            }
                        }
                    }.single()
                }
            }

        val first = compose()
        assertEquals(listOf(1, 1, 1), tabRuns.toList())

        selectedIndex.value = 1
        val second = compose()

        assertEquals(listOf(2, 2, 1), tabRuns.toList())
        assertNotSame(first.children[0], second.children[0])
        assertNotSame(first.children[1], second.children[1])
        assertSame(first.children[2], second.children[2])
    }

    private fun ComposerLite.prepareLazyItems(
        rows: List<CacheRow>,
    ): ComposerLite.PreparedComposition<VNode> {
        return ComposerContext.withComposer(this) {
            requestRootRecompose()
            prepareRoot {
                buildVNodeTree {
                    LazyColumn(
                        items = rows,
                        key = CacheRow::id,
                        contentType = { "row" },
                        contentRevision = CacheRow::revision,
                    ) { row ->
                        Text("${row.id}:${row.revision}")
                    }
                }.single()
            }
        }
    }

    private fun ComposerLite.commitLazyItems(rows: List<CacheRow>): List<LazyListItem> {
        val prepared = prepareLazyItems(rows)
        prepared.commit()
        commitSideEffects()
        return prepared.value.lazyItems()
    }

    private fun VNode.lazyItems(): List<LazyListItem> {
        return (spec as LazyColumnNodeProps).items
    }

    private fun textSpec(text: String): TextNodeProps {
        return TextNodeProps(
            document = TextDocument.plain(text),
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

    private data class CacheRow(
        val id: String,
        val revision: Int,
    )
}
