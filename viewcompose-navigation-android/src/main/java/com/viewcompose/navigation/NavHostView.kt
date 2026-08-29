package com.viewcompose.navigation

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import com.viewcompose.navigation.core.NavPaneRole
import java.util.IdentityHashMap

/**
 * Native View container for NavHost, responsible for pane measurement and layout of destination
 * child Views.
 */
internal class NavHostView(
    context: Context,
) : FrameLayout(context) {
    internal var runtime: NavHostRuntime? = null
    internal var paneSpacingPixels: Int = 0
        set(value) {
            require(value >= 0) {
                "Navigation pane spacing must be non-negative."
            }
            if (field != value) {
                field = value
                requestLayout()
            }
        }
    private val destinationLayouts = IdentityHashMap<View, NavDestinationLayout>()

    init {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
        )
        clipChildren = true
        clipToPadding = true
    }

    internal fun updateDestinationLayouts(layouts: Map<View, NavDestinationLayout>) {
        check(layouts.keys.all { child -> child.parent === this }) {
            "Every destination layout must reference a child of this navigation host."
        }
        destinationLayouts.clear()
        destinationLayouts.putAll(layouts)
        requestLayout()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val measuredWidth = getDefaultSize(suggestedMinimumWidth, widthMeasureSpec)
        val measuredHeight = getDefaultSize(suggestedMinimumHeight, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
        val availableWidth = (measuredWidth - paddingLeft - paddingRight).coerceAtLeast(0)
        val availableHeight = (measuredHeight - paddingTop - paddingBottom).coerceAtLeast(0)
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) {
                continue
            }
            val bounds = resolveDestinationHorizontalBounds(
                availableWidth = availableWidth,
                destinationLayout = destinationLayouts[child]
                    ?: NavDestinationLayout.Content(NavPaneLayout.Single),
            )
            child.measure(
                MeasureSpec.makeMeasureSpec(bounds.width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val availableWidth = (right - left - paddingLeft - paddingRight).coerceAtLeast(0)
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) {
                continue
            }
            val bounds = resolveDestinationHorizontalBounds(
                availableWidth = availableWidth,
                destinationLayout = destinationLayouts[child]
                    ?: NavDestinationLayout.Content(NavPaneLayout.Single),
            )
            val childLeft = paddingLeft + bounds.left
            child.layout(
                childLeft,
                paddingTop,
                paddingLeft + bounds.right,
                bottom - top - paddingBottom,
            )
        }
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width != oldWidth) {
            runtime?.onHostWidthChanged(width)
        }
    }

    private fun resolveDestinationHorizontalBounds(
        availableWidth: Int,
        destinationLayout: NavDestinationLayout,
    ): NavPaneHorizontalBounds {
        return when (destinationLayout) {
            is NavDestinationLayout.Content -> resolvePaneHorizontalBounds(
                availableWidth = availableWidth,
                paneLayout = destinationLayout.pane,
                paneSpacingPixels = paneSpacingPixels,
                layoutDirection = layoutDirection,
            )
            NavDestinationLayout.Overlay -> NavPaneHorizontalBounds(0, availableWidth)
        }
    }

}

/**
 * Converts a logical pane role into horizontal pixel bounds for the current layout direction.
 */
internal fun resolvePaneHorizontalBounds(
    availableWidth: Int,
    paneLayout: NavPaneLayout,
    paneSpacingPixels: Int,
    layoutDirection: Int,
): NavPaneHorizontalBounds {
    require(availableWidth >= 0)
    require(paneSpacingPixels >= 0)
    val paneCount = paneLayout.paneCount
    val totalSpacing = paneSpacingPixels * (paneCount - 1)
    val contentWidth = (availableWidth - totalSpacing).coerceAtLeast(0)
    val baseWidth = contentWidth / paneCount
    val remainder = contentWidth % paneCount
    val logicalIndex = paneLayout.role.ordinal
    val physicalIndex = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
        paneCount - logicalIndex - 1
    } else {
        logicalIndex
    }
    val paneLeft = physicalIndex * (baseWidth + paneSpacingPixels) +
        minOf(physicalIndex, remainder)
    val paneWidth = baseWidth + if (physicalIndex < remainder) 1 else 0
    return NavPaneHorizontalBounds(
        left = paneLeft,
        right = paneLeft + paneWidth,
    )
}

/**
 * Horizontal bounds of one pane inside the host content area.
 */
internal data class NavPaneHorizontalBounds(
    val left: Int,
    val right: Int,
) {
    val width: Int
        get() = right - left

    init {
        require(left >= 0 && right >= left)
    }
}

/**
 * Layout role of one destination in the current pane scene.
 */
internal data class NavPaneLayout(
    val role: NavPaneRole,
    val paneCount: Int,
) {
    init {
        require(paneCount in 1..3) {
            "Navigation pane layout count must be between 1 and 3."
        }
        require(role.ordinal < paneCount) {
            "Navigation pane role $role is outside a $paneCount-pane layout."
        }
    }

    companion object {
        /**
         * Single-pane layout where the destination fills the host.
         */
        val Single = NavPaneLayout(
            role = NavPaneRole.Primary,
            paneCount = 1,
        )
    }
}

/** Native bounds and surface role for one visible navigation destination. */
internal sealed interface NavDestinationLayout {
    /** Opaque content destination constrained to one logical pane. */
    data class Content(
        val pane: NavPaneLayout,
    ) : NavDestinationLayout

    /** Transparent modal destination filling the complete host content area. */
    data object Overlay : NavDestinationLayout
}

/**
 * Destination surface that enforces the execution plan's input-ownership decision.
 *
 * Returning handled while input is disabled prevents an event from falling through to a
 * transitioning sibling. System Back remains owned by the host runtime and does not depend on a
 * destination's key dispatch.
 */
internal class NavDestinationContainer(
    context: Context,
    private val contentSurfaceBackground: Drawable?,
) : FrameLayout(context) {
    internal var acceptsNavigationInput: Boolean = true
    private var blocksSiblingInput: Boolean = false

    internal fun applyDestinationLayout(layout: NavDestinationLayout) {
        blocksSiblingInput = layout == NavDestinationLayout.Overlay
        background = if (blocksSiblingInput) null else contentSurfaceBackground
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return if (acceptsNavigationInput) {
            super.dispatchTouchEvent(event) || blocksSiblingInput
        } else {
            true
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        return if (acceptsNavigationInput) {
            super.dispatchGenericMotionEvent(event) || blocksSiblingInput
        } else {
            true
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return if (acceptsNavigationInput) {
            super.dispatchKeyEvent(event) || blocksSiblingInput
        } else {
            true
        }
    }
}

/**
 * Creates a destination root container and inherits the host theme background as its surface.
 */
internal fun destinationContainer(context: Context): NavDestinationContainer {
    return NavDestinationContainer(
        context = context,
        contentSurfaceBackground = resolveDestinationSurfaceBackground(context),
    ).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        clipChildren = true
        clipToPadding = true
        applyDestinationLayout(NavDestinationLayout.Content(NavPaneLayout.Single))
    }
}

private fun resolveDestinationSurfaceBackground(context: Context): Drawable? {
    return resolveThemeDrawable(context, android.R.attr.colorBackground)
        ?: resolveThemeDrawable(context, android.R.attr.windowBackground)
}

private fun resolveThemeDrawable(
    context: Context,
    @AttrRes attribute: Int,
): Drawable? {
    val value = TypedValue()
    if (!context.theme.resolveAttribute(attribute, value, true)) {
        return null
    }
    return when {
        value.resourceId != 0 -> context.getDrawable(value.resourceId)?.mutate()
        value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT ->
            ColorDrawable(value.data)
        else -> null
    }
}
