package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.unit.sp

import com.viewcompose.ui.unit.dp

/*
 * 测试职责：覆盖 renderer view/tree 中的 View Tree Render Transaction 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers View Tree Render Transaction behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.renderer.R
import com.viewcompose.renderer.decoration.AndroidViewDecorationRuntime
import com.viewcompose.renderer.decoration.RecordingDecorationBackend
import com.viewcompose.renderer.view.container.DeclarativeAnimatedBoundsHostLayout
import com.viewcompose.renderer.view.requireUiEnvironment
import com.viewcompose.renderer.view.shape.UiShapeDrawable
import com.viewcompose.text.TextDocument
import com.viewcompose.text.TextFieldState
import com.viewcompose.text.TextFieldValue
import com.viewcompose.text.TextRange
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.NativeViewElement
import com.viewcompose.ui.modifier.AnimateBoundsModifierElement
import com.viewcompose.ui.modifier.ContentSizeEasingModel
import com.viewcompose.ui.modifier.ContentSizeTweenSpecModel
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.innerShadow
import com.viewcompose.ui.modifier.marginRelative
import com.viewcompose.ui.modifier.offsetRelative
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.paddingRelative
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.modifier.systemBarsInsetsPadding
import com.viewcompose.ui.modifier.systemBarsInsetsPaddingRelative
import com.viewcompose.ui.modifier.width
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.asLazyItemTable
import com.viewcompose.ui.node.lazyListItemSessionStrategy
import com.viewcompose.ui.node.TextFieldKeyboardOptions
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.policy.LazyContentPadding
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.ui.node.spec.AndroidViewOperationException
import com.viewcompose.ui.node.spec.ColumnNodeProps
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.LazyVerticalGridNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.node.spec.TextFieldNodeProps
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.shape.UiCornerFamily
import com.viewcompose.ui.shape.UiCornerSize
import com.viewcompose.ui.unit.UiDensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ViewTreeRenderTransactionTest {
    private lateinit var decorationBackend: RecordingDecorationBackend

    @Before
    fun installDecorationBackend() {
        decorationBackend = RecordingDecorationBackend()
        AndroidViewDecorationRuntime.install(decorationBackend)
    }

    @After
    fun resetDecorationBackend() {
        AndroidViewDecorationRuntime.resetForTests()
    }

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun `environment density and font scale are reapplied to an existing text view`() {
        val container = FrameLayout(context)
        val firstNode = environmentTextNode(density = 2f, fontScale = 1f)
        val first = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(firstNode),
        )
        val textView = container.getChildAt(0) as TextView
        assertEquals(20f, textView.textSize, 0.01f)

        ViewTreeRenderer.renderInto(
            container = container,
            previous = first.mountedNodes,
            nodes = listOf(environmentTextNode(density = 3f, fontScale = 1.5f)),
        )

        assertSame(textView, container.getChildAt(0))
        assertEquals(45f, textView.textSize, 0.01f)
    }

    @Test
    @Config(sdk = [27])
    fun `implicit line height follows scaled font metrics`() {
        val container = FrameLayout(context)

        ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                environmentTextNode(
                    density = 3f,
                    fontScale = 1.3f,
                    text = "Android resource\nenvironment",
                    maxLines = 2,
                    textSizeSp = 24,
                ),
            ),
        )

        val textView = container.getChildAt(0) as TextView
        val fontMetricsHeight = textView.paint.fontMetricsInt.run { descent - ascent }
        assertTrue(
            "implicit line height must not install negative line spacing",
            textView.lineSpacingExtra >= 0f,
        )
        assertTrue(
            "implicit line height ${textView.lineHeight}px must fit ${fontMetricsHeight}px font metrics",
            textView.lineHeight >= fontMetricsHeight,
        )
    }

    @Test
    fun `removing explicit line height restores the native line spacing baseline`() {
        val textView = TextView(context).apply {
            setLineSpacing(6f, 1.2f)
        }

        ContentViewBinder.applyTextAppearance(
            view = textView,
            textSizePx = 72f,
            lineHeightPx = 96,
        )
        assertEquals(96, textView.lineHeight)

        ContentViewBinder.applyTextAppearance(
            view = textView,
            lineHeightPx = null,
        )

        assertEquals(6f, textView.lineSpacingExtra, 0.01f)
        assertEquals(1.2f, textView.lineSpacingMultiplier, 0.01f)
    }

    @Test
    fun `lazy content modifier and system bar padding survive environment rebind`() {
        for (nodeType in listOf(NodeType.LazyColumn, NodeType.LazyVerticalGrid)) {
            val container = FrameLayout(context)
            val first = ViewTreeRenderer.renderInto(
                container = container,
                previous = emptyList(),
                nodes = listOf(
                    lazyCollectionEnvironmentNode(
                        nodeType = nodeType,
                        resourceRevision = 1L,
                    ),
                ),
            )
            val recyclerView = container.getChildAt(0) as RecyclerView
            val systemBars = Insets.of(2, 24, 4, 6)
            ViewCompat.dispatchApplyWindowInsets(
                recyclerView,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(), systemBars)
                    .build(),
            )

            assertEquals(21, recyclerView.paddingLeft)
            assertEquals(29, recyclerView.paddingTop)
            assertEquals(27, recyclerView.paddingRight)
            assertEquals(17, recyclerView.paddingBottom)

            ViewTreeRenderer.renderInto(
                container = container,
                previous = first.mountedNodes,
                nodes = listOf(
                    lazyCollectionEnvironmentNode(
                        nodeType = nodeType,
                        resourceRevision = 2L,
                    ),
                ),
            )

            assertSame(recyclerView, container.getChildAt(0))
            assertEquals(21, recyclerView.paddingLeft)
            assertEquals(29, recyclerView.paddingTop)
            assertEquals(27, recyclerView.paddingRight)
            assertEquals(17, recyclerView.paddingBottom)
        }
    }

    @Test
    fun `lazy logical content padding follows direction changes`() {
        val container = FrameLayout(context)
        val first = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                lazyCollectionEnvironmentNode(
                    resourceRevision = 1L,
                    layoutDirection = UiLayoutDirection.Ltr,
                ),
            ),
        )
        val recyclerView = container.getChildAt(0) as RecyclerView
        assertEquals(19, recyclerView.paddingLeft)
        assertEquals(23, recyclerView.paddingRight)

        ViewTreeRenderer.renderInto(
            container = container,
            previous = first.mountedNodes,
            nodes = listOf(
                lazyCollectionEnvironmentNode(
                    resourceRevision = 2L,
                    layoutDirection = UiLayoutDirection.Rtl,
                ),
            ),
        )

        assertSame(recyclerView, container.getChildAt(0))
        assertEquals(23, recyclerView.paddingLeft)
        assertEquals(19, recyclerView.paddingRight)
    }

    @Test
    fun `relative layout modifiers re-resolve on runtime direction changes`() {
        val container = FrameLayout(context)
        val relativeModifier = Modifier
            .padding(left = 1.dp, right = 2.dp)
            .paddingRelative(start = 10.dp, end = 20.dp)
            .marginRelative(start = 6.dp, end = 8.dp)
            .offsetRelative(horizontal = 4.dp, vertical = 5.dp)
            .systemBarsInsetsPaddingRelative(
                start = true,
                top = false,
                end = false,
                bottom = false,
            )
        val first = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(relativeTextNode(UiLayoutDirection.Ltr, relativeModifier)),
        )
        val view = first.mountedNodes.single().view as TextView
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(3, 0, 7, 0))
            .build()
        ViewCompat.dispatchApplyWindowInsets(view, insets)

        assertEquals(13, view.paddingLeft)
        assertEquals(20, view.paddingRight)
        assertEquals(4f, view.translationX)
        assertEquals(5f, view.translationY)
        assertEquals(6, (view.layoutParams as FrameLayout.LayoutParams).leftMargin)
        assertEquals(8, (view.layoutParams as FrameLayout.LayoutParams).rightMargin)

        val second = ViewTreeRenderer.renderInto(
            container = container,
            previous = first.mountedNodes,
            nodes = listOf(relativeTextNode(UiLayoutDirection.Rtl, relativeModifier)),
        )
        ViewCompat.dispatchApplyWindowInsets(view, insets)

        assertSame(view, second.mountedNodes.single().view)
        assertEquals(UiLayoutDirection.Rtl, view.requireUiEnvironment().layoutDirection)
        assertEquals(20, view.paddingLeft)
        assertEquals(17, view.paddingRight)
        assertEquals(-4f, view.translationX)
        assertEquals(5f, view.translationY)
        assertEquals(8, (view.layoutParams as FrameLayout.LayoutParams).leftMargin)
        assertEquals(6, (view.layoutParams as FrameLayout.LayoutParams).rightMargin)
    }

    @Test
    fun `node style patch preserves modifier shape override`() {
        val container = FrameLayout(context)
        val state = TextFieldState(TextFieldValue("value"))
        val modifierShape = UiShape.cut(14.dp)
        val modifier = Modifier.shape(modifierShape)
        val initialResult = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                textFieldNode(
                    state = state,
                    modifier = modifier,
                    nodeShape = UiShape.rounded(4.dp),
                ),
            ),
        )
        val previous = initialResult.mountedNodes
        assertEquals(NodeType.TextField, initialResult.tree.single().type)
        assertEquals(RenderPatchOperation.Insert, initialResult.patches.single().operation)

        val nextResult = ViewTreeRenderer.renderInto(
            container = container,
            previous = previous,
            nodes = listOf(
                textFieldNode(
                    state = state,
                    modifier = modifier,
                    nodeShape = UiShape.rounded(28.dp),
                    backgroundColor = 0xFF112233.toInt(),
                ),
            ),
        )
        val next = nextResult.mountedNodes
        assertEquals(RenderPatchOperation.Patch, nextResult.patches.single().operation)
        assertTrue(nextResult.patches.single().detail?.contains("TextFieldNodePatch") == true)

        val drawable = next.single().view.background as UiShapeDrawable
        assertEquals(UiCornerFamily.Cut, drawable.currentShape.topStart.family)
        assertEquals(UiCornerSize.Absolute(14.dp), drawable.currentShape.topStart.size)
    }

    @Test
    fun `inner shadow preserves native text editing across modifier patch`() {
        val container = FrameLayout(context)
        val state = TextFieldState(TextFieldValue("value"))
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                textFieldNode(
                    state = state,
                    modifier = Modifier.innerShadow(
                        UiShadow(
                            color = 0x33000000,
                            blurRadius = 4.dp,
                        ),
                    ),
                ),
            ),
        )
        val editText = initial.mountedNodes.single().view as ViewComposeEditText
        editText.setSelection(2)
        editText.text?.insert(2, "X")

        assertEquals("vaXlue", state.value.text)
        assertEquals(3, editText.selectionStart)
        requireNotNull(decorationBackend.requestOrNull(editText))

        val patched = ViewTreeRenderer.renderInto(
            container = container,
            previous = initial.mountedNodes,
            nodes = listOf(
                textFieldNode(
                    state = state,
                    modifier = Modifier.innerShadow(
                        UiShadow(
                            color = 0x55000000,
                            blurRadius = 6.dp,
                        ),
                    ),
                ),
            ),
        )

        assertSame(editText, patched.mountedNodes.single().view)
        assertEquals(0, patched.stats.reboundNodes)
        assertEquals(1, patched.stats.patchedNodes)
        assertEquals(RenderPatchOperation.Patch, patched.patches.single().operation)
        assertEquals("TextFieldNodePatch", patched.patches.single().detail)
        assertEquals("vaXlue", editText.text.toString())
        assertEquals(3, editText.selectionStart)
        assertEquals(
            6.dp,
            requireNotNull(decorationBackend.requestOrNull(editText))
                .innerShadows.single().shadows.single().blurRadius,
        )
    }

    @Test
    fun `visual-only modifier patch preserves layout params identity`() {
        val container = FrameLayout(context)
        val state = TextFieldState(TextFieldValue("value"))
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                textFieldNode(
                    state = state,
                    modifier = Modifier.backgroundColor(0xFF112233.toInt()),
                ),
            ),
        )
        val view = initial.mountedNodes.single().view
        val layoutParams = view.layoutParams

        val patched = ViewTreeRenderer.renderInto(
            container = container,
            previous = initial.mountedNodes,
            nodes = listOf(
                textFieldNode(
                    state = state,
                    modifier = Modifier.backgroundColor(0xFF445566.toInt()),
                ),
            ),
        )

        assertSame(view, patched.mountedNodes.single().view)
        assertSame(layoutParams, view.layoutParams)
        assertEquals(0, patched.stats.reboundNodes)
        assertEquals(1, patched.stats.patchedNodes)
        assertEquals("ModifierOnly", patched.patches.single().detail)
    }

    @Test
    fun `layout modifier patch replaces layout params without rebinding`() {
        val container = FrameLayout(context)
        val state = TextFieldState(TextFieldValue("value"))
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(textFieldNode(state = state, modifier = Modifier.width(40.dp))),
        )
        val view = initial.mountedNodes.single().view
        val layoutParams = view.layoutParams

        val patched = ViewTreeRenderer.renderInto(
            container = container,
            previous = initial.mountedNodes,
            nodes = listOf(textFieldNode(state = state, modifier = Modifier.width(80.dp))),
        )

        assertSame(view, patched.mountedNodes.single().view)
        assertNotSame(layoutParams, view.layoutParams)
        assertEquals(80, view.layoutParams.width)
        assertEquals(0, patched.stats.reboundNodes)
        assertEquals(1, patched.stats.patchedNodes)
    }

    @Test
    fun `modifier-only failure restores previous native configuration`() {
        val container = FrameLayout(context)
        val state = TextFieldState(TextFieldValue("value"))
        val previousNode = textFieldNode(
            state = state,
            modifier = Modifier.then(
                NativeViewElement(stableKey = "old") { nativeView ->
                    (nativeView as View).tag = "old"
                },
            ),
        )
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(previousNode),
        )
        val view = initial.mountedNodes.single().view
        assertEquals("old", view.tag)

        val error = runCatching {
            ViewTreeRenderer.renderInto(
                container = container,
                previous = initial.mountedNodes,
                nodes = listOf(
                    textFieldNode(
                        state = state,
                        modifier = Modifier.then(
                            NativeViewElement(stableKey = "failing") { nativeView ->
                                (nativeView as View).tag = "new"
                                error("native config failed")
                            },
                        ),
                    ),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("native config failed", error?.message)
        assertSame(previousNode, initial.mountedNodes.single().vnode)
        assertEquals("old", view.tag)
    }

    @Test
    fun `android view modifier-only patch skips native lifecycle callbacks`() {
        val container = FrameLayout(context)
        var updates = 0
        var resets = 0
        var releases = 0
        var commits = 0
        val spec = AndroidViewNodeProps(
            factory = { rawContext, _ -> View(rawContext as Context) },
            update = { _, _ -> updates += 1 },
            onReset = { _, _ -> resets += 1 },
            onRelease = { releases += 1 },
            onCommit = { _, _ -> commits += 1 },
        )
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                VNode(
                    type = NodeType.AndroidView,
                    key = "interop",
                    spec = spec,
                    modifier = Modifier.backgroundColor(0xFF112233.toInt()),
                ),
            ),
        )
        val view = initial.mountedNodes.single().view
        assertEquals(1, updates)
        assertEquals(1, initial.commitEffects.size)

        val patched = ViewTreeRenderer.renderInto(
            container = container,
            previous = initial.mountedNodes,
            nodes = listOf(
                VNode(
                    type = NodeType.AndroidView,
                    key = "interop",
                    spec = spec,
                    modifier = Modifier.backgroundColor(0xFF445566.toInt()),
                ),
            ),
        )

        assertSame(view, patched.mountedNodes.single().view)
        assertEquals(1, updates)
        assertEquals(0, resets)
        assertEquals(0, releases)
        assertEquals(0, commits)
        assertTrue(patched.commitEffects.isEmpty())
        assertEquals(0, patched.stats.reboundNodes)
        assertEquals(1, patched.stats.patchedNodes)
    }

    @Test
    fun `same construction identity rebinds without invoking cross-key reset`() {
        val container = FrameLayout(context)
        var resets = 0
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                androidNode(
                    key = "interop",
                    value = "old",
                    onReset = { resets += 1 },
                    constructionIdentity = "style-a",
                ),
            ),
        )
        val view = initial.mountedNodes.single().view

        val rebound = ViewTreeRenderer.renderInto(
            container = container,
            previous = initial.mountedNodes,
            nodes = listOf(
                androidNode(
                    key = "interop",
                    value = "new",
                    onReset = { resets += 1 },
                    constructionIdentity = "style-a",
                ),
            ),
        )

        assertSame(view, rebound.mountedNodes.single().view)
        assertEquals("new", view.tag)
        assertEquals(0, resets)
    }

    @Test
    fun `android view lifecycle receives the owning immutable environment`() {
        val container = FrameLayout(context)
        val environment = UiEnvironmentValues.Default.copy(resourceRevision = 42L)
        val revisions = mutableListOf<Long>()
        val node = VNode(
            type = NodeType.AndroidView,
            key = "interop",
            spec = AndroidViewNodeProps(
                factory = { rawContext, values ->
                    revisions += values.resourceRevision
                    View(rawContext as Context)
                },
                update = { _, values -> revisions += values.resourceRevision },
                onReset = { _, values -> revisions += values.resourceRevision },
                onCommit = { _, values -> revisions += values.resourceRevision },
            ),
            environment = environment,
        )

        val rendered = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(node),
        )
        rendered.commitEffects.single().commit()
        assertTrue(ViewTreeRenderer.detachMountedForReuse(container, rendered.mountedNodes))

        assertEquals(listOf(42L, 42L, 42L, 42L), revisions)
        ViewTreeRenderer.releaseReusableMounted(rendered.mountedNodes)
    }

    @Test
    fun `construction identity change atomically replaces and releases displaced view`() {
        val container = FrameLayout(context)
        var oldReleases = 0
        var newReleases = 0
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                androidNode(
                    key = "interop",
                    value = "old",
                    onRelease = { oldReleases += 1 },
                    constructionIdentity = "style-a",
                    adapterName = "player-adapter",
                    lifecycleMode = "AdapterManaged",
                ),
            ),
        )
        val oldView = initial.mountedNodes.single().view

        val replaced = ViewTreeRenderer.renderInto(
            container = container,
            previous = initial.mountedNodes,
            nodes = listOf(
                androidNode(
                    key = "interop",
                    value = "new",
                    onRelease = { newReleases += 1 },
                    constructionIdentity = "style-b",
                    adapterName = "player-adapter",
                    lifecycleMode = "AdapterManaged",
                ),
            ),
        )

        val newView = replaced.mountedNodes.single().view
        assertNotSame(oldView, newView)
        assertSame(newView, container.getChildAt(0))
        assertEquals("new", newView.tag)
        assertEquals(1, oldReleases)
        assertEquals(0, newReleases)
        assertTrue(replaced.patches.single().detail.orEmpty().contains("generation=1"))
        assertTrue(replaced.patches.single().detail.orEmpty().contains("lifecycle=AdapterManaged"))
        assertTrue(replaced.patches.single().detail.orEmpty().contains("replacement=true"))

        ViewTreeRenderer.disposeMounted(container, replaced.mountedNodes)
        assertEquals(1, newReleases)
    }

    @Test
    fun `failed construction candidate releases candidate and preserves committed view`() {
        val container = FrameLayout(context)
        var oldReleases = 0
        var candidateReleases = 0
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                androidNode(
                    key = "interop",
                    value = "old",
                    onRelease = { oldReleases += 1 },
                    constructionIdentity = "style-a",
                ),
            ),
        )
        val oldMounted = initial.mountedNodes.single()

        val error = runCatching {
            ViewTreeRenderer.renderInto(
                container = container,
                previous = initial.mountedNodes,
                nodes = listOf(
                    androidNode(
                        key = "interop",
                        value = "broken",
                        failUpdate = true,
                        onRelease = { candidateReleases += 1 },
                        constructionIdentity = "style-b",
                    ),
                ),
            )
        }.exceptionOrNull()

        assertAndroidViewUpdateFailure(error)
        assertSame(oldMounted.view, container.getChildAt(0))
        assertSame(oldMounted, initial.mountedNodes.single())
        assertEquals("old", oldMounted.view.tag)
        assertEquals(0, oldReleases)
        assertEquals(1, candidateReleases)
    }

    @Test
    fun `later frame failure rolls back construction replacement`() {
        val container = FrameLayout(context)
        var displacedReleases = 0
        var candidateReleases = 0
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                androidNode(
                    key = "first",
                    value = "old",
                    onRelease = { displacedReleases += 1 },
                    constructionIdentity = "style-a",
                ),
                androidNode(key = "second", value = "stable"),
            ),
        )
        val previousViews = initial.mountedNodes.map(MountedNode::view)

        val error = runCatching {
            ViewTreeRenderer.renderInto(
                container = container,
                previous = initial.mountedNodes,
                nodes = listOf(
                    androidNode(
                        key = "first",
                        value = "candidate",
                        onRelease = { candidateReleases += 1 },
                        constructionIdentity = "style-b",
                    ),
                    androidNode(key = "second", value = "broken", failUpdate = true),
                ),
            )
        }.exceptionOrNull()

        assertAndroidViewUpdateFailure(error)
        assertSame(previousViews[0], container.getChildAt(0))
        assertSame(previousViews[1], container.getChildAt(1))
        assertEquals("old", previousViews[0].tag)
        assertEquals("stable", previousViews[1].tag)
        assertEquals(0, displacedReleases)
        assertEquals(1, candidateReleases)
    }

    @Test
    fun `failed rebind restores previous vnode values and view order`() {
        val container = FrameLayout(context)
        val previousNodes = listOf(
            androidNode(key = "first", value = "old-first"),
            androidNode(key = "second", value = "old-second"),
        )
        val previous = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = previousNodes,
        ).mountedNodes
        val previousViews = previous.map(MountedNode::view)
        val previousVNodes = previous.map(MountedNode::vnode)

        val error = runCatching {
            ViewTreeRenderer.renderInto(
                container = container,
                previous = previous,
                nodes = listOf(
                    androidNode(key = "first", value = "new-first"),
                    androidNode(
                        key = "second",
                        value = "new-second",
                        failUpdate = true,
                    ),
                ),
            )
        }.exceptionOrNull()

        assertAndroidViewUpdateFailure(error)
        assertEquals(2, container.childCount)
        previousViews.forEachIndexed { index, view ->
            assertSame(view, container.getChildAt(index))
        }
        assertEquals("old-first", previous[0].view.tag)
        assertEquals("old-second", previous[1].view.tag)
        assertSame(previousVNodes[0], previous[0].vnode)
        assertSame(previousVNodes[1], previous[1].vnode)
    }

    @Test
    fun `failed frame releases inserted nodes and restores previous children`() {
        val container = FrameLayout(context)
        var insertedReleases = 0
        val stableNode = androidNode(key = "stable", value = "stable")
        val previous = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(stableNode),
        ).mountedNodes
        val stableView = previous.single().view
        val previousVNode = previous.single().vnode

        val error = runCatching {
            ViewTreeRenderer.renderInto(
                container = container,
                previous = previous,
                nodes = listOf(
                    androidNode(
                        key = "inserted",
                        value = "inserted",
                        onRelease = { insertedReleases += 1 },
                    ),
                    androidNode(
                        key = "stable",
                        value = "broken",
                        failUpdate = true,
                    ),
                ),
            )
        }.exceptionOrNull()

        assertAndroidViewUpdateFailure(error)
        assertEquals(1, insertedReleases)
        assertEquals(1, container.childCount)
        assertSame(stableView, container.getChildAt(0))
        assertSame(previousVNode, previous.single().vnode)
        assertEquals("stable", stableView.tag)
    }

    @Test
    fun `nested failure rolls back earlier child updates in the same frame`() {
        val container = FrameLayout(context)
        val previous = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                columnNode(
                    androidNode(key = "first", value = "old-first"),
                    androidNode(key = "second", value = "old-second"),
                ),
            ),
        ).mountedNodes
        val previousRootVNode = previous.single().vnode
        val previousChildren = previous.single().children.toList()

        val error = runCatching {
            ViewTreeRenderer.renderInto(
                container = container,
                previous = previous,
                nodes = listOf(
                    columnNode(
                        androidNode(key = "first", value = "new-first"),
                        androidNode(
                            key = "second",
                            value = "new-second",
                            failUpdate = true,
                        ),
                    ),
                ),
            )
        }.exceptionOrNull()

        assertAndroidViewUpdateFailure(error)
        assertSame(previousRootVNode, previous.single().vnode)
        assertEquals(previousChildren, previous.single().children)
        assertEquals("old-first", previousChildren[0].view.tag)
        assertEquals("old-second", previousChildren[1].view.tag)
    }

    @Test
    fun `retained lazy child publishes latest closure only from parent commit effect`() {
        val container = FrameLayout(context)
        val events = mutableListOf<String>()
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(lazySessionNode(label = "old", events = events)),
        )
        initial.commitEffects.forEach { effect -> effect.commit() }
        val recyclerView = initial.mountedNodes.single().view as RecyclerView
        val adapter = recyclerView.adapter as com.viewcompose.renderer.view.lazy.adapter.LazyListAdapter
        val holder = adapter.onCreateViewHolder(recyclerView, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)
        assertEquals(listOf("update:old", "render:old"), events)

        val candidate = ViewTreeRenderer.renderInto(
            container = container,
            previous = initial.mountedNodes,
            nodes = listOf(lazySessionNode(label = "new", events = events)),
        )

        assertEquals(
            "Child composition must remain unchanged until the parent frame commits.",
            listOf("update:old", "render:old"),
            events,
        )
        candidate.commitEffects.forEach { effect -> effect.commit() }
        assertEquals(
            listOf("update:old", "render:old", "update:new", "render:new"),
            events,
        )
    }

    @Test
    fun `failed parent frame discards retained lazy child submission`() {
        val container = FrameLayout(context)
        val events = mutableListOf<String>()
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                lazySessionNode(label = "old", events = events),
                androidNode(key = "failure", value = "stable"),
            ),
        )
        initial.commitEffects.forEach { effect -> effect.commit() }
        val recyclerView = initial.mountedNodes.first().view as RecyclerView
        val adapter = recyclerView.adapter as com.viewcompose.renderer.view.lazy.adapter.LazyListAdapter
        val holder = adapter.onCreateViewHolder(recyclerView, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)
        assertEquals(listOf("update:old", "render:old"), events)

        val error = runCatching {
            ViewTreeRenderer.renderInto(
                container = container,
                previous = initial.mountedNodes,
                nodes = listOf(
                    lazySessionNode(label = "new", events = events),
                    androidNode(
                        key = "failure",
                        value = "broken",
                        failUpdate = true,
                    ),
                ),
            )
        }.exceptionOrNull()

        assertAndroidViewUpdateFailure(error)
        assertEquals(
            "A rolled-back parent frame must not update or render its retained child session.",
            listOf("update:old", "render:old"),
            events,
        )
    }

    @Test
    fun `removed nodes release only after a successful frame`() {
        val container = FrameLayout(context)
        var releases = 0
        val previous = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                androidNode(
                    key = "removed",
                    value = "value",
                    onRelease = { releases += 1 },
                ),
            ),
        ).mountedNodes

        val result = ViewTreeRenderer.renderInto(
            container = container,
            previous = previous,
            nodes = emptyList(),
        )

        assertTrue(result.mountedNodes.isEmpty())
        assertEquals(0, container.childCount)
        assertEquals(1, releases)
    }

    @Test
    fun `failed frame restores text value selection and composing snapshot`() {
        val container = FrameLayout(context)
        val state = TextFieldState(
            TextFieldValue(
                text = "old",
                selection = TextRange(1, 3),
                composition = TextRange(0, 3),
            ),
        )
        val previous = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                textFieldNode(state),
                androidNode(key = "failure", value = "stable"),
            ),
        ).mountedNodes

        state.setTextAndPlaceCursorAtEnd("new value")
        val error = runCatching {
            ViewTreeRenderer.renderInto(
                container = container,
                previous = previous,
                nodes = listOf(
                    textFieldNode(state),
                    androidNode(
                        key = "failure",
                        value = "broken",
                        failUpdate = true,
                    ),
                ),
            )
        }.exceptionOrNull()

        val view = previous[0].view as ViewComposeEditText
        assertAndroidViewUpdateFailure(error)
        assertEquals("old", view.text.toString())
        assertEquals(1, view.selectionStart)
        assertEquals(3, view.selectionEnd)
        assertEquals(
            0,
            android.view.inputmethod.BaseInputConnection.getComposingSpanStart(view.editableText),
        )
        assertEquals(
            3,
            android.view.inputmethod.BaseInputConnection.getComposingSpanEnd(view.editableText),
        )
    }

    @Test
    fun `android view commit effect is published only after a successful tree transaction`() {
        val container = FrameLayout(context)
        var commits = 0

        val result = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                androidNode(
                    key = "committed",
                    value = "value",
                    onCommit = { commits += 1 },
                ),
            ),
        )

        assertEquals(0, commits)
        assertEquals(1, result.commitEffects.size)
        result.commitEffects.single().commit()
        assertEquals(1, commits)

        val error = runCatching {
            ViewTreeRenderer.renderInto(
                container = container,
                previous = result.mountedNodes,
                nodes = listOf(
                    androidNode(
                        key = "committed",
                        value = "next",
                        onCommit = { commits += 10 },
                    ),
                    androidNode(
                        key = "failure",
                        value = "broken",
                        failUpdate = true,
                    ),
                ),
            )
        }.exceptionOrNull()

        assertAndroidViewUpdateFailure(error)
        assertEquals(1, commits)
    }

    @Test
    fun `failed frame restores bounds animation spec and parent layout contract`() {
        val container = FrameLayout(context)
        val previousTiming = ContentSizeTweenSpecModel(
            durationMillis = 180,
            delayMillis = 0,
            easing = ContentSizeEasingModel.Linear,
        )
        val previous = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                animatedBoundsTextNode(width = 120, timing = previousTiming),
                androidNode(key = "failure", value = "stable"),
            ),
        ).mountedNodes
        val host = container.getChildAt(0) as DeclarativeAnimatedBoundsHostLayout
        assertEquals(120, host.layoutParams.width)
        assertEquals(previousTiming, host.animationSpec)

        val error = runCatching {
            ViewTreeRenderer.renderInto(
                container = container,
                previous = previous,
                nodes = listOf(
                    animatedBoundsTextNode(
                        width = 220,
                        timing = ContentSizeTweenSpecModel(
                            durationMillis = 720,
                            delayMillis = 24,
                            easing = ContentSizeEasingModel.FastOutSlowIn,
                        ),
                    ),
                    androidNode(key = "failure", value = "broken", failUpdate = true),
                ),
            )
        }.exceptionOrNull()

        assertAndroidViewUpdateFailure(error)
        assertSame(host, container.getChildAt(0))
        assertEquals(120, host.layoutParams.width)
        assertEquals(previousTiming, host.animationSpec)
    }

    @Test
    fun `android view commit and release failures keep structured operation context`() {
        val container = FrameLayout(context)
        val mounted = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                androidNode(
                    key = "interop",
                    value = "value",
                    onCommit = { error("commit failed") },
                    onRelease = { error("release failed") },
                ),
            ),
        )

        val commitError = runCatching {
            mounted.commitEffects.single().commit()
        }.exceptionOrNull()
        assertTrue(commitError is AndroidViewOperationException)
        assertEquals(
            AndroidViewOperation.Commit,
            (commitError as AndroidViewOperationException).operation,
        )
        assertEquals("interop", commitError.nodeKey)

        val removed = ViewTreeRenderer.renderInto(
            container = container,
            previous = mounted.mountedNodes,
            nodes = emptyList(),
        )

        val releaseFailure = removed.commitFailures.single()
        assertEquals(AndroidViewOperation.Release, releaseFailure.operation)
        assertEquals("interop", releaseFailure.nodeKey)
        assertTrue(releaseFailure.cause is AndroidViewOperationException)
        assertTrue(removed.mountedNodes.isEmpty())
        assertEquals(0, container.childCount)
    }

    @Test
    fun `cross owner reuse requires reset and releases only on final disposal`() {
        val firstContainer = FrameLayout(context)
        val secondContainer = FrameLayout(context)
        var resets = 0
        var releases = 0
        val first = ViewTreeRenderer.renderInto(
            container = firstContainer,
            previous = emptyList(),
            nodes = listOf(
                androidNode(
                    key = "first",
                    value = "old",
                    onReset = { resets += 1 },
                    onRelease = { releases += 1 },
                ),
            ),
        )
        val nativeView = first.mountedNodes.single().view

        assertTrue(ViewTreeRenderer.detachMountedForReuse(firstContainer, first.mountedNodes))
        assertEquals(1, resets)
        assertEquals(0, releases)
        ViewTreeRenderer.attachReusableMounted(secondContainer, first.mountedNodes)
        val rebound = ViewTreeRenderer.renderInto(
            container = secondContainer,
            previous = first.mountedNodes,
            nodes = listOf(
                androidNode(
                    key = "second",
                    value = "new",
                    onReset = { resets += 1 },
                    onRelease = { releases += 1 },
                ),
            ),
        )

        assertSame(nativeView, rebound.mountedNodes.single().view)
        assertEquals("new", nativeView.tag)
        assertEquals(1, resets)
        assertEquals(0, releases)
        ViewTreeRenderer.releaseReusableMounted(rebound.mountedNodes)
        assertEquals(1, releases)
    }

    @Test
    fun `logical owner transfer reuses a keyed root and patches changed descendants`() {
        val container = FrameLayout(context)
        val firstRoot = columnNode(
            environmentTextNode(density = 1f, fontScale = 1f, text = "old"),
        ).copy(key = "first-owner")
        val first = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(firstRoot),
        )
        val rootView = first.mountedNodes.single().view
        val textView = first.mountedNodes.single().children.single().view as TextView

        container.setTag(R.id.viewcompose_lazy_logical_owner_transfer, true)
        val transferred = try {
            ViewTreeRenderer.renderInto(
                container = container,
                previous = first.mountedNodes,
                nodes = listOf(
                    columnNode(
                        environmentTextNode(
                            density = 1f,
                            fontScale = 1f,
                            text = "new",
                        ),
                    ).copy(key = "second-owner"),
                ),
                collectStatistics = true,
            )
        } finally {
            container.setTag(R.id.viewcompose_lazy_logical_owner_transfer, null)
        }

        assertSame(rootView, transferred.mountedNodes.single().view)
        assertSame(textView, transferred.mountedNodes.single().children.single().view)
        assertEquals("new", textView.text.toString())
        assertEquals(0, transferred.stats.inserts)
        assertEquals(0, transferred.stats.removals)
        assertEquals(1, transferred.stats.patchedNodes)
    }

    @Test
    fun `cross owner reuse cancels bounds motion and settles the adopted layout`() {
        val firstContainer = FrameLayout(context)
        val secondContainer = FrameLayout(context)
        val timing = ContentSizeTweenSpecModel(
            durationMillis = 1_000,
            delayMillis = 0,
            easing = ContentSizeEasingModel.Linear,
        )
        val first = ViewTreeRenderer.renderInto(
            container = firstContainer,
            previous = emptyList(),
            nodes = listOf(animatedBoundsTextNode(width = 80, timing = timing)),
        )
        firstContainer.measureAndLayoutForBoundsReuse()
        val host = first.mountedNodes.single().view as DeclarativeAnimatedBoundsHostLayout
        val moving = ViewTreeRenderer.renderInto(
            container = firstContainer,
            previous = first.mountedNodes,
            nodes = listOf(animatedBoundsTextNode(width = 160, timing = timing)),
        )
        firstContainer.measureAndLayoutForBoundsReuse()
        assertTrue(host.animatorForTest() != null)

        assertTrue(ViewTreeRenderer.detachMountedForReuse(firstContainer, moving.mountedNodes))
        assertNull(host.animatorForTest())
        ViewTreeRenderer.attachReusableMounted(secondContainer, moving.mountedNodes)
        val rebound = ViewTreeRenderer.renderInto(
            container = secondContainer,
            previous = moving.mountedNodes,
            nodes = listOf(
                animatedBoundsTextNode(width = 120, timing = timing).copy(key = "adopted-bounds"),
            ),
        )
        secondContainer.measureAndLayoutForBoundsReuse()

        assertSame(host, rebound.mountedNodes.single().view)
        assertEquals(120, host.width)
        assertNull(host.animatorForTest())
        assertEquals(host.currentBoundsForTest(), host.targetBoundsForTest())
    }

    @Test
    fun `android view without reset never participates in cross owner reuse`() {
        val container = FrameLayout(context)
        var releases = 0
        val mounted = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                androidNode(
                    key = "interop",
                    value = "value",
                    onRelease = { releases += 1 },
                ),
            ),
        )

        assertFalse(ViewTreeRenderer.detachMountedForReuse(container, mounted.mountedNodes))
        assertEquals(1, container.childCount)
        assertEquals(0, releases)
        ViewTreeRenderer.disposeMounted(container, mounted.mountedNodes)
        assertEquals(1, releases)
    }

    @Test
    fun `failed cross owner rebind releases tree without invoking disposed owner update`() {
        val oldContainer = FrameLayout(context)
        val newContainer = FrameLayout(context)
        var oldUpdates = 0
        var releases = 0
        val mounted = ViewTreeRenderer.renderInto(
            container = oldContainer,
            previous = emptyList(),
            nodes = listOf(
                VNode(
                    type = NodeType.AndroidView,
                    key = "old",
                    spec = AndroidViewNodeProps(
                        factory = { rawContext, _ -> View(rawContext as Context) },
                        update = { _, _ -> oldUpdates += 1 },
                        onReset = { _, _ -> },
                        onRelease = { releases += 1 },
                    ),
                ),
            ),
        )
        assertEquals(1, oldUpdates)
        assertTrue(ViewTreeRenderer.detachMountedForReuse(oldContainer, mounted.mountedNodes))
        ViewTreeRenderer.attachReusableMounted(newContainer, mounted.mountedNodes)

        val error = runCatching {
            ViewTreeRenderer.renderInto(
                container = newContainer,
                previous = mounted.mountedNodes,
                nodes = listOf(
                    androidNode(
                        key = "new",
                        value = "new",
                        failUpdate = true,
                        onReset = {},
                    ),
                ),
            )
        }.exceptionOrNull()

        assertAndroidViewUpdateFailure(error)
        assertEquals(1, oldUpdates)
        assertEquals(1, releases)
        assertEquals(0, newContainer.childCount)
        assertTrue(mounted.mountedNodes.single().disposed)
    }

    @Test
    fun `observed property patch updates exact text target without reconciliation`() {
        val container = FrameLayout(context)
        val previousNode = environmentTextNode(
            density = 1f,
            fontScale = 1f,
            text = "before",
        ).copy(observedPropertyId = 1L)
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(previousNode),
        )
        val mounted = initial.mountedNodes.single()
        val textView = mounted.view as TextView
        val nextNode = previousNode.copy(
            spec = (previousNode.spec as TextNodeProps).copy(
                document = TextDocument.plain("after"),
            ),
        )

        val result = ViewTreeRenderer.patchObservedProperties(
            patches = listOf(
                ViewTreeObservedPropertyPatch(
                    id = 1L,
                    mountedNode = mounted,
                    previous = previousNode,
                    next = nextNode,
                ),
            ),
        )

        assertSame(textView, mounted.view)
        assertEquals("after", textView.text.toString())
        assertSame(nextNode, mounted.vnode)
        assertEquals(1, result.stats.patchedNodes)
        assertEquals(0, result.stats.inserts)
        assertEquals(0, result.stats.removals)
    }

    @Test
    fun `observed property patch rejects construction identity changes before mutation`() {
        val container = FrameLayout(context)
        val previous = androidNode(
            key = "interop",
            value = "old",
            constructionIdentity = "style-a",
        ).copy(observedPropertyId = 7L)
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(previous),
        )
        val mounted = initial.mountedNodes.single()
        val next = androidNode(
            key = "interop",
            value = "new",
            constructionIdentity = "style-b",
        ).copy(observedPropertyId = 7L)

        val error = runCatching {
            ViewTreeRenderer.patchObservedProperties(
                patches = listOf(
                    ViewTreeObservedPropertyPatch(7L, mounted, previous, next),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("cannot change AndroidView construction identity"))
        assertSame(previous, mounted.vnode)
        assertEquals("old", mounted.view.tag)
        assertSame(mounted.view, container.getChildAt(0))
    }

    @Test
    fun `small observed property batch rejects duplicate targets before mutation`() {
        val container = FrameLayout(context)
        val previous = environmentTextNode(
            density = 1f,
            fontScale = 1f,
            text = "before",
        ).copy(observedPropertyId = 1L)
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(previous),
        )
        val mounted = initial.mountedNodes.single()
        val firstNext = previous.copy(
            spec = (previous.spec as TextNodeProps).copy(
                document = TextDocument.plain("first"),
            ),
        )
        val secondNext = previous.copy(
            spec = (previous.spec as TextNodeProps).copy(
                document = TextDocument.plain("second"),
            ),
        )

        val error = runCatching {
            ViewTreeRenderer.patchObservedProperties(
                patches = listOf(
                    ViewTreeObservedPropertyPatch(1L, mounted, previous, firstNext),
                    ViewTreeObservedPropertyPatch(2L, mounted, previous, secondNext),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("targeted only once"))
        assertSame(previous, mounted.vnode)
        assertEquals("before", (mounted.view as TextView).text.toString())
    }

    @Test
    fun `large observed property batch rejects duplicate ids before mutation`() {
        val container = FrameLayout(context)
        val previousNodes = (1L..9L).map { id ->
            environmentTextNode(
                density = 1f,
                fontScale = 1f,
                text = "before-$id",
            ).copy(observedPropertyId = id)
        }
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = previousNodes,
        )
        val patches = previousNodes.mapIndexed { index, previous ->
            val id = if (index == previousNodes.lastIndex) 1L else index + 1L
            ViewTreeObservedPropertyPatch(
                id = id,
                mountedNode = initial.mountedNodes[index],
                previous = previous,
                next = previous.copy(
                    spec = (previous.spec as TextNodeProps).copy(
                        document = TextDocument.plain("after-${index + 1}"),
                    ),
                ),
            )
        }

        val error = runCatching {
            ViewTreeRenderer.patchObservedProperties(patches = patches)
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("ids must be unique"))
        previousNodes.forEachIndexed { index, previous ->
            val mounted = initial.mountedNodes[index]
            assertSame(previous, mounted.vnode)
            assertEquals("before-${index + 1}", (mounted.view as TextView).text.toString())
        }
    }

    @Test
    fun `observed property rollback restores native targets without invoking reuse reset`() {
        val container = FrameLayout(context)
        val lifecycle = mutableListOf<String>()
        val previousNodes = listOf(
            androidNode(
                key = "first",
                value = "old-first",
                onReset = { lifecycle += "reset-old-first" },
            ).copy(observedPropertyId = 1L),
            androidNode(
                key = "second",
                value = "old-second",
                onReset = { lifecycle += "reset-old-second" },
            ).copy(observedPropertyId = 2L),
        )
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = previousNodes,
        )
        val firstMounted = initial.mountedNodes[0]
        val secondMounted = initial.mountedNodes[1]
        val firstNext = androidNode(
            key = "first",
            value = "new-first",
            onReset = { lifecycle += "reset-new-first" },
            onCommit = { lifecycle += "commit-new-first" },
        )
            .copy(observedPropertyId = 1L)
        val secondNext = androidNode(
            key = "second",
            value = "new-second",
            failUpdate = true,
            onReset = { lifecycle += "reset-new-second" },
            onCommit = { lifecycle += "commit-new-second" },
        )
            .copy(observedPropertyId = 2L)

        val error = runCatching {
            ViewTreeRenderer.patchObservedProperties(
                patches = listOf(
                    ViewTreeObservedPropertyPatch(1L, firstMounted, previousNodes[0], firstNext),
                    ViewTreeObservedPropertyPatch(2L, secondMounted, previousNodes[1], secondNext),
                ),
            )
        }.exceptionOrNull()

        assertAndroidViewUpdateFailure(error)
        assertEquals("old-first", firstMounted.view.tag)
        assertEquals("old-second", secondMounted.view.tag)
        assertSame(previousNodes[0], firstMounted.vnode)
        assertSame(previousNodes[1], secondMounted.vnode)
        assertTrue(lifecycle.isEmpty())
    }

    @Test
    fun `observed property batch failure restores earlier native no-op snapshot`() {
        val container = FrameLayout(context)
        val previousText = environmentTextNode(
            density = 1f,
            fontScale = 1f,
            text = "stable",
        ).copy(observedPropertyId = 1L)
        val previousFailing = androidNode(
            key = "failing",
            value = "old",
        ).copy(observedPropertyId = 2L)
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(previousText, previousFailing),
        )
        val textMounted = initial.mountedNodes[0]
        val failingMounted = initial.mountedNodes[1]
        val equalTextCandidate = previousText.copy()
        val failingCandidate = androidNode(
            key = "failing",
            value = "new",
            failUpdate = true,
        ).copy(observedPropertyId = 2L)

        val error = runCatching {
            ViewTreeRenderer.patchObservedProperties(
                patches = listOf(
                    ViewTreeObservedPropertyPatch(
                        1L,
                        textMounted,
                        previousText,
                        equalTextCandidate,
                    ),
                    ViewTreeObservedPropertyPatch(
                        2L,
                        failingMounted,
                        previousFailing,
                        failingCandidate,
                    ),
                ),
            )
        }.exceptionOrNull()

        assertAndroidViewUpdateFailure(error)
        assertSame(previousText, textMounted.vnode)
        assertSame(previousFailing, failingMounted.vnode)
        assertEquals("stable", (textMounted.view as TextView).text.toString())
        assertEquals("old", failingMounted.view.tag)
    }

    private fun androidNode(
        key: Any,
        value: String,
        failUpdate: Boolean = false,
        onReset: (() -> Unit)? = null,
        onRelease: (() -> Unit)? = null,
        onCommit: (() -> Unit)? = null,
        constructionIdentity: Any? = Unit,
        adapterName: String = "callback",
        lifecycleMode: String = "None",
    ): VNode {
        return VNode(
            type = NodeType.AndroidView,
            key = key,
            spec = AndroidViewNodeProps(
                factory = { rawContext, _ ->
                    View(rawContext as Context)
                },
                update = { rawView, _ ->
                    if (failUpdate) {
                        error("update failed")
                    }
                    (rawView as View).tag = value
                },
                onReset = if (onReset == null) {
                    null
                } else {
                    { _, _ -> onReset() }
                },
                onRelease = if (onRelease == null) {
                    null
                } else {
                    { onRelease() }
                },
                onCommit = if (onCommit == null) {
                    null
                } else {
                    { _, _ -> onCommit() }
                },
                constructionIdentity = constructionIdentity,
                adapterName = adapterName,
                lifecycleMode = lifecycleMode,
            ),
        )
    }

    private fun assertAndroidViewUpdateFailure(error: Throwable?) {
        assertTrue(error is AndroidViewOperationException)
        assertEquals(
            AndroidViewOperation.Update,
            (error as AndroidViewOperationException).operation,
        )
        assertTrue(error.cause is IllegalStateException)
    }

    private fun columnNode(vararg children: VNode): VNode {
        return VNode(
            type = NodeType.Column,
            key = "column",
            spec = ColumnNodeProps(
                spacing = 0.dp,
                arrangement = MainAxisArrangement.Start,
                horizontalAlignment = HorizontalAlignment.Start,
            ),
            children = children.toList(),
        )
    }

    private fun environmentTextNode(
        density: Float,
        fontScale: Float,
        text: String = "Environment",
        maxLines: Int = 1,
        textSizeSp: Int = 10,
    ): VNode {
        return VNode(
            type = NodeType.Text,
            key = "environment-text",
            spec = TextNodeProps(
                document = TextDocument.plain(text),
                maxLines = maxLines,
                overflow = com.viewcompose.ui.node.TextOverflow.Clip,
                textAlign = com.viewcompose.ui.node.TextAlign.Start,
                textColor = 0xFF000000.toInt(),
                textSizeSp = textSizeSp.sp,
            ),
            environment = UiEnvironmentValues.Default.copy(
                density = UiDensity(
                    density = density,
                    fontScale = fontScale,
                ),
            ),
        )
    }

    private fun animatedBoundsTextNode(
        width: Int,
        timing: ContentSizeTweenSpecModel,
    ): VNode {
        return environmentTextNode(density = 1f, fontScale = 1f).copy(
            key = "animated-bounds-text",
            modifier = Modifier
                .width(width.dp)
                .then(AnimateBoundsModifierElement(timing)),
        )
    }

    private fun FrameLayout.measureAndLayoutForBoundsReuse() {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
        measure(widthSpec, heightSpec)
        layout(0, 0, 400, 400)
    }

    private fun relativeTextNode(
        layoutDirection: UiLayoutDirection,
        modifier: Modifier,
    ): VNode {
        return environmentTextNode(
            density = 1f,
            fontScale = 1f,
        ).copy(
            modifier = modifier,
            environment = UiEnvironmentValues.Default.copy(
                density = UiDensity(density = 1f, fontScale = 1f),
                layoutDirection = layoutDirection,
            ),
        )
    }

    private fun lazyCollectionEnvironmentNode(
        nodeType: NodeType = NodeType.LazyColumn,
        resourceRevision: Long,
        layoutDirection: UiLayoutDirection = UiLayoutDirection.Ltr,
    ): VNode {
        val contentPadding = LazyContentPadding(
            start = 3.dp,
            top = 5.dp,
            end = 7.dp,
            bottom = 11.dp,
        )
        return VNode(
            type = nodeType,
            key = "environment-$nodeType",
            modifier = Modifier
                .systemBarsInsetsPadding()
                .padding(horizontal = 16.dp),
            spec = when (nodeType) {
                NodeType.LazyColumn -> LazyColumnNodeProps(
                    contentPadding = contentPadding,
                    spacing = 0.dp,
                    items = emptyList<LazyListItem>().asLazyItemTable(),
                )
                NodeType.LazyVerticalGrid -> LazyVerticalGridNodeProps(
                    cells = com.viewcompose.ui.node.policy.GridCells.Fixed(2),
                    contentPadding = contentPadding,
                    horizontalSpacing = 0.dp,
                    verticalSpacing = 0.dp,
                    items = emptyList<LazyListItem>().asLazyItemTable(),
                    state = null,
                )
                else -> error("Unsupported lazy collection node type: $nodeType")
            },
            environment = UiEnvironmentValues.Default.copy(
                density = UiDensity(density = 1f, fontScale = 1f),
                layoutDirection = layoutDirection,
                resourceRevision = resourceRevision,
            ),
        )
    }

    private fun lazySessionNode(
        label: String,
        events: MutableList<String>,
    ): VNode {
        return VNode(
            type = NodeType.LazyColumn,
            key = "lazy-session",
            spec = LazyColumnNodeProps(
                contentPadding = LazyContentPadding(),
                spacing = 0.dp,
                items = listOf(
                    LazyListItem(
                        key = "item",
                        contentRevision = label,
                        sessionStrategy = lazyListItemSessionStrategy(
                            create = { TransactionRecordingSession(events) },
                            update = { session ->
                                (session as TransactionRecordingSession).label = label
                                events += "update:$label"
                            },
                        ),
                    ),
                ).asLazyItemTable(),
            ),
        )
    }

    private class TransactionRecordingSession(
        private val events: MutableList<String>,
    ) : LazyListItemSession {
        var label: String = ""

        override fun render(): Boolean {
            events += "render:$label"
            return true
        }

        override fun dispose() = Unit
    }

    private fun textFieldNode(
        state: TextFieldState,
        modifier: Modifier = Modifier,
        nodeShape: UiShape = UiShape.rounded(0.dp),
        backgroundColor: Int = 0,
    ): VNode {
        return VNode(
            type = NodeType.TextField,
            key = "text-field",
            modifier = modifier,
            spec = TextFieldNodeProps(
                state = state,
                value = state.value,
                placeholder = "",
                enabled = true,
                singleLine = true,
                minLines = 1,
                maxLines = 1,
                keyboardOptions = TextFieldKeyboardOptions(),
                inputTransformation = null,
                onKeyboardAction = null,
                onFocusChange = null,
                autofillHints = emptySet(),
                hintColor = 0,
                readOnly = false,
                textColor = 0xFF000000.toInt(),
                textSizeSp = 16.sp,
                backgroundColor = backgroundColor,
                borderWidth = 0.dp,
                borderColor = 0,
                shape = nodeShape,
                minHeight = 0.dp,
                paddingHorizontal = 0.dp,
                paddingVertical = 0.dp,
            ),
        )
    }
}
