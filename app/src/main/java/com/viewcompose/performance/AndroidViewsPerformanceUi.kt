package com.viewcompose.performance

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import kotlin.math.roundToInt

internal data class AndroidViewsPerformanceHeader(
    val view: LinearLayout,
    val stateView: TextView,
)

internal fun createAndroidViewsPerformanceHeader(
    context: Context,
    readyText: String,
    stateText: String,
    primaryActionText: String,
    secondaryActionText: String? = null,
    resetText: String,
    scenario: DemoScenarioSpec,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: (() -> Unit)? = null,
    onReset: () -> Unit,
): AndroidViewsPerformanceHeader {
    val header = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            context.performanceDp(12),
            context.performanceDp(12),
            context.performanceDp(12),
            context.performanceDp(12),
        )
        background = context.performanceRoundedBackground(
            color = PERFORMANCE_SURFACE_COLOR,
            radiusDp = 0,
        )
    }
    val readyView = context.performanceTextView(
        text = readyText,
        sizeSp = 18f,
        color = PERFORMANCE_PRIMARY_TEXT_COLOR,
        medium = true,
    ).apply {
        performanceScenarioTarget(scenario, DemoAutomationRole.Ready)
    }
    header.addView(
        readyView,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )
    val stateView = context.performanceTextView(
        text = stateText,
        sizeSp = 14f,
        color = PERFORMANCE_SECONDARY_TEXT_COLOR,
    ).apply {
        performanceScenarioTarget(scenario, DemoAutomationRole.State)
    }
    header.addView(
        stateView,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).withTopMargin(context.performanceDp(8)),
    )
    val actions = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val primaryAction = context.performanceActionView(
        text = primaryActionText,
        onClick = onPrimaryAction,
    ).apply {
        performanceScenarioTarget(scenario, DemoAutomationRole.PrimaryAction)
    }
    actions.addView(
        primaryAction,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )
    if (secondaryActionText != null && onSecondaryAction != null) {
        val secondaryAction = context.performanceActionView(
            text = secondaryActionText,
            onClick = onSecondaryAction,
        ).apply {
            performanceScenarioTarget(scenario, DemoAutomationRole.SecondaryAction)
        }
        actions.addView(
            secondaryAction,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).withStartMargin(context.performanceDp(8)),
        )
    }
    val reset = context.performanceActionView(
        text = resetText,
        onClick = onReset,
    ).apply {
        performanceScenarioTarget(scenario, DemoAutomationRole.Reset)
    }
    actions.addView(
        reset,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).withStartMargin(context.performanceDp(8)),
    )
    header.addView(
        actions,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).withTopMargin(context.performanceDp(8)),
    )
    return AndroidViewsPerformanceHeader(
        view = header,
        stateView = stateView,
    )
}

internal fun Context.performanceTextView(
    text: String? = null,
    sizeSp: Float,
    color: Int,
    medium: Boolean = false,
): TextView = TextView(this).apply {
    if (text != null) {
        this.text = text
    }
    textSize = sizeSp
    setTextColor(color)
    maxLines = 1
    ellipsize = TextUtils.TruncateAt.END
    if (medium) {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
}

internal fun Context.performanceRoundedBackground(
    color: Int,
    radiusDp: Int,
): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    setColor(color)
    cornerRadius = performanceDp(radiusDp).toFloat()
}

internal fun Context.performanceDp(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt()

internal fun View.performanceScenarioTarget(
    scenario: DemoScenarioSpec,
    role: DemoAutomationRole,
) {
    id = scenario.automation.require(role).androidViewId
}

internal fun LinearLayout.LayoutParams.withTopMargin(value: Int): LinearLayout.LayoutParams =
    apply { topMargin = value }

internal fun LinearLayout.LayoutParams.withStartMargin(value: Int): LinearLayout.LayoutParams =
    apply { marginStart = value }

private fun Context.performanceActionView(
    text: String,
    onClick: () -> Unit,
): TextView = performanceTextView(
    text = text,
    sizeSp = 14f,
    color = 0xFFFFFFFF.toInt(),
    medium = true,
).apply {
    gravity = Gravity.CENTER
    isClickable = true
    isFocusable = true
    background = RippleDrawable(
        ColorStateList.valueOf(0x33FFFFFF),
        performanceRoundedBackground(
            color = PERFORMANCE_PRIMARY_COLOR,
            radiusDp = 8,
        ),
        performanceRoundedBackground(
            color = 0xFFFFFFFF.toInt(),
            radiusDp = 8,
        ),
    )
    setPadding(
        performanceDp(14),
        performanceDp(8),
        performanceDp(14),
        performanceDp(8),
    )
    setOnClickListener { onClick() }
}
