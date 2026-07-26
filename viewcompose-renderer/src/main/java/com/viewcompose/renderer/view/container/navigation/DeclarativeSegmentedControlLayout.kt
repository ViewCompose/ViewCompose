package com.viewcompose.renderer.view.container

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
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
import com.viewcompose.renderer.view.dpToPx

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
    private var shapeState: UiShape = UiShape.rounded(0)
    private var textColorState: Int = Color.BLACK
    private var selectedTextColorState: Int = Color.WHITE
    private var rippleColorState: Int = Color.TRANSPARENT
    private var textSizeSpState: Int = 14
    private var fontWeightState: Int? = null
    private var fontFamilyState: UiFontFamily? = null
    private var letterSpacingState: Float? = null
    private var lineHeightSpState: Int? = null
    private var includeFontPaddingState: Boolean = false
    private var paddingHorizontalState: Int = 0
    private var paddingVerticalState: Int = 0
    private val indicatorInset = context.dpToPx(2).toFloat()
    private val containerBackground = MaterialShapeDrawable()

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
        textSizeSp: Int,
        fontWeight: Int?,
        fontFamily: UiFontFamily?,
        letterSpacingEm: Float?,
        lineHeightSp: Int?,
        includeFontPadding: Boolean,
        paddingHorizontal: Int,
        paddingVertical: Int,
    ) {
        this.onSelectionChange = onSelectionChange
        val labelsChanged = this.items.map { it.label } != items.map { it.label }
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
            textSizeSpState != textSizeSp ||
            fontWeightState != fontWeight ||
            fontFamilyState != fontFamily ||
            letterSpacingState != letterSpacingEm ||
            lineHeightSpState != lineHeightSp ||
            includeFontPaddingState != includeFontPadding ||
            paddingHorizontalState != paddingHorizontal ||
            paddingVerticalState != paddingVertical

        if (!styleInitialized || backgroundColorState != backgroundColor) {
            containerBackground.fillColor = ColorStateList.valueOf(backgroundColor)
        }
        if (!styleInitialized || shapeState != shape) {
            containerBackground.shapeAppearanceModel = shape.toShapeAppearanceModel(layoutDirection)
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
        textSizeSpState = textSizeSp
        fontWeightState = fontWeight
        fontFamilyState = fontFamily
        letterSpacingState = letterSpacingEm
        lineHeightSpState = lineHeightSp
        includeFontPaddingState = includeFontPadding
        paddingHorizontalState = paddingHorizontal
        paddingVerticalState = paddingVertical
        styleInitialized = true

        when {
            labelsChanged || styleChanged -> updateChildren(
                enabled = enabled,
                indicatorColor = indicatorColor,
                shape = shape,
                textColor = textColor,
                selectedTextColor = selectedTextColor,
                rippleColor = rippleColor,
                textSizeSp = textSizeSp,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                letterSpacingEm = letterSpacingEm,
                lineHeightSp = lineHeightSp,
                includeFontPadding = includeFontPadding,
                paddingHorizontal = paddingHorizontal,
                paddingVertical = paddingVertical,
            )

            selectedChanged -> updateSelectionOnly(
                previousSelectedIndex = previousSelectedIndex,
                nextSelectedIndex = resolvedSelectedIndex,
                enabled = enabled,
                indicatorColor = indicatorColor,
                shape = shape,
                textColor = textColor,
                selectedTextColor = selectedTextColor,
                rippleColor = rippleColor,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                letterSpacingEm = letterSpacingEm,
                lineHeightSp = lineHeightSp,
                includeFontPadding = includeFontPadding,
            )
        }
    }

    private fun rebuild(items: List<SegmentedControlItem>) {
        removeAllViews()
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
        textSizeSp: Int,
        fontWeight: Int?,
        fontFamily: UiFontFamily?,
        letterSpacingEm: Float?,
        lineHeightSp: Int?,
        includeFontPadding: Boolean,
        paddingHorizontal: Int,
        paddingVertical: Int,
    ) {
        val insetPx = indicatorInset.toInt()
        for (index in 0 until childCount) {
            val child = getChildAt(index) as? TextView ?: continue
            val item = items.getOrNull(index) ?: continue
            val isSelected = index == selectedIndex
            child.text = item.label
            child.isEnabled = enabled
            ContentViewBinder.applyTextAppearance(
                view = child,
                textColor = if (isSelected) selectedTextColor else textColor,
                textSizeSp = textSizeSp,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                letterSpacingEm = letterSpacingEm,
                lineHeightSp = lineHeightSp,
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
            child.background = createSegmentBackground(
                enabled = enabled,
                selected = isSelected,
                indicatorColor = indicatorColor,
                rippleColor = rippleColor,
                shape = shape.inset(indicatorInset.toInt()),
            )
            child.isSelected = isSelected
        }
    }

    private fun updateSelectionOnly(
        previousSelectedIndex: Int,
        nextSelectedIndex: Int,
        enabled: Boolean,
        indicatorColor: Int,
        shape: UiShape,
        textColor: Int,
        selectedTextColor: Int,
        rippleColor: Int,
        fontWeight: Int?,
        fontFamily: UiFontFamily?,
        letterSpacingEm: Float?,
        lineHeightSp: Int?,
        includeFontPadding: Boolean,
    ) {
        val indices = linkedSetOf(previousSelectedIndex, nextSelectedIndex)
        val indicatorShape = shape.inset(indicatorInset.toInt())
        indices.forEach { index ->
            if (index !in 0 until childCount) return@forEach
            val child = getChildAt(index) as? TextView ?: return@forEach
            val isSelected = index == nextSelectedIndex
            ContentViewBinder.applyTextAppearance(
                view = child,
                textColor = if (isSelected) selectedTextColor else textColor,
                textSizeSp = textSizeSpState,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                letterSpacingEm = letterSpacingEm,
                lineHeightSp = lineHeightSp,
                includeFontPadding = includeFontPadding,
            )
            child.background = createSegmentBackground(
                enabled = enabled,
                selected = isSelected,
                indicatorColor = indicatorColor,
                rippleColor = rippleColor,
                shape = indicatorShape,
            )
            child.isSelected = isSelected
        }
    }

    private fun createSegmentBackground(
        enabled: Boolean,
        selected: Boolean,
        indicatorColor: Int,
        rippleColor: Int,
        shape: UiShape,
    ) = if (enabled) {
        RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            MaterialShapeDrawable(shape.toShapeAppearanceModel(layoutDirection)).apply {
                fillColor = ColorStateList.valueOf(
                    if (selected) indicatorColor else Color.TRANSPARENT,
                )
            },
            MaterialShapeDrawable(shape.toShapeAppearanceModel(layoutDirection)).apply {
                fillColor = ColorStateList.valueOf(Color.WHITE)
            },
        )
    } else {
        MaterialShapeDrawable(shape.toShapeAppearanceModel(layoutDirection)).apply {
            fillColor = ColorStateList.valueOf(
                if (selected) indicatorColor else Color.TRANSPARENT,
            )
        }
    }
}
