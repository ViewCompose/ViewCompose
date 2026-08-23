package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.unit.sp

import com.viewcompose.ui.unit.dp

/*
 * 测试职责：覆盖 renderer view/tree 中的 Modifier Interaction Applier 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Modifier Interaction Applier behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import android.view.View
import com.viewcompose.graphics.core.Brush
import com.viewcompose.renderer.R
import com.viewcompose.renderer.decoration.AndroidViewDecorationRuntime
import com.viewcompose.renderer.decoration.DecorationChildDrawingOrder
import com.viewcompose.renderer.decoration.RecordingDecorationBackend
import com.viewcompose.renderer.modifier.resolve
import com.viewcompose.renderer.view.shape.UiShapeDrawable
import com.viewcompose.renderer.view.shape.UiShapeOutlineProvider
import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.dropShadow
import com.viewcompose.ui.modifier.elevation
import com.viewcompose.ui.modifier.innerShadow
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.zIndex
import com.viewcompose.ui.modifier.sharedElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import com.viewcompose.ui.node.spec.SurfaceNodeProps
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.shared.SHARED_CONTENT_TAG_KEY
import com.viewcompose.ui.shared.SharedContentKey
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ModifierInteractionApplierTest {
    private lateinit var decorationBackend: RecordingDecorationBackend

    @Before
    fun installDecorationBackend() {
        decorationBackend = RecordingDecorationBackend()
        AndroidViewDecorationRuntime.install(decorationBackend)
    }

    @Test
    fun `shared content metadata updates and clears on a reused view`() {
        val view = View(RuntimeEnvironment.getApplication())
        val key = SharedContentKey("hero")

        ViewModifierApplier.applyModifier(
            view,
            vnode(Modifier.sharedElement(key)),
            defaultRippleColor = 0,
        )

        assertEquals(
            key,
            (view.getTag(SHARED_CONTENT_TAG_KEY)
                as com.viewcompose.ui.modifier.SharedContentModifierElement).key,
        )

        ViewModifierApplier.applyModifier(view, vnode(Modifier), defaultRippleColor = 0)

        assertNull(view.getTag(SHARED_CONTENT_TAG_KEY))
    }

    @After
    fun resetDecorationBackend() {
        AndroidViewDecorationRuntime.resetForTests()
    }

    @Test
    fun `zIndex uses decoration order without changing platform shadow height`() {
        val view = View(RuntimeEnvironment.getApplication())
        val node = vnode(
            Modifier
                .elevation(6.dp)
                .zIndex(4f),
        )

        ViewModifierApplier.applyModifier(view, node, defaultRippleColor = 0)

        assertEquals(4f, DecorationChildDrawingOrder.zIndex(view))
        assertEquals(0f, view.translationZ)
        assertEquals(6f, view.elevation)

        ViewModifierApplier.applyModifier(view, vnode(Modifier), defaultRippleColor = 0)

        assertEquals(0f, DecorationChildDrawingOrder.zIndex(view))
        assertEquals(0f, view.translationZ)
        assertEquals(0f, view.elevation)
    }

    @Test
    fun `incremental modifier application still applies changed layout domain`() {
        val view = View(RuntimeEnvironment.getApplication())
        val firstNode = vnode(Modifier.padding(4.dp))
        val secondNode = vnode(Modifier.padding(12.dp))

        ViewModifierApplier.applyModifier(view, firstNode, defaultRippleColor = 0)
        ViewModifierApplier.applyModifier(view, secondNode, defaultRippleColor = 0)

        assertEquals(12, view.paddingLeft)
        assertEquals(12, view.paddingTop)
        assertEquals(12, view.paddingRight)
        assertEquals(12, view.paddingBottom)
    }

    @Test
    fun `callback-only modifier update does not rebuild the native surface`() {
        val view = View(RuntimeEnvironment.getApplication())
        var latestCalls = 0
        val firstNode = vnode(
            Modifier
                .backgroundColor(0xFF112233.toInt())
                .clickable {},
        )
        val secondNode = vnode(
            Modifier
                .backgroundColor(0xFF112233.toInt())
                .clickable { latestCalls += 1 },
        )

        ViewModifierApplier.applyModifier(view, firstNode, defaultRippleColor = 0)
        val firstBackground = view.background
        ViewModifierApplier.applyModifier(view, secondNode, defaultRippleColor = 0)

        assertSame(firstBackground, view.background)
        view.performClick()
        assertEquals(1, latestCalls)

        val changedSurfaceNode = vnode(
            Modifier
                .backgroundColor(0xFF445566.toInt())
                .clickable { latestCalls += 1 },
        )
        ViewModifierApplier.applyModifier(view, changedSurfaceNode, defaultRippleColor = 0)

        assertNotSame(firstBackground, view.background)
    }

    @Test
    fun `surface fill and clip changes rebuild the native surface`() {
        val view = View(RuntimeEnvironment.getApplication())
        val firstNode = surfaceNode(fillColor = 0xFF112233.toInt(), clipContent = true)
        val secondNode = surfaceNode(fillColor = 0xFF445566.toInt(), clipContent = false)

        ViewModifierApplier.applyModifier(view, firstNode, defaultRippleColor = 0)
        val firstBackground = view.background as UiShapeDrawable
        assertEquals(0xFF112233.toInt(), firstBackground.currentFillColor)
        assertTrue(view.clipToOutline)
        assertTrue(view.outlineProvider is UiShapeOutlineProvider)

        ViewModifierApplier.applyModifier(view, secondNode, defaultRippleColor = 0)
        val secondBackground = view.background as UiShapeDrawable

        assertNotSame(firstBackground, secondBackground)
        assertEquals(0xFF445566.toInt(), secondBackground.currentFillColor)
        assertFalse(view.clipToOutline)
    }

    @Test
    fun `clickable modifier reuses listener while refreshing callback`() {
        val view = View(RuntimeEnvironment.getApplication())
        var firstCalls = 0
        var secondCalls = 0
        val firstNode = vnode(Modifier.clickable { firstCalls += 1 })
        val secondNode = vnode(Modifier.clickable { secondCalls += 1 })

        ModifierInteractionApplier.applyClickAndFocusState(
            view = view,
            node = firstNode,
            resolved = firstNode.modifier.resolve(),
        )
        val firstBinding = view.getTag(R.id.viewcompose_modifier_click_listener)

        ModifierInteractionApplier.applyClickAndFocusState(
            view = view,
            node = secondNode,
            resolved = secondNode.modifier.resolve(),
        )
        val secondBinding = view.getTag(R.id.viewcompose_modifier_click_listener)
        view.performClick()

        assertSame(firstBinding, secondBinding)
        assertEquals(0, firstCalls)
        assertEquals(1, secondCalls)
    }

    @Test
    fun `drop shadow follows resolved node shape and is removed incrementally`() {
        val view = View(RuntimeEnvironment.getApplication())
        val shadowNode = vnode(
            Modifier
                .cornerRadius(6.dp)
                .dropShadow(
                    UiShadow(
                        color = 0x33000000,
                        blurRadius = 8.dp,
                        offsetY = 2.dp,
                    ),
                ),
        )

        ViewModifierApplier.applyModifier(view, shadowNode, defaultRippleColor = 0)

        val installed = requireNotNull(decorationBackend.requestOrNull(view))
        assertEquals(1, installed.dropShadows.size)
        assertEquals(6.dp, installed.defaultShape?.uniformAbsoluteSizeOrNull)

        ViewModifierApplier.applyModifier(view, vnode(Modifier), defaultRippleColor = 0)

        assertNull(decorationBackend.requestOrNull(view))
    }

    @Test
    fun `inner shadow follows shape without rebuilding ripple or click binding`() {
        val view = View(RuntimeEnvironment.getApplication())
        var clicks = 0
        val onClick = { clicks += 1 }
        val baseNode = vnode(
            Modifier
                .backgroundColor(0xFF112233.toInt())
                .cornerRadius(6.dp)
                .clickable(onClick),
        )
        val shadowNode = vnode(
            Modifier
                .backgroundColor(0xFF112233.toInt())
                .cornerRadius(6.dp)
                .innerShadow(
                    UiShadow(
                        color = 0x33000000,
                        blurRadius = 8.dp,
                        offsetY = 2.dp,
                    ),
                )
                .clickable(onClick),
        )

        ViewModifierApplier.applyModifier(view, baseNode, defaultRippleColor = 0x22000000)
        val initialBackground = view.background
        val initialClickBinding = view.getTag(R.id.viewcompose_modifier_click_listener)
        ViewModifierApplier.applyModifier(view, shadowNode, defaultRippleColor = 0x22000000)

        val installed = requireNotNull(decorationBackend.requestOrNull(view))
        assertEquals(1, installed.innerShadows.size)
        assertEquals(6.dp, installed.defaultShape?.uniformAbsoluteSizeOrNull)
        assertSame(initialBackground, view.background)
        assertSame(initialClickBinding, view.getTag(R.id.viewcompose_modifier_click_listener))
        view.performClick()
        assertEquals(1, clicks)

        ViewModifierApplier.applyModifier(view, vnode(Modifier), defaultRippleColor = 0)

        assertNull(decorationBackend.requestOrNull(view))
    }

    private fun vnode(modifier: Modifier): VNode {
        return VNode(
            type = NodeType.Box,
            spec = EmptyNodeSpec,
            modifier = modifier,
        )
    }

    private fun surfaceNode(
        fillColor: Int,
        clipContent: Boolean,
    ): VNode {
        return VNode(
            type = NodeType.Surface,
            spec = SurfaceNodeProps(
                contentAlignment = BoxAlignment.Center,
                fill = Brush.SolidColor(fillColor),
                shape = UiShape.cut(8.dp),
                clipContent = clipContent,
            ),
        )
    }
}
