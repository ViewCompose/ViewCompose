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
import com.viewcompose.ui.unit.dp
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
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.widget.core.OverlaySurfaceSession
import com.viewcompose.widget.core.createOverlaySurfaceSession

/**
 * Android Dialog overlay presenter。
 * Android Dialog overlay presenter.
 *
 * show 只创建 handle；后续更新由 host 对同一 requestKey 的 handle 调用 update 完成。
 * show only creates a handle; later updates are delivered by the host to the same requestKey handle.
 */
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

/**
 * Android PopupWindow overlay presenter。
 * Android PopupWindow overlay presenter.
 *
 * Popup 需要 rootView 查找 anchorId 对应的 View，并在布局/滚动变化时重新定位。
 * Popup needs the rootView to find the View matching anchorId and reposition on layout/scroll changes.
 */
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

/**
 * Dialog overlay 的平台句柄。
 * Platform handle for a dialog overlay.
 *
 * 句柄拥有 Dialog 与内部 OverlaySurfaceSession，dismiss 时必须同时释放平台窗口和渲染 session。
 * The handle owns both Dialog and OverlaySurfaceSession; dismiss must release the platform window and render session together.
 */
private class AndroidDialogOverlayHandle(
    rootView: View,
    spec: DialogOverlaySpec,
    content: DialogOverlayContent,
) : DialogOverlayHandle {
    private val density = AndroidEnvironmentBridge.fromContext(rootView.context).density
    private val dialogContainer = FrameLayout(rootView.context).apply {
        val inset = density.roundToPx(24.dp)
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
            // scrimOpacity 来自 DSL，进入平台前夹紧到 Window 支持的 0..1 范围。
            // scrimOpacity comes from DSL and is clamped to the Window-supported 0..1 range before applying.
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
        // 程序化 dismiss 不应回调 onDismissRequest，避免 host 清理造成业务侧重复关闭。
        // Programmatic dismiss must not call onDismissRequest to avoid duplicate business-close events during host cleanup.
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
 * Popup overlay 的平台句柄。
 * Platform handle for a popup overlay.
 *
 * 句柄监听 rootView 的 attach/layout/scroll 状态，并把 DSL 的锚点定位策略转换为 PopupWindow 坐标。
 * The handle observes rootView attach/layout/scroll state and converts DSL anchor positioning into PopupWindow coordinates.
 */
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
        elevation = density.toPx(12.dp)
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
        // 内容变化可能改变 popup 尺寸，因此更新 surface 后立即重新测量定位。
        // Content changes can alter popup size, so reposition immediately after updating the surface.
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
            // anchor 暂不可用时只隐藏窗口，不触发用户 dismiss 回调，等待下一次布局恢复。
            // When the anchor is temporarily unavailable, hide the window without firing user dismiss and wait for layout recovery.
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
            // 只有位置或尺寸实际变化时才 update，减少 PopupWindow 重新布局抖动。
            // Update only when position or size changes to reduce PopupWindow relayout churn.
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
        // 内部 hide 是定位策略的一部分，不代表用户点击外部关闭。
        // Internal hide is part of positioning behavior and does not mean the user dismissed outside.
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
            // 同时监听布局和滚动，覆盖 anchor 移动、键盘弹起和窗口可见区域变化。
            // Observe both layout and scroll to cover anchor movement, keyboard changes, and visible-frame changes.
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

/**
 * 深度优先查找带 overlay anchor tag 的 View。
 * Finds a View with the overlay anchor tag using depth-first traversal.
 */
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

/**
 * 将声明式 dialog 位置转换为 Android Window gravity。
 * Converts declarative dialog position to Android Window gravity.
 */
private fun DialogPosition.toGravity(): Int {
    return when (this) {
        DialogPosition.Top -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        DialogPosition.Center -> Gravity.CENTER
        DialogPosition.Bottom -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    }
}

/**
 * 为 popup 内容测量生成 AT_MOST 约束。
 * Builds an AT_MOST measure spec for popup content measurement.
 */
private fun Int.atMostMeasureSpec(): Int {
    return if (this > 0) {
        MeasureSpec.makeMeasureSpec(this, MeasureSpec.AT_MOST)
    } else {
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
    }
}
