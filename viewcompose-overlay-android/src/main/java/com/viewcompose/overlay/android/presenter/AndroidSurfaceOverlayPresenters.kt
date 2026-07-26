package com.viewcompose.overlay.android.presenter

import android.app.Dialog
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.core.view.doOnLayout
import com.viewcompose.ui.overlay.OVERLAY_ANCHOR_TAG_KEY
import com.viewcompose.widget.core.AndroidEnvironmentBridge
import com.viewcompose.widget.core.DialogOverlayContent
import com.viewcompose.widget.core.DialogOverlayHandle
import com.viewcompose.widget.core.DialogOverlayPresenter
import com.viewcompose.widget.core.DialogOverlaySpec
import com.viewcompose.widget.core.DialogPosition
import com.viewcompose.widget.core.OverlayEntryId
import com.viewcompose.widget.core.PopupBounds
import com.viewcompose.widget.core.PopupOverlayContent
import com.viewcompose.widget.core.PopupOverlayHandle
import com.viewcompose.widget.core.PopupOverlayPresenter
import com.viewcompose.widget.core.PopupOverlaySpec
import com.viewcompose.widget.core.PopupOverflowPolicy
import com.viewcompose.widget.core.PopupPositioner
import com.viewcompose.widget.core.PopupSize
import com.viewcompose.widget.core.UiLayoutDirection
import com.viewcompose.widget.core.OverlaySurfaceSession
import com.viewcompose.widget.core.createOverlaySurfaceSession

class AndroidDialogOverlayPresenter(
    private val rootView: View,
) : DialogOverlayPresenter {
    override fun show(
        entryId: OverlayEntryId,
        spec: DialogOverlaySpec,
        content: DialogOverlayContent,
    ): DialogOverlayHandle {
        return AndroidDialogOverlayHandle(
            rootView = rootView,
            spec = spec,
            content = content,
        )
    }
}

class AndroidPopupOverlayPresenter(
    private val rootView: View,
) : PopupOverlayPresenter {
    override fun show(
        entryId: OverlayEntryId,
        spec: PopupOverlaySpec,
        content: PopupOverlayContent,
    ): PopupOverlayHandle {
        return AndroidPopupOverlayHandle(
            rootView = rootView,
            spec = spec,
            content = content,
        )
    }
}

private class AndroidDialogOverlayHandle(
    rootView: View,
    spec: DialogOverlaySpec,
    content: DialogOverlayContent,
) : DialogOverlayHandle {
    private val density = AndroidEnvironmentBridge.fromContext(rootView.context).density
    private val dialogContainer = FrameLayout(rootView.context).apply {
        val inset = density.dp(24)
        setPadding(inset, inset, inset, inset)
        background = ColorDrawable(Color.TRANSPARENT)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    private val dialog = Dialog(rootView.context).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(dialogContainer)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
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
        spec: DialogOverlaySpec,
        content: DialogOverlayContent,
    ) {
        currentSpec = spec
        dialog.setCancelable(spec.dismissOnBackPress)
        dialog.setCanceledOnTouchOutside(spec.dismissOnClickOutside)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setGravity(spec.position.toGravity())
            val clampedScrim = spec.scrimOpacity.coerceIn(0f, 1f)
            if (clampedScrim > 0f) {
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(clampedScrim)
            } else {
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
        surfaceSession.update(content.surface)
        if (!dialog.isShowing) {
            dialog.show()
        }
    }

    override fun dismiss() {
        programmaticDismiss = true
        dialog.setOnDismissListener(null)
        surfaceSession.dispose()
        if (dialog.isShowing) {
            dialog.dismiss()
        }
        programmaticDismiss = false
    }
}

private class AndroidPopupOverlayHandle(
    private val rootView: View,
    spec: PopupOverlaySpec,
    content: PopupOverlayContent,
) : PopupOverlayHandle {
    private val density = AndroidEnvironmentBridge.fromContext(rootView.context).density
    private val popupContainer = FrameLayout(rootView.context).apply {
        background = ColorDrawable(Color.TRANSPARENT)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    private val popupWindow = PopupWindow(
        popupContainer,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        spec.focusable,
    ).apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        elevation = density.dp(12).toFloat()
    }
    private val surfaceSession: OverlaySurfaceSession = createOverlaySurfaceSession(
        container = popupContainer,
        content = content.surface,
    )
    private var currentSpec = spec
    private var ignoreNextDismiss = false
    private var userDismissed = false
    private var disposed = false
    private var observedTreeObserver: ViewTreeObserver? = null
    private var lastX: Int? = null
    private var lastY: Int? = null
    private var lastWidth: Int? = null
    private var lastHeight: Int? = null
    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        reposition()
    }
    private val scrollChangedListener = ViewTreeObserver.OnScrollChangedListener {
        reposition()
    }
    private val attachStateListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
            attachTreeObservers()
            reposition()
        }

        override fun onViewDetachedFromWindow(view: View) {
            detachTreeObservers()
            hideWindow()
        }
    }

    init {
        popupWindow.setOnDismissListener {
            if (!ignoreNextDismiss && !disposed) {
                userDismissed = true
                currentSpec.onDismissRequest?.invoke()
            }
        }
        rootView.addOnAttachStateChangeListener(attachStateListener)
        attachTreeObservers()
        update(
            spec = spec,
            content = content,
        )
    }

    override fun update(
        spec: PopupOverlaySpec,
        content: PopupOverlayContent,
    ) {
        if (disposed) {
            return
        }
        currentSpec = spec
        userDismissed = false
        popupWindow.isFocusable = spec.focusable
        popupWindow.isOutsideTouchable = spec.dismissOnClickOutside
        popupWindow.isClippingEnabled = spec.overflowPolicy != PopupOverflowPolicy.None
        surfaceSession.update(content.surface)
        reposition()
        popupContainer.doOnLayout {
            reposition()
        }
    }

    private fun reposition() {
        if (disposed || userDismissed || !rootView.isAttachedToWindow) {
            return
        }
        val anchor = rootView.findAnchorTarget(currentSpec.anchorId)
        if (anchor == null || !anchor.isAttachedToWindow || anchor.width <= 0 || anchor.height <= 0) {
            hideWindow()
            return
        }
        val visibleFrame = Rect()
        rootView.getWindowVisibleDisplayFrame(visibleFrame)
        if (visibleFrame.width() <= 0 || visibleFrame.height() <= 0) {
            val rootLocation = IntArray(2)
            rootView.getLocationOnScreen(rootLocation)
            visibleFrame.set(
                rootLocation[0],
                rootLocation[1],
                rootLocation[0] + rootView.width,
                rootLocation[1] + rootView.height,
            )
        }
        popupContainer.measure(
            visibleFrame.width().atMostMeasureSpec(),
            visibleFrame.height().atMostMeasureSpec(),
        )
        val popupWidth = popupContainer.measuredWidth
        val popupHeight = popupContainer.measuredHeight
        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val position = PopupPositioner.calculate(
            anchorBounds = PopupBounds(
                left = anchorLocation[0],
                top = anchorLocation[1],
                right = anchorLocation[0] + anchor.width,
                bottom = anchorLocation[1] + anchor.height,
            ),
            popupSize = PopupSize(
                width = popupWidth,
                height = popupHeight,
            ),
            viewportBounds = PopupBounds(
                left = visibleFrame.left,
                top = visibleFrame.top,
                right = visibleFrame.right,
                bottom = visibleFrame.bottom,
            ),
            alignment = currentSpec.alignment,
            layoutDirection = if (anchor.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                UiLayoutDirection.Rtl
            } else {
                UiLayoutDirection.Ltr
            },
            overflowPolicy = currentSpec.overflowPolicy,
            windowMargin = currentSpec.windowMargin,
            offsetX = currentSpec.offsetX,
            offsetY = currentSpec.offsetY,
        )
        if (!popupWindow.isShowing) {
            popupWindow.width = popupWidth
            popupWindow.height = popupHeight
            popupWindow.showAtLocation(
                rootView,
                Gravity.NO_GRAVITY,
                position.x,
                position.y,
            )
        } else if (
            lastX != position.x ||
            lastY != position.y ||
            lastWidth != popupWidth ||
            lastHeight != popupHeight
        ) {
            popupWindow.update(
                position.x,
                position.y,
                popupWidth,
                popupHeight,
            )
        }
        lastX = position.x
        lastY = position.y
        lastWidth = popupWidth
        lastHeight = popupHeight
    }

    override fun dismiss() {
        if (disposed) {
            return
        }
        disposed = true
        rootView.removeOnAttachStateChangeListener(attachStateListener)
        detachTreeObservers()
        popupWindow.setOnDismissListener(null)
        surfaceSession.dispose()
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    private fun hideWindow() {
        if (!popupWindow.isShowing) {
            return
        }
        ignoreNextDismiss = true
        popupWindow.dismiss()
        ignoreNextDismiss = false
        lastX = null
        lastY = null
        lastWidth = null
        lastHeight = null
    }

    private fun attachTreeObservers() {
        if (!rootView.isAttachedToWindow) {
            return
        }
        val observer = rootView.viewTreeObserver
        if (observedTreeObserver === observer) {
            return
        }
        detachTreeObservers()
        if (observer.isAlive) {
            observer.addOnGlobalLayoutListener(globalLayoutListener)
            observer.addOnScrollChangedListener(scrollChangedListener)
            observedTreeObserver = observer
        }
    }

    private fun detachTreeObservers() {
        observedTreeObserver?.takeIf { it.isAlive }?.let { observer ->
            observer.removeOnGlobalLayoutListener(globalLayoutListener)
            observer.removeOnScrollChangedListener(scrollChangedListener)
        }
        observedTreeObserver = null
    }
}

private fun View.findAnchorTarget(anchorId: String): View? {
    if (getTag(OVERLAY_ANCHOR_TAG_KEY) == anchorId) {
        return this
    }
    val group = this as? ViewGroup ?: return null
    for (index in 0 until group.childCount) {
        val match = group.getChildAt(index).findAnchorTarget(anchorId)
        if (match != null) {
            return match
        }
    }
    return null
}

private fun DialogPosition.toGravity(): Int {
    return when (this) {
        DialogPosition.Top -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        DialogPosition.Center -> Gravity.CENTER
        DialogPosition.Bottom -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    }
}

private fun Int.atMostMeasureSpec(): Int {
    return if (this > 0) {
        MeasureSpec.makeMeasureSpec(this, MeasureSpec.AT_MOST)
    } else {
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
    }
}
