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
 * Android BottomSheetDialog overlay presenter。
 * Android BottomSheetDialog overlay presenter.
 *
 * presenter 只负责创建平台 handle；同 key 请求的内容/行为更新由 handle.update 处理。
 * The presenter only creates platform handles; same-key content/behavior updates are handled by handle.update.
 */
class AndroidModalBottomSheetPresenter(
    private val rootView: View,
) : ModalBottomSheetOverlayPresenter {
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

/**
 * Modal bottom sheet overlay 的平台句柄。
 * Platform handle for a modal bottom sheet overlay.
 *
 * 句柄同时维护 BottomSheetDialog、内部内容渲染 session 和系统导航栏颜色。
 * The handle maintains the BottomSheetDialog, inner content render session, and system navigation bar color together.
 */
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
            // 未显式指定导航栏颜色时恢复 dialog 初始颜色，并继续让 Android 处理对比度。
            // When no navigation bar color is specified, restore the dialog's initial color and let Android handle contrast.
            val color = spec.navigationBarColor ?: defaultNavigationBarColor
            if (color != null) {
                applyNavigationBarColorCompat(
                    color = color,
                    enforceContrast = spec.navigationBarColor == null,
                )
            }
        }
        if (spec.skipPartiallyExpanded) {
            // skipPartiallyExpanded 对应 Material behavior：直接展开并跳过 collapsed 中间态。
            // skipPartiallyExpanded maps to Material behavior by expanding immediately and skipping the collapsed intermediate state.
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.behavior.skipCollapsed = true
        }
        surfaceSession.update(content.surface)
        if (!dialog.isShowing) {
            dialog.show()
        }
    }

    override fun dismiss() {
        // host 清理触发的 dismiss 不应再次通知业务 onDismissRequest。
        // Dismiss triggered by host cleanup should not notify business onDismissRequest again.
        programmaticDismiss = true
        dialog.setOnDismissListener(null)
        surfaceSession.dispose()
        if (dialog.isShowing) {
            dialog.dismiss()
        }
        programmaticDismiss = false
    }
}

/**
 * 兼容读取 Window.navigationBarColor。
 * Compatibility wrapper for reading Window.navigationBarColor.
 */
@Suppress("DEPRECATION")
private fun Window.readNavigationBarColorCompat(): Int = navigationBarColor

/**
 * 兼容设置 Window.navigationBarColor，并在 Android Q+ 控制对比度强制策略。
 * Compatibility wrapper for setting Window.navigationBarColor and controlling contrast enforcement on Android Q+.
 */
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
