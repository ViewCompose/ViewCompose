package com.viewcompose.renderer.view.container

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.shape.MaterialShapeDrawable
import com.viewcompose.renderer.view.shape.toShapeAppearanceModel
import com.viewcompose.ui.node.SegmentedControlItem
import com.viewcompose.ui.node.spec.UiFontFamily
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.renderer.view.tree.ContentViewBinder
import java.util.IdentityHashMap
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.dp

/**
 * SegmentedControl 的 Android LinearLayout 实现。
 * Android LinearLayout implementation for SegmentedControl.
 *
 * 每个 segment 直接使用 TextView，背景对象被缓存以便只更新选中 indicator。
 * Each segment is a TextView, and background wrappers are cached so only the selected indicator is updated.
 */
internal class DeclarativeSegmentedControlLayout(
    context: Context,
) : LinearLayout(context) {
    private var items: List<SegmentedControlItem> = emptyList()
    private var selectedIndex: Int = -1
    private var onSelectionChange: ((Int) -> Unit)? = null
    private var styleInitialized: Boolean = false
    private var enabledState: Boolean = true
    private var backgroundColorState: Int = Color.TRANSPARENT
    private var indicatorColorState: Int = Color.TRANSPARENT
    private var shapeState: UiShape = UiShape.rounded(UiDp.Zero)
    private var textColorState: Int = Color.BLACK
    private var selectedTextColorState: Int = Color.WHITE
    private var rippleColorState: Int = Color.TRANSPARENT
    private var textSizePxState: Float = 14f
    private var fontWeightState: Int? = null
    private var fontFamilyState: UiFontFamily? = null
    private var letterSpacingState: Float? = null
    private var lineHeightPxState: Int? = null
    private var includeFontPaddingState: Boolean = false
    private var paddingHorizontalState: Int = 0
    private var paddingVerticalState: Int = 0
    private var densityState: UiDensity = UiDensity.Default
    private val indicatorInset = 2.dp
    private val containerBackground = MaterialShapeDrawable()
    // 使用身份映射避免 TextView 文本相等时误共享背景状态。
    // Identity mapping avoids sharing background state between TextViews that happen to have equal content.
    private val segmentBackgrounds = IdentityHashMap<TextView, SegmentBackground>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipToPadding = false
        background = containerBackground
    }

    fun bind(
        items: List<SegmentedControlItem>,
        selectedIndex: Int,
        onSelectionChange: ((Int) -> Unit)?,
        enabled: Boolean,
        backgroundColor: Int,
        indicatorColor: Int,
        shape: UiShape,
        textColor: Int,
        selectedTextColor: Int,
        rippleColor: Int,
        textSizePx: Float,
        fontWeight: Int?,
        fontFamily: UiFontFamily?,
        letterSpacingEm: Float?,
        lineHeightPx: Int?,
        includeFontPadding: Boolean,
        paddingHorizontal: Int,
        paddingVertical: Int,
        density: UiDensity,
    ) {
        this.onSelectionChange = onSelectionChange
        if (background !== containerBackground) {
            background = containerBackground
        }
        // label 或数量变化会重建子 View；纯样式和选中态变化走增量更新。
        // Label or count changes rebuild child views; pure style and selection changes use incremental updates.
        val labelsChanged = this.items.size != items.size ||
            items.indices.any { index -> this.items[index].label != items[index].label }
        if (labelsChanged || childCount != items.size) {
            rebuild(items)
        }

        val resolvedSelectedIndex = if (items.isEmpty()) {
            -1
        } else {
            selectedIndex.coerceIn(0, items.lastIndex)
        }
        val previousSelectedIndex = this.selectedIndex
        val selectedChanged = previousSelectedIndex != resolvedSelectedIndex
        val styleChanged = !styleInitialized ||
            enabledState != enabled ||
            indicatorColorState != indicatorColor ||
            shapeState != shape ||
            textColorState != textColor ||
            selectedTextColorState != selectedTextColor ||
            rippleColorState != rippleColor ||
            textSizePxState != textSizePx ||
            fontWeightState != fontWeight ||
            fontFamilyState != fontFamily ||
            letterSpacingState != letterSpacingEm ||
            lineHeightPxState != lineHeightPx ||
            includeFontPaddingState != includeFontPadding ||
            paddingHorizontalState != paddingHorizontal ||
            paddingVerticalState != paddingVertical ||
            densityState != density

        if (!styleInitialized || backgroundColorState != backgroundColor) {
            containerBackground.fillColor = ColorStateList.valueOf(backgroundColor)
        }
        if (!styleInitialized || shapeState != shape) {
            containerBackground.shapeAppearanceModel = shape.toShapeAppearanceModel(layoutDirection, density)
        }

        this.items = items
        this.selectedIndex = resolvedSelectedIndex

        enabledState = enabled
        backgroundColorState = backgroundColor
        indicatorColorState = indicatorColor
        shapeState = shape
        textColorState = textColor
        selectedTextColorState = selectedTextColor
        rippleColorState = rippleColor
        textSizePxState = textSizePx
        fontWeightState = fontWeight
        fontFamilyState = fontFamily
        letterSpacingState = letterSpacingEm
        lineHeightPxState = lineHeightPx
        includeFontPaddingState = includeFontPadding
        paddingHorizontalState = paddingHorizontal
        paddingVerticalState = paddingVertical
        densityState = density
        styleInitialized = true

        when {
            labelsChanged || styleChanged -> updateChildren(
                enabled = enabled,
                indicatorColor = indicatorColor,
                shape = shape,
                textColor = textColor,
                selectedTextColor = selectedTextColor,
                rippleColor = rippleColor,
                textSizePx = textSizePx,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                letterSpacingEm = letterSpacingEm,
                lineHeightPx = lineHeightPx,
                includeFontPadding = includeFontPadding,
                paddingHorizontal = paddingHorizontal,
                paddingVertical = paddingVertical,
            )

            selectedChanged -> updateSelectionOnly(
                previousSelectedIndex = previousSelectedIndex,
                nextSelectedIndex = resolvedSelectedIndex,
                indicatorColor = indicatorColor,
                textColor = textColor,
                selectedTextColor = selectedTextColor,
            )
        }
    }

    private fun rebuild(items: List<SegmentedControlItem>) {
        removeAllViews()
        segmentBackgrounds.clear()
        items.forEachIndexed { index, _ ->
            addView(
                TextView(context).apply {
                    gravity = Gravity.CENTER
                    ellipsize = TextUtils.TruncateAt.END
                    maxLines = 1
                    layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    setOnClickListener {
                        if (isEnabled) {
                            onSelectionChange?.invoke(index)
                        }
                    }
                },
            )
        }
    }

    private fun updateChildren(
        enabled: Boolean,
        indicatorColor: Int,
        shape: UiShape,
        textColor: Int,
        selectedTextColor: Int,
        rippleColor: Int,
        textSizePx: Float,
        fontWeight: Int?,
        fontFamily: UiFontFamily?,
        letterSpacingEm: Float?,
        lineHeightPx: Int?,
        includeFontPadding: Boolean,
        paddingHorizontal: Int,
        paddingVertical: Int,
    ) {
        val insetPx = densityState.roundToPx(indicatorInset)
        for (index in 0 until childCount) {
            val child = getChildAt(index) as? TextView ?: continue
            val item = items.getOrNull(index) ?: continue
            val isSelected = index == selectedIndex
            child.text = item.label
            child.isEnabled = enabled
            ContentViewBinder.applyTextAppearance(
                view = child,
                textColor = if (isSelected) selectedTextColor else textColor,
                textSizePx = textSizePx,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                letterSpacingEm = letterSpacingEm,
                lineHeightPx = lineHeightPx,
                includeFontPadding = includeFontPadding,
            )
            child.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
            val childParams = child.layoutParams as LayoutParams
            if (childParams.leftMargin != insetPx ||
                childParams.topMargin != insetPx ||
                childParams.rightMargin != insetPx ||
                childParams.bottomMargin != insetPx
            ) {
                childParams.leftMargin = insetPx
                childParams.topMargin = insetPx
                childParams.rightMargin = insetPx
                childParams.bottomMargin = insetPx
                child.layoutParams = childParams
            }
            val segmentBackground = createSegmentBackground(
                enabled = enabled,
                selected = isSelected,
                indicatorColor = indicatorColor,
                rippleColor = rippleColor,
                shape = shape.inset(indicatorInset),
            )
            segmentBackgrounds[child] = segmentBackground
            child.background = segmentBackground.drawable
            child.isSelected = isSelected
        }
    }

    private fun updateSelectionOnly(
        previousSelectedIndex: Int,
        nextSelectedIndex: Int,
        indicatorColor: Int,
        textColor: Int,
        selectedTextColor: Int,
    ) {
        updateSelectionAt(
            index = previousSelectedIndex,
            nextSelectedIndex = nextSelectedIndex,
            indicatorColor = indicatorColor,
            textColor = textColor,
            selectedTextColor = selectedTextColor,
        )
        if (nextSelectedIndex != previousSelectedIndex) {
            updateSelectionAt(
                index = nextSelectedIndex,
                nextSelectedIndex = nextSelectedIndex,
                indicatorColor = indicatorColor,
                textColor = textColor,
                selectedTextColor = selectedTextColor,
            )
        }
    }

    private fun updateSelectionAt(
        index: Int,
        nextSelectedIndex: Int,
        indicatorColor: Int,
        textColor: Int,
        selectedTextColor: Int,
    ) {
        if (index !in 0 until childCount) return
        val child = getChildAt(index) as? TextView ?: return
        val isSelected = index == nextSelectedIndex
        val resolvedTextColor = if (isSelected) selectedTextColor else textColor
        if (child.currentTextColor != resolvedTextColor) {
            child.setTextColor(resolvedTextColor)
        }
        val segmentBackground = segmentBackgrounds[child]
            ?: createSegmentBackground(
                enabled = enabledState,
                selected = isSelected,
                indicatorColor = indicatorColor,
                rippleColor = rippleColorState,
                shape = shapeState.inset(indicatorInset),
            ).also { created ->
                segmentBackgrounds[child] = created
                child.background = created.drawable
            }
        segmentBackground.updateIndicator(
            selected = isSelected,
            indicatorColor = indicatorColor,
        )
        child.isSelected = isSelected
    }

    private fun createSegmentBackground(
        enabled: Boolean,
        selected: Boolean,
        indicatorColor: Int,
        rippleColor: Int,
        shape: UiShape,
    ): SegmentBackground {
        val indicator = MaterialShapeDrawable(shape.toShapeAppearanceModel(layoutDirection, densityState)).apply {
            fillColor = ColorStateList.valueOf(
                if (selected) indicatorColor else Color.TRANSPARENT,
            )
        }
        val drawable = if (enabled) {
            RippleDrawable(
                ColorStateList.valueOf(rippleColor),
                indicator,
                MaterialShapeDrawable(shape.toShapeAppearanceModel(layoutDirection, densityState)).apply {
                    fillColor = ColorStateList.valueOf(Color.WHITE)
                },
            )
        } else {
            indicator
        }
        return SegmentBackground(
            drawable = drawable,
            indicator = indicator,
        )
    }

    private class SegmentBackground(
        val drawable: Drawable,
        private val indicator: MaterialShapeDrawable,
    ) {
        /**
         * 只切换 indicator 的填充色，避免重新创建 ripple/background 层。
         * Switches only the indicator fill color, avoiding recreation of ripple/background layers.
         */
        fun updateIndicator(
            selected: Boolean,
            indicatorColor: Int,
        ) {
            indicator.fillColor = ColorStateList.valueOf(
                if (selected) indicatorColor else Color.TRANSPARENT,
            )
        }
    }
}
