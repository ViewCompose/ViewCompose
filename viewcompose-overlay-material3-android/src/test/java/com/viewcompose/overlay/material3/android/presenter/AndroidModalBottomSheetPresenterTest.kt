package com.viewcompose.overlay.material3.android.presenter

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.viewcompose.host.android.renderInto
import com.viewcompose.ui.foundation.ModalBottomSheetAppearance
import com.viewcompose.ui.foundation.ModalBottomSheetNavigationBarColor
import com.viewcompose.ui.foundation.ModalBottomSheetOverlayContent
import com.viewcompose.ui.foundation.ModalBottomSheetOverlaySpec
import com.viewcompose.ui.foundation.OverlayEntryId
import com.viewcompose.ui.foundation.OverlaySessionId
import com.viewcompose.ui.foundation.OverlaySurfaceContent
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidModalBottomSheetPresenterTest {
    @Test
    fun `same key update reapplies appearance and restores reversible platform policy`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        renderInto(root) {}.dispose()
        val presenter = AndroidModalBottomSheetPresenter(root)
        val initial = request(
            containerColor = 0xFF123456.toInt(),
            scrimOpacity = 0.62f,
            navigationBarColor = ModalBottomSheetNavigationBarColor.Exact(0xFF223344.toInt()),
            skipPartiallyExpanded = true,
        )

        val handle = presenter.show(entryId, initial.first, initial.second)
        val dialog = handle.privateField<BottomSheetDialog>("dialog")
        val defaultNavigationBarColor = handle.privateField<Int?>("defaultNavigationBarColor")
        val sheet = requireNotNull(
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet),
        )
        assertEquals(0xFF123456.toInt(), sheet.background.solidColor())
        assertEquals(0.62f, dialog.window!!.attributes.dimAmount)
        assertEquals(0xFF223344.toInt(), dialog.window!!.navigationBarColor)
        assertFalse(dialog.window!!.isNavigationBarContrastEnforced)
        assertTrue(dialog.behavior.skipCollapsed)

        val updated = request(
            containerColor = 0xFFABCDEF.toInt(),
            scrimOpacity = 0.18f,
            navigationBarColor = ModalBottomSheetNavigationBarColor.PlatformDefault,
            skipPartiallyExpanded = false,
        )
        handle.update(updated.first, updated.second)

        assertEquals(0xFFABCDEF.toInt(), sheet.background.solidColor())
        assertEquals(0.18f, dialog.window!!.attributes.dimAmount)
        assertEquals(defaultNavigationBarColor, dialog.window!!.navigationBarColor)
        assertTrue(dialog.window!!.isNavigationBarContrastEnforced)
        assertFalse(dialog.behavior.skipCollapsed)

        handle.dismiss()
        assertFalse(dialog.isShowing)
    }

    private fun request(
        containerColor: Int,
        scrimOpacity: Float,
        navigationBarColor: ModalBottomSheetNavigationBarColor,
        skipPartiallyExpanded: Boolean,
    ): Pair<ModalBottomSheetOverlaySpec, ModalBottomSheetOverlayContent> {
        val spec = ModalBottomSheetOverlaySpec(
            skipPartiallyExpanded = skipPartiallyExpanded,
            appearance = ModalBottomSheetAppearance(
                containerColor = containerColor,
                contentColor = 0xFF101010.toInt(),
                shape = UiShape.rounded(21.dp),
                scrimOpacity = scrimOpacity,
                navigationBarColor = navigationBarColor,
            ),
        )
        return spec to ModalBottomSheetOverlayContent(captureSurfaceContent())
    }

    private fun captureSurfaceContent(): OverlaySurfaceContent {
        val owner = Class.forName(
            "com.viewcompose.ui.foundation.OverlaySurfaceSessionKt",
        )
        val capture = owner.declaredMethods.single {
            it.name.startsWith("captureOverlaySurfaceContent") && it.parameterCount == 3
        }
        capture.isAccessible = true
        val content: UiTreeBuilder.() -> Unit = { Text("Content") }
        return capture.invoke(null, null, null, content) as OverlaySurfaceContent
    }

    private fun android.graphics.drawable.Drawable.solidColor(): Int {
        val getter = javaClass.declaredMethods.single {
            it.name.startsWith("getCurrentFillColor") && it.parameterCount == 0
        }
        getter.isAccessible = true
        return getter.invoke(this) as Int
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Any.privateField(name: String): T {
        return javaClass.getDeclaredField(name).run {
            isAccessible = true
            get(this@privateField) as T
        }
    }

    private companion object {
        val entryId = OverlayEntryId(OverlaySessionId("test"), "sheet")
    }
}
