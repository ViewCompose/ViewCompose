package com.viewcompose.overlay.android.presenter

import android.app.Dialog
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.core.view.doOnLayout
import com.viewcompose.overlay.android.asOverlayRenderContainerHandle
import com.viewcompose.ui.overlay.OVERLAY_ANCHOR_TAG_KEY
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.host.android.environment.AndroidEnvironmentBridge
import com.viewcompose.ui.foundation.DialogOverlayContent
import com.viewcompose.ui.foundation.DialogOverlayHandle
import com.viewcompose.ui.foundation.DialogOverlayPresenter
import com.viewcompose.ui.foundation.DialogOverlaySpec
import com.viewcompose.ui.foundation.DialogPosition
import com.viewcompose.ui.foundation.OverlayEntryId
import com.viewcompose.ui.foundation.PopupBounds
import com.viewcompose.ui.foundation.PopupOverlayContent
import com.viewcompose.ui.foundation.PopupOverlayHandle
import com.viewcompose.ui.foundation.PopupOverlayPresenter
import com.viewcompose.ui.foundation.PopupOverlaySpec
import com.viewcompose.ui.foundation.PopupOverflowPolicy
import com.viewcompose.ui.foundation.PopupPositioner
import com.viewcompose.ui.foundation.PopupSize
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.foundation.OverlaySurfaceSession
import com.viewcompose.ui.foundation.createOverlaySurfaceSession
import kotlin.math.ceil

/**
 * Creates Android [Dialog] handles for declarative dialog requests.
 *
 * The presenter creates one handle per session-scoped overlay identity. Same-key updates are sent to
 * that handle by the core overlay host, preserving the platform window and nested render session.
 *
 * @param rootView render root whose context and window configuration own created dialogs
 */
internal class AndroidDialogOverlayPresenter(
    private val rootView: View,
    private val windowInset: UiDp,
) : DialogOverlayPresenter {
    /**
     * Creates and immediately shows a dialog handle for [spec] and [content].
     *
     * [entryId] identifies ownership to the host; it is not used as application-visible state.
     * Later content or policy changes arrive through the returned handle's update contract.
     */
    override fun show(
        entryId: OverlayEntryId,
        spec: DialogOverlaySpec,
        content: DialogOverlayContent,
    ): DialogOverlayHandle {
        return AndroidDialogOverlayHandle(
            rootView = rootView,
            windowInset = windowInset,
            spec = spec,
            content = content,
        )
    }
}

/**
 * Creates anchored Android [PopupWindow] handles for declarative popup requests.
 *
 * Each handle resolves the requested anchor below [rootView] and follows layout, scrolling, window
 * visible-frame, and layout-direction changes. A temporarily missing anchor hides the popup without
 * treating that condition as a user dismissal; the handle shows it again when the anchor returns.
 *
 * @param rootView attached render root searched for overlay anchor tags
 */
internal class AndroidPopupOverlayPresenter(
    private val rootView: View,
) : PopupOverlayPresenter {
    /**
     * Creates a popup handle and begins observing [rootView] for [spec]'s anchor.
     *
     * The popup can remain hidden until a matching, laid-out anchor exists. Same-key updates reuse
     * the returned handle and remeasure [content] before recalculating placement.
     */
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

/** Owns one dialog window and the nested render session that supplies its content. */
private class AndroidDialogOverlayHandle(
    rootView: View,
    windowInset: UiDp,
    spec: DialogOverlaySpec,
    content: DialogOverlayContent,
) : DialogOverlayHandle {
    private val density = AndroidEnvironmentBridge.fromContext(rootView.context).density
    private val dialogContainer = FrameLayout(rootView.context).apply {
        val inset = density.roundToPx(windowInset)
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
        container = dialogContainer.asOverlayRenderContainerHandle(),
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
            // Clamp the declarative value before handing it to the platform Window API.
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
        // Host cleanup is not a user dismissal and must not call application close state twice.
        programmaticDismiss = true
        dialog.setOnDismissListener(null)
        surfaceSession.dispose()
        if (dialog.isShowing) {
            dialog.dismiss()
        }
        programmaticDismiss = false
    }
}

/** Owns one popup window, nested render session, and anchor-observation lifecycle. */
private class AndroidPopupOverlayHandle(
    private val rootView: View,
    spec: PopupOverlaySpec,
    content: PopupOverlayContent,
) : PopupOverlayHandle {
    private val popupContentContainer = FrameLayout(rootView.context).apply {
        background = ColorDrawable(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    private val popupContainer = FrameLayout(rootView.context).apply {
        background = ColorDrawable(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        addView(popupContentContainer)
    }
    private val popupWindow = PopupWindow(
        popupContainer,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        spec.focusable,
    ).apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        // Popup content owns its shape and elevation. A second platform elevation produces a
        // rectangular shadow around the exact PopupWindow bounds and clips the content shadow.
        elevation = 0f
    }
    private val surfaceSession: OverlaySurfaceSession = createOverlaySurfaceSession(
        container = popupContentContainer.asOverlayRenderContainerHandle(),
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
        popupWindow.setTouchInterceptor { _, event ->
            if (
                event.actionMasked == MotionEvent.ACTION_DOWN &&
                currentSpec.dismissOnClickOutside &&
                popupContainer.isOutsideContentBounds(event.x, event.y)
            ) {
                // Transparent shadow outsets remain outside the semantic popup content.
                // Dismissing through PopupWindow preserves the single user-dismiss callback path.
                popupWindow.dismiss()
                true
            } else {
                false
            }
        }
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
        // Content can change intrinsic size, so placement must be recomputed after every update.
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
            // Missing geometry is transient: hide without reporting a user dismissal and await layout.
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
        val shadowOutset = popupContentContainer.requiredNativeShadowOutsetPx()
        if (
            popupContainer.paddingLeft != shadowOutset ||
            popupContainer.paddingTop != shadowOutset ||
            popupContainer.paddingRight != shadowOutset ||
            popupContainer.paddingBottom != shadowOutset
        ) {
            popupContainer.setPadding(
                shadowOutset,
                shadowOutset,
                shadowOutset,
                shadowOutset,
            )
        }
        popupContainer.measure(
            visibleFrame.width().atMostMeasureSpec(),
            visibleFrame.height().atMostMeasureSpec(),
        )
        val windowWidth = popupContainer.measuredWidth
        val windowHeight = popupContainer.measuredHeight
        val contentWidth = (
            windowWidth - popupContainer.paddingLeft - popupContainer.paddingRight
        ).coerceAtLeast(0)
        val contentHeight = (
            windowHeight - popupContainer.paddingTop - popupContainer.paddingBottom
        ).coerceAtLeast(0)
        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val contentViewportLeft = (visibleFrame.left + popupContainer.paddingLeft)
            .coerceAtMost(visibleFrame.right)
        val contentViewportTop = (visibleFrame.top + popupContainer.paddingTop)
            .coerceAtMost(visibleFrame.bottom)
        val contentViewportRight = (visibleFrame.right - popupContainer.paddingRight)
            .coerceAtLeast(contentViewportLeft)
        val contentViewportBottom = (visibleFrame.bottom - popupContainer.paddingBottom)
            .coerceAtLeast(contentViewportTop)
        val position = PopupPositioner.calculate(
            anchorBounds = PopupBounds(
                left = anchorLocation[0],
                top = anchorLocation[1],
                right = anchorLocation[0] + anchor.width,
                bottom = anchorLocation[1] + anchor.height,
            ),
            popupSize = PopupSize(
                width = contentWidth,
                height = contentHeight,
            ),
            viewportBounds = PopupBounds(
                left = contentViewportLeft,
                top = contentViewportTop,
                right = contentViewportRight,
                bottom = contentViewportBottom,
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
        val windowX = position.x - popupContainer.paddingLeft
        val windowY = position.y - popupContainer.paddingTop
        if (!popupWindow.isShowing) {
            popupWindow.width = windowWidth
            popupWindow.height = windowHeight
            popupWindow.showAtLocation(
                rootView,
                Gravity.NO_GRAVITY,
                windowX,
                windowY,
            )
        } else if (
            lastX != windowX ||
            lastY != windowY ||
            lastWidth != windowWidth ||
            lastHeight != windowHeight
        ) {
            // Avoid an unnecessary PopupWindow relayout when measured geometry is unchanged.
            popupWindow.update(
                windowX,
                windowY,
                windowWidth,
                windowHeight,
            )
        }
        lastX = windowX
        lastY = windowY
        lastWidth = windowWidth
        lastHeight = windowHeight
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
        // Internal hiding is part of anchor recovery, not an outside-click dismissal.
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
            // Both listeners are needed for anchor motion, scrolling, IME, and visible-frame changes.
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

private fun View.requiredNativeShadowOutsetPx(): Int {
    fun View.maximumNativeElevationPx(): Float {
        if (visibility != View.VISIBLE) {
            return 0f
        }
        var maximum = (elevation + translationZ).coerceAtLeast(0f)
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                maximum = maxOf(maximum, getChildAt(index).maximumNativeElevationPx())
            }
        }
        return maximum
    }

    return ceil(maximumNativeElevationPx() * POPUP_NATIVE_SHADOW_OUTSET_MULTIPLIER).toInt()
}

private fun ViewGroup.isOutsideContentBounds(x: Float, y: Float): Boolean {
    return x < paddingLeft ||
        x >= width - paddingRight ||
        y < paddingTop ||
        y >= height - paddingBottom
}

/** Finds the first matching overlay anchor using depth-first child order. */
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

/** Converts logical dialog placement to Android window gravity. */
private fun DialogPosition.toGravity(): Int {
    return when (this) {
        DialogPosition.Top -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        DialogPosition.Center -> Gravity.CENTER
        DialogPosition.Bottom -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    }
}

/** Builds a bounded popup-content measure specification. */
private fun Int.atMostMeasureSpec(): Int {
    return if (this > 0) {
        MeasureSpec.makeMeasureSpec(this, MeasureSpec.AT_MOST)
    } else {
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
    }
}

// Android's ambient and spot shadows vary by API and light configuration. Twice the effective Z
// conservatively contains both components without making the window itself a second shadow owner.
private const val POPUP_NATIVE_SHADOW_OUTSET_MULTIPLIER = 2f
