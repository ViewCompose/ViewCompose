package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.unit.sp

import com.viewcompose.ui.unit.dp

/*
 * 测试职责：覆盖 renderer view/tree 中的 View Tree Render Transaction 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers View Tree Render Transaction behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import android.content.Context
import android.graphics.RectF
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.material.shape.CutCornerTreatment
import com.viewcompose.renderer.decoration.AndroidViewDecorationRuntime
import com.viewcompose.renderer.decoration.RecordingDecorationBackend
import com.viewcompose.text.TextFieldState
import com.viewcompose.text.TextFieldValue
import com.viewcompose.text.TextRange
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.innerShadow
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.TextFieldKeyboardOptions
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.ui.node.spec.AndroidViewOperationException
import com.viewcompose.ui.node.spec.ColumnNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.node.spec.TextFieldNodeProps
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDensity
import com.google.android.material.shape.MaterialShapeDrawable
import org.junit.Assert.assertEquals
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

        val drawable = next.single().view.background as MaterialShapeDrawable
        assertTrue(drawable.shapeAppearanceModel.topLeftCorner is CutCornerTreatment)
        assertEquals(
            14f,
            drawable.shapeAppearanceModel.topLeftCornerSize.getCornerSize(
                RectF(0f, 0f, 100f, 100f),
            ),
            0.001f,
        )
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
        assertEquals("vaXlue", editText.text.toString())
        assertEquals(3, editText.selectionStart)
        assertEquals(
            6.dp,
            requireNotNull(decorationBackend.requestOrNull(editText))
                .innerShadows.single().shadows.single().blurRadius,
        )
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

    private fun androidNode(
        key: Any,
        value: String,
        failUpdate: Boolean = false,
        onRelease: (() -> Unit)? = null,
        onCommit: (() -> Unit)? = null,
    ): VNode {
        return VNode(
            type = NodeType.AndroidView,
            key = key,
            spec = AndroidViewNodeProps(
                factory = { rawContext ->
                    View(rawContext as Context)
                },
                update = { rawView ->
                    if (failUpdate) {
                        error("update failed")
                    }
                    (rawView as View).tag = value
                },
                onRelease = if (onRelease == null) {
                    null
                } else {
                    { onRelease() }
                },
                onCommit = if (onCommit == null) {
                    null
                } else {
                    { onCommit() }
                },
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
    ): VNode {
        return VNode(
            type = NodeType.Text,
            key = "environment-text",
            spec = TextNodeProps(
                text = "Environment",
                maxLines = 1,
                overflow = com.viewcompose.ui.node.TextOverflow.Clip,
                textAlign = com.viewcompose.ui.node.TextAlign.Start,
                textColor = 0xFF000000.toInt(),
                textSizeSp = 10.sp,
            ),
            environment = UiEnvironmentValues.Default.copy(
                density = UiDensity(
                    density = density,
                    fontScale = fontScale,
                ),
            ),
        )
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
