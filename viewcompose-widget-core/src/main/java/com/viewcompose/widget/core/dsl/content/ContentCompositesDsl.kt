package com.viewcompose.widget.core

import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.clip
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.minWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.size

/**
 * Composite Badge; null count renders a dot, and values above 99 render 99+.
 */
fun UiTreeBuilder.Badge(
    count: Int? = null,
    containerColor: Int = BadgeDefaults.containerColor(),
    contentColor: Int = BadgeDefaults.contentColor(),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    if (count != null && count <= 0) return
    if (count == null) {
        val dotSize = BadgeDefaults.dotSize()
        Box(
            key = key,
            modifier = Modifier
                .size(width = dotSize, height = dotSize)
                .backgroundColor(containerColor)
                .cornerRadius(dotSize / 2)
                .clip()
                .then(modifier),
        ) {}
    } else {
        val displayText = if (count > 99) "99+" else count.toString()
        val pillHeight = BadgeDefaults.pillHeight()
        val hPadding = BadgeDefaults.pillHorizontalPadding()
        val style = BadgeDefaults.textStyle()
        Box(
            key = key,
            contentAlignment = BoxAlignment.Center,
            modifier = Modifier
                .height(pillHeight)
                .minWidth(BadgeDefaults.pillMinWidth())
                .backgroundColor(containerColor)
                .cornerRadius(pillHeight / 2)
                .clip()
                .padding(horizontal = hPadding)
                .then(modifier),
        ) {
            Text(
                text = displayText,
                style = style,
                color = contentColor,
            )
        }
    }
}

/**
 * Box composite with a badge anchored to the top end.
 */
fun UiTreeBuilder.BadgedBox(
    badge: UiTreeBuilder.() -> Unit,
    key: Any? = null,
    modifier: Modifier = Modifier,
    content: BoxScope.() -> Unit,
) {
    Box(key = key, modifier = modifier) {
        content()
        Box(
            modifier = Modifier.align(BoxAlignment.TopEnd),
        ) {
            badge()
        }
    }
}
