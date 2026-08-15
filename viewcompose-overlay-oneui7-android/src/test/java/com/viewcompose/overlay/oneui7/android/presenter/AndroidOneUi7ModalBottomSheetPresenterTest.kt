package com.viewcompose.overlay.oneui7.android.presenter

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.viewcompose.host.android.renderInto
import com.viewcompose.overlay.oneui7.android.OneUi7OverlayStyle
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
class AndroidOneUi7ModalBottomSheetPresenterTest {
    @Test
    fun `same key update reapplies chrome and clears obsolete dim policy`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        renderInto(root) {}.dispose()
        val presenter = AndroidOneUi7ModalBottomSheetPresenter(root, style)
        val initial = request(
            containerColor = 0xFF345678.toInt(),
            scrimOpacity = 0.54f,
            navigationBarColor = ModalBottomSheetNavigationBarColor.Exact(0xFF456789.toInt()),
        )

        val handle = presenter.show(entryId, initial.first, initial.second)
        val dialog = handle.privateField<Dialog>("dialog")
        val defaultNavigationBarColor = handle.privateField<Int?>("defaultNavigationBarColor")
        val sheet = handle.privateField<View>("sheet")
        assertEquals(0xFF345678.toInt(), sheet.background.solidColor())
        assertEquals(0.54f, dialog.window!!.attributes.dimAmount)
        assertTrue(dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
        assertEquals(0xFF456789.toInt(), dialog.window!!.navigationBarColor)
        assertFalse(dialog.window!!.isNavigationBarContrastEnforced)

        val updated = request(
            containerColor = 0xFFB0C0D0.toInt(),
            scrimOpacity = 0f,
            navigationBarColor = ModalBottomSheetNavigationBarColor.PlatformDefault,
        )
        handle.update(updated.first, updated.second)

        assertEquals(0xFFB0C0D0.toInt(), sheet.background.solidColor())
        assertTrue(dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND == 0)
        assertEquals(defaultNavigationBarColor, dialog.window!!.navigationBarColor)
        assertTrue(dialog.window!!.isNavigationBarContrastEnforced)

        handle.dismiss()
        assertFalse(dialog.isShowing)
    }

    private fun request(
        containerColor: Int,
        scrimOpacity: Float,
        navigationBarColor: ModalBottomSheetNavigationBarColor,
    ): Pair<ModalBottomSheetOverlaySpec, ModalBottomSheetOverlayContent> {
        val spec = ModalBottomSheetOverlaySpec(
            appearance = ModalBottomSheetAppearance(
                containerColor = containerColor,
                contentColor = 0xFF101010.toInt(),
                shape = UiShape.rounded(24.dp),
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
        val style = OneUi7OverlayStyle(
            surfaceColor = 0,
            contentColor = 0,
            secondaryContentColor = 0,
            snackbarColor = 0,
            snackbarContentColor = 0,
            actionColor = 0,
            outlineColor = 0,
            cornerRadiusDp = 20f,
            horizontalMarginDp = 24,
        )
    }
}
