package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.viewcompose.renderer.view.shape.UiShapeDrawable
import com.viewcompose.renderer.view.tree.ContentViewBinder
import com.viewcompose.renderer.view.tree.ModifierSemanticsApplier
import com.viewcompose.renderer.view.tree.toColorStateList
import com.viewcompose.ui.modifier.SemanticsCollectionItemInfo
import com.viewcompose.ui.modifier.SemanticsConfiguration
import com.viewcompose.ui.modifier.SemanticsRole
import com.viewcompose.ui.node.SegmentedControlItem
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.spec.UiFontFamily
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.dp
import java.util.IdentityHashMap

/**
 * Android LinearLayout implementation of SegmentedControl.
 *
 * Uses one TextView per segment and caches backgrounds so selection updates change only the
 * indicator.
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
    private var unselectedStateLayerColorsState: UiStateLayerColors? = null
    private var selectedStateLayerColorsState: UiStateLayerColors? = null
    private var textSizePxState: Float = 14f
    private var fontWeightState: Int? = null
    private var fontFamilyState: UiFontFamily? = null
    private var letterSpacingState: Float? = null
    private var lineHeightPxState: Int? = null
    private var includeFontPaddingState: Boolean = false
    private var paddingHorizontalState: Int = 0
    private var paddingVerticalState: Int = 0
    private var densityState: UiDensity = UiDensity.Default
    private var layoutDirectionState: Int = layoutDirection
    private val indicatorInset = 2.dp
    private var containerBackground = UiShapeDrawable(shapeState, layoutDirection, densityState)
    // Identity mapping prevents equal TextView labels from accidentally sharing background state.
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
        unselectedStateLayerColors: UiStateLayerColors,
        selectedStateLayerColors: UiStateLayerColors,
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
        require(items.map(SegmentedControlItem::key).toSet().size == items.size) {
            "SegmentedControl item keys must be unique."
        }
        val structureChanged = this.items.map(SegmentedControlItem::key) !=
            items.map(SegmentedControlItem::key) || childCount != items.size
        val contentChanged = this.items != items
        if (structureChanged) {
            reconcileItems(items)
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
            unselectedStateLayerColorsState != unselectedStateLayerColors ||
            selectedStateLayerColorsState != selectedStateLayerColors ||
            textSizePxState != textSizePx ||
            fontWeightState != fontWeight ||
            fontFamilyState != fontFamily ||
            letterSpacingState != letterSpacingEm ||
            lineHeightPxState != lineHeightPx ||
            includeFontPaddingState != includeFontPadding ||
            paddingHorizontalState != paddingHorizontal ||
            paddingVerticalState != paddingVertical ||
            densityState != density ||
            layoutDirectionState != layoutDirection

        val shapeEnvironmentChanged = densityState != density ||
            layoutDirectionState != layoutDirection
        if (shapeEnvironmentChanged) {
            containerBackground = UiShapeDrawable(shape, layoutDirection, density)
            background = containerBackground
        } else if (background !== containerBackground) {
            background = containerBackground
        }

        if (shapeEnvironmentChanged || !styleInitialized || backgroundColorState != backgroundColor) {
            containerBackground.setFillColor(backgroundColor)
        }
        if (!shapeEnvironmentChanged && (!styleInitialized || shapeState != shape)) {
            containerBackground.setShape(shape)
        }

        this.items = items
        this.selectedIndex = resolvedSelectedIndex

        enabledState = enabled
        backgroundColorState = backgroundColor
        indicatorColorState = indicatorColor
        shapeState = shape
        textColorState = textColor
        selectedTextColorState = selectedTextColor
        unselectedStateLayerColorsState = unselectedStateLayerColors
        selectedStateLayerColorsState = selectedStateLayerColors
        textSizePxState = textSizePx
        fontWeightState = fontWeight
        fontFamilyState = fontFamily
        letterSpacingState = letterSpacingEm
        lineHeightPxState = lineHeightPx
        includeFontPaddingState = includeFontPadding
        paddingHorizontalState = paddingHorizontal
        paddingVerticalState = paddingVertical
        densityState = density
        layoutDirectionState = layoutDirection
        styleInitialized = true

        when {
            structureChanged || contentChanged || styleChanged -> updateChildren(
                enabled = enabled,
                indicatorColor = indicatorColor,
                shape = shape,
                textColor = textColor,
                selectedTextColor = selectedTextColor,
                unselectedStateLayerColors = unselectedStateLayerColors,
                selectedStateLayerColors = selectedStateLayerColors,
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

    private fun reconcileItems(items: List<SegmentedControlItem>) {
        val existingByKey = this.items.indices.associate { index ->
            this.items[index].key to (getChildAt(index) as TextView)
        }
        removeAllViews()
        val retainedViews = linkedSetOf<TextView>()
        items.forEachIndexed { index, item ->
            val child = existingByKey[item.key] ?: TextView(context).apply {
                gravity = Gravity.CENTER
                ellipsize = TextUtils.TruncateAt.END
                maxLines = 1
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener {
                    if (isEnabled) {
                        onSelectionChange?.invoke(tag as Int)
                    }
                }
            }
            child.tag = index
            retainedViews += child
            addView(child)
        }
        segmentBackgrounds.keys.retainAll(retainedViews)
    }

    private fun updateChildren(
        enabled: Boolean,
        indicatorColor: Int,
        shape: UiShape,
        textColor: Int,
        selectedTextColor: Int,
        unselectedStateLayerColors: UiStateLayerColors,
        selectedStateLayerColors: UiStateLayerColors,
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
            val itemEnabled = enabled && item.enabled
            child.tag = index
            child.text = item.label
            child.isEnabled = itemEnabled
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
                enabled = itemEnabled,
                selected = isSelected,
                indicatorColor = indicatorColor,
                stateLayerColors = if (isSelected) {
                    selectedStateLayerColors
                } else {
                    unselectedStateLayerColors
                },
                shape = shape.inset(indicatorInset),
            )
            segmentBackgrounds[child] = segmentBackground
            child.background = segmentBackground.drawable
            child.isSelected = isSelected
            applyItemSemantics(child, index, isSelected, itemEnabled)
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
        val itemEnabled = enabledState && items.getOrNull(index)?.enabled == true
        val resolvedTextColor = if (isSelected) selectedTextColor else textColor
        if (child.currentTextColor != resolvedTextColor) {
            child.setTextColor(resolvedTextColor)
        }
        val stateRolesDiffer = selectedStateLayerColorsState != unselectedStateLayerColorsState
        if (stateRolesDiffer) {
            val segmentBackground = createSegmentBackground(
                enabled = itemEnabled,
                selected = isSelected,
                indicatorColor = indicatorColor,
                stateLayerColors = requireNotNull(if (isSelected) {
                    selectedStateLayerColorsState
                } else {
                    unselectedStateLayerColorsState
                }),
                shape = shapeState.inset(indicatorInset),
            )
            segmentBackgrounds[child] = segmentBackground
            child.background = segmentBackground.drawable
        } else {
            val segmentBackground = segmentBackgrounds[child]
                ?: createSegmentBackground(
                    enabled = itemEnabled,
                    selected = isSelected,
                    indicatorColor = indicatorColor,
                    stateLayerColors = requireNotNull(selectedStateLayerColorsState),
                    shape = shapeState.inset(indicatorInset),
                ).also { created ->
                    segmentBackgrounds[child] = created
                    child.background = created.drawable
                }
            segmentBackground.updateIndicator(
                selected = isSelected,
                indicatorColor = indicatorColor,
            )
        }
        child.isSelected = isSelected
        applyItemSemantics(child, index, isSelected, itemEnabled)
    }

    private fun applyItemSemantics(
        child: TextView,
        index: Int,
        selected: Boolean,
        enabled: Boolean,
    ) {
        ModifierSemanticsApplier.apply(
            view = child,
            semantics = SemanticsConfiguration(
                role = SemanticsRole.Tab,
                collectionItemInfo = SemanticsCollectionItemInfo(
                    rowIndex = 0,
                    columnIndex = index,
                ),
                selected = selected,
                enabled = enabled,
            ),
        )
    }

    private fun createSegmentBackground(
        enabled: Boolean,
        selected: Boolean,
        indicatorColor: Int,
        stateLayerColors: UiStateLayerColors,
        shape: UiShape,
    ): SegmentBackground {
        val indicator = UiShapeDrawable(shape, layoutDirection, densityState).apply {
            setFillColor(
                if (selected) indicatorColor else Color.TRANSPARENT,
            )
        }
        val drawable = if (enabled) {
            RippleDrawable(
                stateLayerColors.toColorStateList(),
                indicator,
                UiShapeDrawable(shape, layoutDirection, densityState).apply {
                    setFillColor(Color.WHITE)
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
        private val indicator: UiShapeDrawable,
    ) {
        fun updateIndicator(
            selected: Boolean,
            indicatorColor: Int,
        ) {
            indicator.setFillColor(
                if (selected) indicatorColor else Color.TRANSPARENT,
            )
        }
    }
}
