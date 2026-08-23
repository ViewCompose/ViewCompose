package com.viewcompose.navigation

/*
 * Test responsibility: covers bounded shared-content pairing, fallback, endpoint restoration, and
 * committed focus transfer in the native navigation overlay.
 */

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import com.viewcompose.ui.modifier.SharedContentModifierElement
import com.viewcompose.ui.shared.SHARED_CONTENT_TAG_KEY
import com.viewcompose.ui.shared.SharedContentKey
import com.viewcompose.ui.shared.SharedContentMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class AndroidSharedTransitionOverlayTest {
    private val activityControllers = mutableListOf<ActivityController<Activity>>()

    @After
    fun tearDown() {
        activityControllers.asReversed().forEach { controller ->
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `unique element pair suppresses endpoints and restores them on cancellation`() {
        val fixture = fixture(
            sourceMode = SharedContentMode.Element,
            targetMode = SharedContentMode.Element,
        )
        val overlay = AndroidSharedTransitionOverlay(
            host = fixture.host,
            outgoingRoots = listOf(fixture.outgoingRoot),
            incomingRoots = listOf(fixture.incomingRoot),
        )

        fixture.host.viewTreeObserver.dispatchOnPreDraw()

        assertEquals(1, overlay.pairCount)
        assertEquals(View.INVISIBLE, fixture.source.visibility)
        assertEquals(0.7f, fixture.source.alpha)
        assertEquals(0f, fixture.target.alpha)

        overlay.update(0.5f)
        overlay.finish(committed = false)

        assertEquals(0.7f, fixture.source.alpha)
        assertEquals(View.VISIBLE, fixture.source.visibility)
        assertEquals(0.8f, fixture.target.alpha)
    }

    @Test
    fun `duplicate or mode-mismatched keys fall back without endpoint mutation`() {
        val duplicate = fixture(
            sourceMode = SharedContentMode.Element,
            targetMode = SharedContentMode.Element,
        )
        duplicate.outgoingRoot.addView(
            endpointView(
                root = duplicate.outgoingRoot,
                key = SharedContentKey("hero"),
                mode = SharedContentMode.Element,
                left = 140,
                top = 20,
            ),
        )
        layoutHost(duplicate.host)
        val duplicateOverlay = AndroidSharedTransitionOverlay(
            host = duplicate.host,
            outgoingRoots = listOf(duplicate.outgoingRoot),
            incomingRoots = listOf(duplicate.incomingRoot),
        )
        duplicate.host.viewTreeObserver.dispatchOnPreDraw()

        assertEquals(0, duplicateOverlay.pairCount)
        assertEquals(0.7f, duplicate.source.alpha)
        assertEquals(0.8f, duplicate.target.alpha)
        duplicateOverlay.finish(committed = false)

        val mismatch = fixture(
            sourceMode = SharedContentMode.Element,
            targetMode = SharedContentMode.Bounds,
        )
        val mismatchOverlay = AndroidSharedTransitionOverlay(
            host = mismatch.host,
            outgoingRoots = listOf(mismatch.outgoingRoot),
            incomingRoots = listOf(mismatch.incomingRoot),
        )
        mismatch.host.viewTreeObserver.dispatchOnPreDraw()

        assertEquals(0, mismatchOverlay.pairCount)
        assertEquals(0.7f, mismatch.source.alpha)
        assertEquals(0.8f, mismatch.target.alpha)
        mismatchOverlay.finish(committed = false)
    }

    @Test
    fun `missing and over-budget pairs fall back independently from a valid pair`() {
        val fixture = fixture(
            sourceMode = SharedContentMode.Bounds,
            targetMode = SharedContentMode.Bounds,
        )
        val missing = endpointView(
            root = fixture.outgoingRoot,
            key = SharedContentKey("missing"),
            mode = SharedContentMode.Bounds,
            left = 10,
            top = 200,
        )
        val oversizedSource = endpointView(
            root = fixture.outgoingRoot,
            key = SharedContentKey("oversized"),
            mode = SharedContentMode.Bounds,
            left = 0,
            top = 0,
            width = 1_500,
            height = 1_000,
        )
        val oversizedTarget = endpointView(
            root = fixture.incomingRoot,
            key = SharedContentKey("oversized"),
            mode = SharedContentMode.Bounds,
            left = 0,
            top = 0,
            width = 1_500,
            height = 1_000,
        )
        fixture.outgoingRoot.addView(missing)
        fixture.outgoingRoot.addView(oversizedSource)
        fixture.incomingRoot.addView(oversizedTarget)
        layoutHost(fixture.host)

        val overlay = AndroidSharedTransitionOverlay(
            host = fixture.host,
            outgoingRoots = listOf(fixture.outgoingRoot),
            incomingRoots = listOf(fixture.incomingRoot),
        )
        fixture.host.viewTreeObserver.dispatchOnPreDraw()

        assertEquals(1, overlay.pairCount)
        assertEquals(View.VISIBLE, missing.visibility)
        assertEquals(View.VISIBLE, oversizedSource.visibility)
        assertEquals(1f, oversizedTarget.alpha)
        overlay.finish(committed = false)
        assertEquals(0, overlay.pairCount)
    }

    @Test
    fun `finishing before pre-draw cancels preparation without endpoint mutation`() {
        val fixture = fixture(
            sourceMode = SharedContentMode.Element,
            targetMode = SharedContentMode.Element,
        )
        val overlay = AndroidSharedTransitionOverlay(
            host = fixture.host,
            outgoingRoots = listOf(fixture.outgoingRoot),
            incomingRoots = listOf(fixture.incomingRoot),
        )

        overlay.finish(committed = false)
        fixture.host.viewTreeObserver.dispatchOnPreDraw()

        assertEquals(0, overlay.pairCount)
        assertEquals(View.VISIBLE, fixture.source.visibility)
        assertEquals(0.7f, fixture.source.alpha)
        assertEquals(0.8f, fixture.target.alpha)
    }

    @Test
    fun `surface-backed subtree falls back and committed pair transfers focus`() {
        val unsupported = fixture(
            sourceMode = SharedContentMode.Bounds,
            targetMode = SharedContentMode.Bounds,
        )
        (unsupported.source as FrameLayout).addView(SurfaceView(unsupported.host.context))
        val unsupportedOverlay = AndroidSharedTransitionOverlay(
            host = unsupported.host,
            outgoingRoots = listOf(unsupported.outgoingRoot),
            incomingRoots = listOf(unsupported.incomingRoot),
        )
        unsupported.host.viewTreeObserver.dispatchOnPreDraw()

        assertEquals(0, unsupportedOverlay.pairCount)
        unsupportedOverlay.finish(committed = false)

        val focus = fixture(
            sourceMode = SharedContentMode.Bounds,
            targetMode = SharedContentMode.Bounds,
        )
        focus.source.isFocusable = true
        focus.source.isFocusableInTouchMode = true
        focus.target.isFocusable = true
        focus.target.isFocusableInTouchMode = true
        assertTrue(focus.source.requestFocus())
        val focusOverlay = AndroidSharedTransitionOverlay(
            host = focus.host,
            outgoingRoots = listOf(focus.outgoingRoot),
            incomingRoots = listOf(focus.incomingRoot),
        )
        focus.host.viewTreeObserver.dispatchOnPreDraw()
        focusOverlay.update(1f)
        focusOverlay.finish(committed = true)

        assertTrue(focus.target.isFocused)
        assertFalse(focus.source.isFocused)
    }

    private fun fixture(
        sourceMode: SharedContentMode,
        targetMode: SharedContentMode,
    ): Fixture {
        val activity = Robolectric.buildActivity(Activity::class.java)
            .setup()
            .visible()
        activityControllers += activity
        val host = NavHostView(activity.get())
        activity.get().setContentView(host)
        val outgoingRoot = FrameLayout(host.context)
        val incomingRoot = FrameLayout(host.context)
        host.addView(outgoingRoot)
        host.addView(incomingRoot)
        val key = SharedContentKey("hero")
        val source = endpointView(
            root = outgoingRoot,
            key = key,
            mode = sourceMode,
            left = 20,
            top = 30,
        ).apply { alpha = 0.7f }
        val target = endpointView(
            root = incomingRoot,
            key = key,
            mode = targetMode,
            left = 300,
            top = 400,
            width = 200,
            height = 160,
        ).apply { alpha = 0.8f }
        outgoingRoot.addView(source)
        incomingRoot.addView(target)
        layoutHost(host)
        return Fixture(host, outgoingRoot, incomingRoot, source, target)
    }

    private fun endpointView(
        root: FrameLayout,
        key: SharedContentKey,
        mode: SharedContentMode,
        left: Int,
        top: Int,
        width: Int = 100,
        height: Int = 80,
    ): View {
        return FrameLayout(root.context).apply {
            background = ColorDrawable(Color.RED)
            setTag(
                SHARED_CONTENT_TAG_KEY,
                SharedContentModifierElement(key, mode),
            )
            layoutParams = FrameLayout.LayoutParams(width, height).apply {
                leftMargin = left
                topMargin = top
            }
        }
    }

    private fun layoutHost(host: NavHostView) {
        val exactSize = View.MeasureSpec.makeMeasureSpec(1_000, View.MeasureSpec.EXACTLY)
        host.measure(exactSize, exactSize)
        host.layout(0, 0, 1_000, 1_000)
        assertTrue(host.isAttachedToWindow)
    }

    private data class Fixture(
        val host: NavHostView,
        val outgoingRoot: FrameLayout,
        val incomingRoot: FrameLayout,
        val source: View,
        val target: View,
    )
}
