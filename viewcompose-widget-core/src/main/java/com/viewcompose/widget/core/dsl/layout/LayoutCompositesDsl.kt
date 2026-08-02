package com.viewcompose.widget.core

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.border
import com.viewcompose.ui.modifier.clip
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.elevation
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.minHeight
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.unit.UiDp

/**
 * Composite Card built from Box and Surface-like style tokens.
 */
fun UiTreeBuilder.Card(
    onClick: (() -> Unit)? = null,
    variant: CardVariant = CardVariant.Filled,
    enabled: Boolean = true,
    key: Any? = null,
    modifier: Modifier = Modifier,
    content: BoxScope.() -> Unit,
) {
    val bgColor = CardDefaults.containerColor(variant)
    val shape = CardDefaults.shape()
    val elev = CardDefaults.elevation(variant)
    val bw = CardDefaults.borderWidth(variant)
    val bc = CardDefaults.borderColor(variant)
    val semanticModifier = Modifier
        .backgroundColor(bgColor)
        .shape(shape)
        .clip()
        .let { m -> if (elev > UiDp.Zero) m.elevation(elev) else m }
        .let { m -> if (bw > UiDp.Zero) m.border(bw, bc) else m }
        .let { m ->
            if (enabled && onClick != null) {
                m.clickable(onClick)
            } else {
                m
            }
        }
        .then(modifier)
    ProvideLocal(LocalContentColor, CardDefaults.contentColor()) {
        Box(
            key = key,
            rippleColor = if (enabled && onClick != null) CardDefaults.pressedColor() else null,
            modifier = semanticModifier,
            content = content,
        )
    }
}

/**
 * Elevated convenience variant of Card.
 */
fun UiTreeBuilder.ElevatedCard(
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    key: Any? = null,
    modifier: Modifier = Modifier,
    content: BoxScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        variant = CardVariant.Elevated,
        enabled = enabled,
        key = key,
        modifier = modifier,
        content = content,
    )
}

/**
 * Outlined convenience variant of Card.
 */
fun UiTreeBuilder.OutlinedCard(
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    key: Any? = null,
    modifier: Modifier = Modifier,
    content: BoxScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        variant = CardVariant.Outlined,
        enabled = enabled,
        key = key,
        modifier = modifier,
        content = content,
    )
}

/**
 * Standard list item composite.
 */
fun UiTreeBuilder.ListItem(
    headlineText: String,
    supportingText: String? = null,
    overlineText: String? = null,
    leadingContent: (UiTreeBuilder.() -> Unit)? = null,
    trailingContent: (UiTreeBuilder.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val hPadding = ListItemDefaults.horizontalPadding()
    val vPadding = ListItemDefaults.verticalPadding()
    val semanticModifier = Modifier
        .fillMaxWidth()
        .minHeight(ListItemDefaults.minHeight())
        .padding(horizontal = hPadding, vertical = vPadding)
        .let { m ->
            if (onClick != null) {
                m.clickable(onClick)
            } else {
                m
            }
        }
        .then(modifier)
    Row(
        key = key,
        spacing = ListItemDefaults.leadingTrailingSpacing(),
        verticalAlignment = VerticalAlignment.Center,
        modifier = semanticModifier,
    ) {
        if (leadingContent != null) {
            leadingContent()
        }
        Column(
            spacing = ListItemDefaults.textSpacing(),
            modifier = Modifier.weight(1f),
        ) {
            if (overlineText != null) {
                Text(
                    text = overlineText,
                    style = ListItemDefaults.overlineStyle(),
                    color = ListItemDefaults.overlineColor(),
                    maxLines = 1,
                )
            }
            Text(
                text = headlineText,
                style = ListItemDefaults.headlineStyle(),
                color = ListItemDefaults.headlineColor(),
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = ListItemDefaults.supportingStyle(),
                    color = ListItemDefaults.supportingColor(),
                )
            }
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

/**
 * Page scaffold composite that arranges top/content/fab/bottom regions.
 */
fun UiTreeBuilder.Scaffold(
    topBar: (UiTreeBuilder.() -> Unit)? = null,
    bottomBar: (UiTreeBuilder.() -> Unit)? = null,
    floatingActionButton: (UiTreeBuilder.() -> Unit)? = null,
    containerColor: Int = ScaffoldDefaults.containerColor(),
    contentColor: Int = ScaffoldDefaults.contentColor(),
    key: Any? = null,
    modifier: Modifier = Modifier,
    content: BoxScope.() -> Unit,
) {
    ProvideLocal(LocalContentColor, contentColor) {
        Column(
            key = key,
            modifier = Modifier
                .fillMaxSize()
                .backgroundColor(containerColor)
                .then(modifier),
        ) {
            if (topBar != null) {
                topBar()
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                content()
                if (floatingActionButton != null) {
                    Box(
                        contentAlignment = BoxAlignment.BottomEnd,
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                    ) {
                        floatingActionButton()
                    }
                }
            }
            if (bottomBar != null) {
                bottomBar()
            }
        }
    }
}
