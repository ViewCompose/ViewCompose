package com.viewcompose.overlay.android.presenter

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.viewcompose.widget.core.AndroidEnvironmentBridge
import com.viewcompose.widget.core.ModalBottomSheetOverlayContent
import com.viewcompose.widget.core.ModalBottomSheetOverlayHandle
import com.viewcompose.widget.core.ModalBottomSheetOverlayPresenter
import com.viewcompose.widget.core.ModalBottomSheetOverlaySpec
import com.viewcompose.widget.core.OverlayEntryId
import com.viewcompose.widget.core.OverlaySurfaceSession
import com.viewcompose.widget.core.createOverlaySurfaceSession

/**
 * Creates Material [BottomSheetDialog] handles for declarative modal bottom sheets.
 *
 * A same-key request reuses its handle and nested render session. Updates can change content,
 * dismissal policy, scrim opacity, expansion policy, and navigation-bar treatment without creating
 * a second platform window.
 *
 * @param rootView render root whose context and window configuration own created sheets
 */
class AndroidModalBottomSheetPresenter(
    private val rootView: View,
) : ModalBottomSheetOverlayPresenter {
    /**
     * Creates and immediately shows a modal bottom-sheet handle for [spec] and [content].
     *
     * [entryId] is the host's session-scoped ownership identity. Later same-key changes are applied
     * through the returned handle rather than by calling this method again.
     */
    override fun show(
        entryId: OverlayEntryId,
        spec: ModalBottomSheetOverlaySpec,
        content: ModalBottomSheetOverlayContent,
    ): ModalBottomSheetOverlayHandle {
        return AndroidModalBottomSheetHandle(
            rootView = rootView,
            spec = spec,
            content = content,
        )
    }
}

/** Owns one bottom-sheet dialog, its nested render session, and navigation-bar styling. */
private class AndroidModalBottomSheetHandle(
    rootView: View,
    spec: ModalBottomSheetOverlaySpec,
    content: ModalBottomSheetOverlayContent,
) : ModalBottomSheetOverlayHandle {
    private val dialogContainer = FrameLayout(rootView.context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    private val dialog = BottomSheetDialog(rootView.context).apply {
        setContentView(dialogContainer)
    }
    private val defaultNavigationBarColor: Int? = dialog.window?.readNavigationBarColorCompat()
    private val surfaceSession: OverlaySurfaceSession = createOverlaySurfaceSession(
        container = dialogContainer,
        content = content.surface,
    )
    private var currentSpec = spec
    private var programmaticDismiss = false

    init {
        dialog.setOnDismissListener {
            if (!programmaticDismiss) {
                currentSpec.onDismissRequest?.invoke()
            }
        }
        update(
            spec = spec,
            content = content,
        )
        dialog.show()
    }

    override fun update(
        spec: ModalBottomSheetOverlaySpec,
        content: ModalBottomSheetOverlayContent,
    ) {
        currentSpec = spec
        dialog.setCancelable(spec.dismissOnBackPress)
        dialog.setCanceledOnTouchOutside(spec.dismissOnClickOutside)
        dialog.window?.apply {
            setDimAmount(spec.scrimOpacity.coerceIn(0f, 1f))
            // Restore the dialog default when no override is declared and keep platform contrast.
            val color = spec.navigationBarColor ?: defaultNavigationBarColor
            if (color != null) {
                applyNavigationBarColorCompat(
                    color = color,
                    enforceContrast = spec.navigationBarColor == null,
                )
            }
        }
        if (spec.skipPartiallyExpanded) {
            // The Material behavior represents partial expansion with its collapsed intermediate state.
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.behavior.skipCollapsed = true
        }
        surfaceSession.update(content.surface)
        if (!dialog.isShowing) {
            dialog.show()
        }
    }

    override fun dismiss() {
        // Host cleanup is not a user dismissal and must not notify application close state twice.
        programmaticDismiss = true
        dialog.setOnDismissListener(null)
        surfaceSession.dispose()
        if (dialog.isShowing) {
            dialog.dismiss()
        }
        programmaticDismiss = false
    }
}

/** Reads the deprecated color API behind a single compatibility boundary. */
@Suppress("DEPRECATION")
private fun Window.readNavigationBarColorCompat(): Int = navigationBarColor

/** Applies navigation-bar color and Android Q+ contrast policy behind one compatibility boundary. */
@Suppress("DEPRECATION")
private fun Window.applyNavigationBarColorCompat(
    color: Int,
    enforceContrast: Boolean,
) {
    navigationBarColor = color
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isNavigationBarContrastEnforced = enforceContrast
    }
}
