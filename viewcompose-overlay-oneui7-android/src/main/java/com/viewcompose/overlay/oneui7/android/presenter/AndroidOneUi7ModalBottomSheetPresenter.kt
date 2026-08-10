package com.viewcompose.overlay.oneui7.android.presenter

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.viewcompose.overlay.android.asOverlayRenderContainerHandle
import com.viewcompose.overlay.oneui7.android.OneUi7OverlayStyle
import com.viewcompose.overlay.oneui7.android.roundedDrawable
import com.viewcompose.ui.foundation.ModalBottomSheetOverlayContent
import com.viewcompose.ui.foundation.ModalBottomSheetOverlayHandle
import com.viewcompose.ui.foundation.ModalBottomSheetOverlayPresenter
import com.viewcompose.ui.foundation.ModalBottomSheetOverlaySpec
import com.viewcompose.ui.foundation.OverlayEntryId
import com.viewcompose.ui.foundation.OverlaySurfaceSession
import com.viewcompose.ui.foundation.createOverlaySurfaceSession
import kotlin.math.max
import kotlin.math.roundToInt

/** Material-free bottom-dialog presenter with One UI chrome and drag-to-dismiss behavior. */
internal class AndroidOneUi7ModalBottomSheetPresenter(
    private val rootView: View,
    private val style: OneUi7OverlayStyle,
) : ModalBottomSheetOverlayPresenter {
    override fun show(
        entryId: OverlayEntryId,
        spec: ModalBottomSheetOverlaySpec,
        content: ModalBottomSheetOverlayContent,
    ): ModalBottomSheetOverlayHandle = AndroidOneUi7ModalBottomSheetHandle(
        rootView = rootView,
        style = style,
        spec = spec,
        content = content,
    )
}

/** Owns one bottom-gravity Dialog and its nested ViewCompose render session. */
private class AndroidOneUi7ModalBottomSheetHandle(
    private val rootView: View,
    private val style: OneUi7OverlayStyle,
    spec: ModalBottomSheetOverlaySpec,
    content: ModalBottomSheetOverlayContent,
) : ModalBottomSheetOverlayHandle {
    private val context = rootView.context
    private val contentContainer = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    private val sheet = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedDrawable(style.surfaceColor, context.dp(style.cornerRadiusDp))
        elevation = context.dp(16f)
        contentDescription = ONE_UI_BOTTOM_SHEET_DESCRIPTION
    }
    private val dragArea = FrameLayout(context).apply {
        minimumHeight = context.dp(40f).roundToInt()
        isClickable = true
        isFocusable = true
        addView(
            View(context).apply {
                background = roundedDrawable(style.outlineColor, context.dp(2f))
            },
            FrameLayout.LayoutParams(
                context.dp(32f).roundToInt(),
                context.dp(4f).roundToInt(),
                Gravity.CENTER,
            ),
        )
    }
    private val windowContainer = FrameLayout(context).apply {
        val horizontal = context.dp(style.horizontalMarginDp.toFloat()).roundToInt()
        setPadding(horizontal, 0, horizontal, rootView.systemBottomInset() + context.dp(12f).roundToInt())
        addView(
            sheet,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
    }
    private val dialog = Dialog(context).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(windowContainer)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
    @Suppress("DEPRECATION")
    private val defaultNavigationBarColor = dialog.window?.navigationBarColor
    private val surfaceSession: OverlaySurfaceSession
    private var currentSpec = spec
    private var programmaticDismiss = false
    private var disposed = false

    init {
        sheet.addView(
            dragArea,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.dp(40f).roundToInt(),
            ),
        )
        sheet.addView(contentContainer)
        surfaceSession = createOverlaySurfaceSession(
            container = contentContainer.asOverlayRenderContainerHandle(),
            content = content.surface,
        )
        dragArea.setOnTouchListener(SheetDragDismissTouchListener())
        dialog.setOnDismissListener {
            if (!programmaticDismiss && !disposed) {
                currentSpec.onDismissRequest?.invoke()
            }
        }
        dialog.show()
        update(spec, content)
    }

    override fun update(
        spec: ModalBottomSheetOverlaySpec,
        content: ModalBottomSheetOverlayContent,
    ) {
        if (disposed) {
            return
        }
        currentSpec = spec
        dialog.setCancelable(spec.dismissOnBackPress)
        dialog.setCanceledOnTouchOutside(spec.dismissOnClickOutside)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            val clampedScrim = spec.scrimOpacity.coerceIn(0f, 1f)
            if (clampedScrim > 0f) {
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(clampedScrim)
            } else {
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
            (spec.navigationBarColor ?: defaultNavigationBarColor)?.let(::applyNavigationBarColorCompat)
        }
        surfaceSession.update(content.surface)
        if (!dialog.isShowing) {
            dialog.show()
        }
    }

    override fun dismiss() {
        if (disposed) {
            return
        }
        disposed = true
        programmaticDismiss = true
        dialog.setOnDismissListener(null)
        dragArea.setOnTouchListener(null)
        surfaceSession.dispose()
        if (dialog.isShowing) {
            dialog.dismiss()
        }
        programmaticDismiss = false
    }

    private fun dismissFromGesture() {
        if (!dialog.isShowing || disposed) {
            return
        }
        dialog.dismiss()
    }

    @Suppress("ClickableViewAccessibility")
    private inner class SheetDragDismissTouchListener : View.OnTouchListener {
        private var startY = 0f
        private var velocityTracker: VelocityTracker? = null

        override fun onTouch(
            view: View,
            event: MotionEvent,
        ): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                    sheet.animate().cancel()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    sheet.translationY = max(0f, event.rawY - startY)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1_000)
                    val velocity = velocityTracker?.yVelocity ?: 0f
                    val threshold = max(context.dp(96f), sheet.height * 0.25f)
                    val shouldDismiss = sheet.translationY >= threshold || velocity >= context.dp(900f)
                    velocityTracker?.recycle()
                    velocityTracker = null
                    if (shouldDismiss) {
                        dismissFromGesture()
                    } else {
                        sheet.animate().translationY(0f).setDuration(180L).start()
                        view.performClick()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.recycle()
                    velocityTracker = null
                    sheet.animate().translationY(0f).setDuration(180L).start()
                    return true
                }
            }
            return false
        }
    }

    private companion object {
        const val ONE_UI_BOTTOM_SHEET_DESCRIPTION = "One UI Bottom Sheet"
    }
}

private fun View.systemBottomInset(): Int =
    ViewCompat.getRootWindowInsets(this)
        ?.getInsets(WindowInsetsCompat.Type.systemBars())
        ?.bottom
        ?: 0

private fun android.content.Context.dp(value: Float): Float = value * resources.displayMetrics.density

@Suppress("DEPRECATION")
private fun Window.applyNavigationBarColorCompat(color: Int) {
    navigationBarColor = color
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isNavigationBarContrastEnforced = false
    }
}
