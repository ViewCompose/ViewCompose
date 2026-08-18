package com.viewcompose.renderer.view.container

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.viewcompose.ui.modifier.SemanticsCollectionItemInfo
import com.viewcompose.ui.modifier.SemanticsConfiguration
import com.viewcompose.ui.modifier.SemanticsRole
import com.viewcompose.ui.node.NavigationBarItem
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.spec.UiFontFamily
import com.viewcompose.renderer.view.tree.ContentViewBinder
import com.viewcompose.renderer.view.tree.ModifierSemanticsApplier
import com.viewcompose.renderer.view.dpToPx
import com.viewcompose.renderer.view.tree.toColorStateList

/**
 * Android LinearLayout implementation of NavigationBar.
 *
 * Caches each item's child Views and separates structural, style, and selection updates to avoid rebuilding the full bar.
 */
internal class DeclarativeNavigationBarLayout(
    context: Context,
) : LinearLayout(context) {
    /**
     * Child View references and last applied visual state for one navigation item.
     */
    private data class ItemViewRefs(
        val key: Any,
        val root: LinearLayout,
        val indicator: View,
        val indicatorDrawable: GradientDrawable,
        val iconView: ImageView,
        val badgeView: TextView,
        val badgeDrawable: GradientDrawable,
        val labelView: TextView,
        var iconResId: Int,
        var iconTint: Int? = null,
        var indicatorColor: Int? = null,
        var badgeInitialized: Boolean = false,
        var badgeCount: Int? = null,
        var badgeColor: Int? = null,
        var badgeTextColor: Int? = null,
        var stateLayerColors: UiStateLayerColors,
    )

    private var items: List<NavigationBarItem> = emptyList()
    private var selectedIndex: Int = -1
    private var onItemSelected: ((Int) -> Unit)? = null
    private var styleInitialized: Boolean = false
    private var containerColorState: Int = Color.TRANSPARENT
    private var selectedIconColorState: Int = Color.TRANSPARENT
    private var unselectedIconColorState: Int = Color.TRANSPARENT
    private var selectedLabelColorState: Int = Color.TRANSPARENT
    private var unselectedLabelColorState: Int = Color.TRANSPARENT
    private var indicatorColorState: Int = Color.TRANSPARENT
    private var selectedStateLayerColorsState: UiStateLayerColors? = null
    private var unselectedStateLayerColorsState: UiStateLayerColors? = null
    private var iconSizeState: Int = 0
    private var labelSizePxState: Float = 0f
    private var labelFontWeightState: Int? = null
    private var labelFontFamilyState: UiFontFamily? = null
    private var labelLetterSpacingState: Float? = null
    private var labelLineHeightPxState: Int? = null
    private var labelIncludeFontPaddingState: Boolean = false
    private var badgeColorState: Int = Color.TRANSPARENT
    private var badgeTextColorState: Int = Color.TRANSPARENT
    private val itemRefs = mutableListOf<ItemViewRefs>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.BOTTOM
    }

    fun bind(
        items: List<NavigationBarItem>,
        selectedIndex: Int,
        onItemSelected: ((Int) -> Unit)?,
        containerColor: Int,
        selectedIconColor: Int,
        unselectedIconColor: Int,
        selectedLabelColor: Int,
        unselectedLabelColor: Int,
        indicatorColor: Int,
        selectedStateLayerColors: UiStateLayerColors,
        unselectedStateLayerColors: UiStateLayerColors,
        iconSize: Int,
        labelSizePx: Float,
        labelFontWeight: Int?,
        labelFontFamily: UiFontFamily?,
        labelLetterSpacingEm: Float?,
        labelLineHeightPx: Int?,
        labelIncludeFontPadding: Boolean,
        badgeColor: Int,
        badgeTextColor: Int,
    ) {
        val previousItems = this.items
        val previousSelectedIndex = this.selectedIndex
        this.onItemSelected = onItemSelected

        val resolvedSelectedIndex = if (items.isEmpty()) {
            -1
        } else {
            selectedIndex.coerceIn(0, items.lastIndex)
        }
        require(items.map(NavigationBarItem::key).toSet().size == items.size) {
            "NavigationBar item keys must be unique."
        }
        val structureContentChanged = previousItems.map(NavigationBarItem::key) !=
            items.map(NavigationBarItem::key)
        val stateLayersChanged = !styleInitialized ||
            selectedStateLayerColorsState != selectedStateLayerColors ||
            unselectedStateLayerColorsState != unselectedStateLayerColors
        val structureChanged = structureContentChanged || childCount != items.size
        if (structureChanged) {
            reconcileItems(items, unselectedStateLayerColors)
        }
        val styleChanged = !styleInitialized ||
            selectedIconColorState != selectedIconColor ||
            unselectedIconColorState != unselectedIconColor ||
            selectedLabelColorState != selectedLabelColor ||
            unselectedLabelColorState != unselectedLabelColor ||
            indicatorColorState != indicatorColor ||
            iconSizeState != iconSize ||
            labelSizePxState != labelSizePx ||
            labelFontWeightState != labelFontWeight ||
            labelFontFamilyState != labelFontFamily ||
            labelLetterSpacingState != labelLetterSpacingEm ||
            labelLineHeightPxState != labelLineHeightPx ||
            labelIncludeFontPaddingState != labelIncludeFontPadding ||
            badgeColorState != badgeColor ||
            badgeTextColorState != badgeTextColor ||
            stateLayersChanged
        val selectionChanged = previousSelectedIndex != resolvedSelectedIndex
        val contentChangedIndices = if (structureChanged) {
            emptySet()
        } else {
            calculateChangedItemIndices(
                previousItems = previousItems,
                nextItems = items,
            )
        }
        if (!styleInitialized || containerColorState != containerColor) {
            setBackgroundColor(containerColor)
        }

        this.items = items
        this.selectedIndex = resolvedSelectedIndex

        containerColorState = containerColor
        selectedIconColorState = selectedIconColor
        unselectedIconColorState = unselectedIconColor
        selectedLabelColorState = selectedLabelColor
        unselectedLabelColorState = unselectedLabelColor
        indicatorColorState = indicatorColor
        selectedStateLayerColorsState = selectedStateLayerColors
        unselectedStateLayerColorsState = unselectedStateLayerColors
        iconSizeState = iconSize
        labelSizePxState = labelSizePx
        labelFontWeightState = labelFontWeight
        labelFontFamilyState = labelFontFamily
        labelLetterSpacingState = labelLetterSpacingEm
        labelLineHeightPxState = labelLineHeightPx
        labelIncludeFontPaddingState = labelIncludeFontPadding
        badgeColorState = badgeColor
        badgeTextColorState = badgeTextColor
        styleInitialized = true

        when {
            structureChanged || styleChanged -> updateChildren(
                selectedIconColor = selectedIconColor,
                unselectedIconColor = unselectedIconColor,
                selectedLabelColor = selectedLabelColor,
                unselectedLabelColor = unselectedLabelColor,
                indicatorColor = indicatorColor,
                iconSize = iconSize,
                labelSizePx = labelSizePx,
                labelFontWeight = labelFontWeight,
                labelFontFamily = labelFontFamily,
                labelLetterSpacingEm = labelLetterSpacingEm,
                labelLineHeightPx = labelLineHeightPx,
                labelIncludeFontPadding = labelIncludeFontPadding,
                badgeColor = badgeColor,
                badgeTextColor = badgeTextColor,
                applyFullTextAppearance = true,
            )

            selectionChanged || contentChangedIndices.isNotEmpty() -> {
                val indices = linkedSetOf<Int>()
                if (selectionChanged) {
                    indices += previousSelectedIndex
                    indices += resolvedSelectedIndex
                }
                indices += contentChangedIndices
                updateChildrenAt(
                    indices = indices,
                    selectedIconColor = selectedIconColor,
                    unselectedIconColor = unselectedIconColor,
                    selectedLabelColor = selectedLabelColor,
                    unselectedLabelColor = unselectedLabelColor,
                    indicatorColor = indicatorColor,
                    iconSize = iconSize,
                    labelSizePx = labelSizePx,
                    labelFontWeight = labelFontWeight,
                    labelFontFamily = labelFontFamily,
                    labelLetterSpacingEm = labelLetterSpacingEm,
                    labelLineHeightPx = labelLineHeightPx,
                    labelIncludeFontPadding = labelIncludeFontPadding,
                    badgeColor = badgeColor,
                    badgeTextColor = badgeTextColor,
                    applyFullTextAppearance = false,
                )
            }
        }
    }

    private fun reconcileItems(items: List<NavigationBarItem>, stateLayerColors: UiStateLayerColors) {
        val existingByKey = itemRefs.associateBy(ItemViewRefs::key)
        removeAllViews()
        itemRefs.clear()
        items.forEachIndexed { index, item ->
            val refs = existingByKey[item.key] ?: createItemView(index, item, stateLayerColors)
            refs.root.tag = index
            itemRefs += refs
            addView(refs.root)
        }
    }

    private fun createItemView(
        index: Int,
        item: NavigationBarItem,
        stateLayerColors: UiStateLayerColors,
    ): ItemViewRefs {
        val itemLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setPadding(0, context.dpToPx(12), 0, context.dpToPx(16))
            isClickable = true
            isFocusable = true
            tag = index
            foreground = createItemRipple(stateLayerColors)
            setOnClickListener {
                if (isEnabled) {
                    onItemSelected?.invoke(tag as Int)
                }
            }
        }

        // Icon-layer container stacking the pill indicator, icon, and badge.
        val iconContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                context.dpToPx(INDICATOR_WIDTH),
                context.dpToPx(INDICATOR_HEIGHT),
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        // Pill-shaped selection indicator drawn behind the icon.
        val indicatorDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
        }
        val indicator = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                context.dpToPx(INDICATOR_WIDTH),
                context.dpToPx(INDICATOR_HEIGHT),
            ).apply {
                gravity = Gravity.CENTER
            }
            background = indicatorDrawable
        }
        iconContainer.addView(indicator)

        // Navigation icon updated incrementally only when its resource or tint changes.
        val iconView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                context.dpToPx(ICON_SIZE_DEFAULT),
                context.dpToPx(ICON_SIZE_DEFAULT),
            ).apply {
                gravity = Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(item.icon.resId)
        }
        iconContainer.addView(iconView)

        // Optional dot or numeric badge positioned at the icon container's top-right corner.
        val badgeDrawable = GradientDrawable()
        val badgeView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                marginStart = context.dpToPx(INDICATOR_WIDTH / 2)
            }
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            background = badgeDrawable
        }
        iconContainer.addView(badgeView)

        itemLayout.addView(iconContainer)

        // Text label whose complete typography is reapplied only when needed.
        val label = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = context.dpToPx(4)
            }
            this.gravity = Gravity.CENTER
            text = item.label
            maxLines = 1
        }
        itemLayout.addView(label)

        return ItemViewRefs(
            key = item.key,
            root = itemLayout,
            indicator = indicator,
            indicatorDrawable = indicatorDrawable,
            iconView = iconView,
            badgeView = badgeView,
            badgeDrawable = badgeDrawable,
            labelView = label,
            iconResId = item.icon.resId,
            stateLayerColors = stateLayerColors,
        )
    }

    private fun updateChildren(
        selectedIconColor: Int,
        unselectedIconColor: Int,
        selectedLabelColor: Int,
        unselectedLabelColor: Int,
        indicatorColor: Int,
        iconSize: Int,
        labelSizePx: Float,
        labelFontWeight: Int?,
        labelFontFamily: UiFontFamily?,
        labelLetterSpacingEm: Float?,
        labelLineHeightPx: Int?,
        labelIncludeFontPadding: Boolean,
        badgeColor: Int,
        badgeTextColor: Int,
        applyFullTextAppearance: Boolean,
    ) {
        for (index in 0 until itemRefs.size) {
            updateChildAt(
                index = index,
                selectedIconColor = selectedIconColor,
                unselectedIconColor = unselectedIconColor,
                selectedLabelColor = selectedLabelColor,
                unselectedLabelColor = unselectedLabelColor,
                indicatorColor = indicatorColor,
                iconSize = iconSize,
                labelSizePx = labelSizePx,
                labelFontWeight = labelFontWeight,
                labelFontFamily = labelFontFamily,
                labelLetterSpacingEm = labelLetterSpacingEm,
                labelLineHeightPx = labelLineHeightPx,
                labelIncludeFontPadding = labelIncludeFontPadding,
                badgeColor = badgeColor,
                badgeTextColor = badgeTextColor,
                applyFullTextAppearance = applyFullTextAppearance,
            )
        }
    }

    private fun updateChildrenAt(
        indices: Set<Int>,
        selectedIconColor: Int,
        unselectedIconColor: Int,
        selectedLabelColor: Int,
        unselectedLabelColor: Int,
        indicatorColor: Int,
        iconSize: Int,
        labelSizePx: Float,
        labelFontWeight: Int?,
        labelFontFamily: UiFontFamily?,
        labelLetterSpacingEm: Float?,
        labelLineHeightPx: Int?,
        labelIncludeFontPadding: Boolean,
        badgeColor: Int,
        badgeTextColor: Int,
        applyFullTextAppearance: Boolean,
    ) {
        indices.forEach { index ->
            updateChildAt(
                index = index,
                selectedIconColor = selectedIconColor,
                unselectedIconColor = unselectedIconColor,
                selectedLabelColor = selectedLabelColor,
                unselectedLabelColor = unselectedLabelColor,
                indicatorColor = indicatorColor,
                iconSize = iconSize,
                labelSizePx = labelSizePx,
                labelFontWeight = labelFontWeight,
                labelFontFamily = labelFontFamily,
                labelLetterSpacingEm = labelLetterSpacingEm,
                labelLineHeightPx = labelLineHeightPx,
                labelIncludeFontPadding = labelIncludeFontPadding,
                badgeColor = badgeColor,
                badgeTextColor = badgeTextColor,
                applyFullTextAppearance = applyFullTextAppearance,
            )
        }
    }

    private fun updateChildAt(
        index: Int,
        selectedIconColor: Int,
        unselectedIconColor: Int,
        selectedLabelColor: Int,
        unselectedLabelColor: Int,
        indicatorColor: Int,
        iconSize: Int,
        labelSizePx: Float,
        labelFontWeight: Int?,
        labelFontFamily: UiFontFamily?,
        labelLetterSpacingEm: Float?,
        labelLineHeightPx: Int?,
        labelIncludeFontPadding: Boolean,
        badgeColor: Int,
        badgeTextColor: Int,
        applyFullTextAppearance: Boolean,
    ) {
        if (index !in itemRefs.indices) {
            return
        }
        val item = items.getOrNull(index) ?: return
        val isSelected = index == selectedIndex
        val refs = itemRefs[index]
        refs.root.tag = index
        refs.root.isEnabled = item.enabled
        val stateLayerColors = if (isSelected) {
            requireNotNull(selectedStateLayerColorsState)
        } else {
            requireNotNull(unselectedStateLayerColorsState)
        }
        if (refs.stateLayerColors != stateLayerColors) {
            refs.stateLayerColors = stateLayerColors
            val ripple = refs.root.foreground as? RippleDrawable
            if (ripple == null) {
                refs.root.foreground = createItemRipple(stateLayerColors)
            } else {
                // Selection may recompose synchronously from the click callback. Keep the active
                // drawable so its release animation survives while its semantic color changes.
                ripple.setColor(stateLayerColors.toColorStateList())
                refs.root.invalidate()
            }
        }

        ModifierSemanticsApplier.apply(
            view = refs.root,
            semantics = SemanticsConfiguration(
                contentDescription = item.label,
                role = SemanticsRole.Tab,
                collectionItemInfo = SemanticsCollectionItemInfo(
                    rowIndex = 0,
                    columnIndex = index,
                ),
                selected = isSelected,
                enabled = item.enabled,
                mergeDescendants = true,
            ),
        )

        refs.indicator.apply {
            visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
        }
        if (refs.indicatorColor != indicatorColor) {
            refs.indicatorColor = indicatorColor
            refs.indicatorDrawable.setColor(indicatorColor)
            refs.indicatorDrawable.cornerRadius = context.dpToPx(INDICATOR_CORNER_RADIUS).toFloat()
        }

        refs.iconView.apply {
            val selectedIcon = item.selectedIcon
            val iconRes = if (isSelected && selectedIcon != null) {
                selectedIcon.resId
            } else {
                item.icon.resId
            }
            if (refs.iconResId != iconRes) {
                refs.iconResId = iconRes
                setImageResource(iconRes)
            }
            val iconTint = if (isSelected) selectedIconColor else unselectedIconColor
            if (refs.iconTint != iconTint) {
                refs.iconTint = iconTint
                imageTintList = ColorStateList.valueOf(iconTint)
            }
            val size = iconSize.coerceAtLeast(1)
            val currentParams = layoutParams as FrameLayout.LayoutParams
            if (currentParams.width != size || currentParams.height != size) {
                layoutParams = currentParams.apply {
                    width = size
                    height = size
                }
            }
        }

        refs.badgeView.apply {
            val badgeCount = item.badgeCount
            val badgeStateChanged = !refs.badgeInitialized ||
                refs.badgeCount != badgeCount ||
                refs.badgeColor != badgeColor ||
                refs.badgeTextColor != badgeTextColor
            if (!badgeStateChanged) {
                return@apply
            }
            refs.badgeInitialized = true
            refs.badgeCount = badgeCount
            refs.badgeColor = badgeColor
            refs.badgeTextColor = badgeTextColor
            when {
                badgeCount == null -> {
                    visibility = View.GONE
                }
                badgeCount == 0 -> {
                    visibility = View.VISIBLE
                    text = ""
                    minWidth = 0
                    setPadding(0, 0, 0, 0)
                    val dotSize = context.dpToPx(DOT_BADGE_SIZE)
                    val currentParams = layoutParams as FrameLayout.LayoutParams
                    if (currentParams.width != dotSize || currentParams.height != dotSize) {
                        layoutParams = currentParams.apply {
                            width = dotSize
                            height = dotSize
                        }
                    }
                    refs.badgeDrawable.shape = GradientDrawable.OVAL
                    refs.badgeDrawable.cornerRadius = 0f
                    refs.badgeDrawable.setColor(badgeColor)
                }
                else -> {
                    visibility = View.VISIBLE
                    text = if (badgeCount > 99) "99+" else badgeCount.toString()
                    setTextColor(badgeTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, BADGE_TEXT_SIZE_SP)
                    val badgeHeight = context.dpToPx(BADGE_HEIGHT)
                    val hPad = context.dpToPx(BADGE_HORIZONTAL_PADDING)
                    setPadding(hPad, 0, hPad, 0)
                    val currentParams = layoutParams as FrameLayout.LayoutParams
                    if (currentParams.width != ViewGroup.LayoutParams.WRAP_CONTENT || currentParams.height != badgeHeight) {
                        layoutParams = currentParams.apply {
                            width = ViewGroup.LayoutParams.WRAP_CONTENT
                            height = badgeHeight
                        }
                    }
                    minWidth = badgeHeight
                    refs.badgeDrawable.shape = GradientDrawable.RECTANGLE
                    refs.badgeDrawable.setColor(badgeColor)
                    refs.badgeDrawable.cornerRadius = badgeHeight / 2f
                }
            }
        }

        refs.labelView.apply {
            if (text != item.label) {
                text = item.label
            }
            val textColor = if (isSelected) selectedLabelColor else unselectedLabelColor
            if (applyFullTextAppearance) {
                ContentViewBinder.applyTextAppearance(
                    view = this,
                    textColor = textColor,
                    textSizePx = labelSizePx,
                    fontWeight = labelFontWeight,
                    fontFamily = labelFontFamily,
                    letterSpacingEm = labelLetterSpacingEm,
                    lineHeightPx = labelLineHeightPx,
                    includeFontPadding = labelIncludeFontPadding,
                )
            } else if (currentTextColor != textColor) {
                setTextColor(textColor)
            }
        }
    }

    private fun calculateChangedItemIndices(
        previousItems: List<NavigationBarItem>,
        nextItems: List<NavigationBarItem>,
    ): Set<Int> {
        if (previousItems.size != nextItems.size) {
            return nextItems.indices.toSet()
        }
        val indices = linkedSetOf<Int>()
        for (index in nextItems.indices) {
            if (previousItems[index] != nextItems[index]) {
                indices += index
            }
        }
        return indices
    }

    private fun createItemRipple(stateLayerColors: UiStateLayerColors): RippleDrawable {
        return RippleDrawable(
            stateLayerColors.toColorStateList(),
            null,
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
            },
        )
    }

    companion object {
        private const val INDICATOR_WIDTH = 64
        private const val INDICATOR_HEIGHT = 32
        private const val INDICATOR_CORNER_RADIUS = 16
        private const val ICON_SIZE_DEFAULT = 24
        private const val DOT_BADGE_SIZE = 6
        private const val BADGE_HEIGHT = 16
        private const val BADGE_HORIZONTAL_PADDING = 4
        private const val BADGE_TEXT_SIZE_SP = 10f
    }
}
