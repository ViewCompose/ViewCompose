package com.viewcompose.overlay.oneui7.android.presenter

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.viewcompose.overlay.oneui7.android.OneUi7OverlayStyle
import com.viewcompose.overlay.oneui7.android.roundedDrawable
import com.viewcompose.ui.foundation.OverlayEntryId
import com.viewcompose.ui.foundation.SnackbarDuration
import com.viewcompose.ui.foundation.SnackbarOverlayPresenter
import com.viewcompose.ui.foundation.SnackbarOverlaySpec
import com.viewcompose.ui.foundation.TransientFeedbackDismissReason
import kotlin.math.roundToInt

/** Android-native, Material-free One UI Snackbar presenter. */
internal class AndroidOneUi7SnackbarPresenter(
    private val rootView: View,
    private val style: OneUi7OverlayStyle,
) : SnackbarOverlayPresenter {
    private val handler = Handler(Looper.getMainLooper())
    private val activeSnackbars = mutableMapOf<OverlayEntryId, ActiveSnackbar>()

    override fun show(
        entryId: OverlayEntryId,
        spec: SnackbarOverlaySpec,
        onDismissed: (TransientFeedbackDismissReason) -> Unit,
    ) {
        val content = createSnackbarContent(
            context = rootView.context,
            spec = spec,
            onAction = {
                spec.onAction?.invoke()
                dismiss(entryId, TransientFeedbackDismissReason.Action)
            },
        )
        val popup = PopupWindow(
            content,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false,
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isClippingEnabled = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            elevation = rootView.context.dp(8f)
        }
        val active = ActiveSnackbar(
            popup = popup,
            onDismissed = onDismissed,
        )
        activeSnackbars[entryId] = active
        popup.setOnDismissListener {
            finish(
                entryId = entryId,
                active = active,
                reason = active.requestedDismissReason ?: TransientFeedbackDismissReason.Platform,
            )
        }
        popup.showAtLocation(
            rootView,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            0,
            rootView.systemBottomInset() + rootView.context.dp(12f).roundToInt(),
        )
        spec.duration.timeoutMillis(rootView.context)?.let { timeoutMillis ->
            active.timeout = Runnable {
                dismiss(entryId, TransientFeedbackDismissReason.Timeout)
            }.also { handler.postDelayed(it, timeoutMillis) }
        }
    }

    override fun dismiss(
        entryId: OverlayEntryId,
        reason: TransientFeedbackDismissReason,
    ) {
        val active = activeSnackbars[entryId] ?: return
        if (active.completed) {
            return
        }
        active.requestedDismissReason = reason
        if (active.popup.isShowing) {
            active.popup.dismiss()
        } else {
            finish(entryId, active, reason)
        }
    }

    private fun finish(
        entryId: OverlayEntryId,
        active: ActiveSnackbar,
        reason: TransientFeedbackDismissReason,
    ) {
        if (active.completed || activeSnackbars[entryId] !== active) {
            return
        }
        active.completed = true
        active.timeout?.let(handler::removeCallbacks)
        active.popup.setOnDismissListener(null)
        activeSnackbars.remove(entryId)
        active.onDismissed(reason)
    }

    private fun createSnackbarContent(
        context: Context,
        spec: SnackbarOverlaySpec,
        onAction: () -> Unit,
    ): View {
        val horizontalMargin = context.dp(style.horizontalMarginDp.toFloat()).roundToInt()
        val outer = FrameLayout(context).apply {
            setPadding(horizontalMargin, 0, horizontalMargin, 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(48f).roundToInt()
            val horizontalPadding = context.dp(20f).roundToInt()
            setPadding(horizontalPadding, 0, context.dp(8f).roundToInt(), 0)
            background = roundedDrawable(
                color = style.snackbarColor,
                radiusPx = context.dp(18f),
            )
            elevation = context.dp(8f)
            contentDescription = ONE_UI_SNACKBAR_DESCRIPTION
        }
        val message = TextView(context).apply {
            text = spec.message
            setTextColor(style.snackbarContentColor)
            textSize = 15f
            maxLines = 3
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(12f).roundToInt(), context.dp(12f).roundToInt(), context.dp(12f).roundToInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        bar.addView(message)
        spec.actionLabel?.takeIf(String::isNotBlank)?.let { label ->
            bar.addView(
                TextView(context).apply {
                    text = label
                    setTextColor(style.actionColor)
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    minimumHeight = context.dp(48f).roundToInt()
                    isClickable = true
                    isFocusable = true
                    background = RippleDrawable(
                        ColorStateList.valueOf(style.actionColor.withAlpha(0.16f)),
                        null,
                        roundedDrawable(Color.WHITE, context.dp(14f)),
                    )
                    setPadding(context.dp(12f).roundToInt(), 0, context.dp(12f).roundToInt(), 0)
                    setOnClickListener { onAction() }
                },
            )
        }
        outer.addView(
            bar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        return outer
    }

    private data class ActiveSnackbar(
        val popup: PopupWindow,
        val onDismissed: (TransientFeedbackDismissReason) -> Unit,
        var requestedDismissReason: TransientFeedbackDismissReason? = null,
        var timeout: Runnable? = null,
        var completed: Boolean = false,
    )

    internal companion object {
        const val ONE_UI_SNACKBAR_DESCRIPTION = "One UI Snackbar"
    }
}

private fun SnackbarDuration.timeoutMillis(context: Context): Long? {
    val base = when (this) {
        SnackbarDuration.Short -> 4_000
        SnackbarDuration.Long -> 7_000
        SnackbarDuration.Indefinite -> return null
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return base.toLong()
    }
    val accessibility = context.getSystemService(AccessibilityManager::class.java)
    return accessibility?.getRecommendedTimeoutMillis(
        base,
        AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS,
    )?.toLong() ?: base.toLong()
}

private fun View.systemBottomInset(): Int =
    ViewCompat.getRootWindowInsets(this)
        ?.getInsets(WindowInsetsCompat.Type.systemBars())
        ?.bottom
        ?: 0

private fun Context.dp(value: Float): Float = value * resources.displayMetrics.density

private fun Int.withAlpha(alpha: Float): Int {
    val resolvedAlpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
    return (this and 0x00FFFFFF) or (resolvedAlpha shl 24)
}
