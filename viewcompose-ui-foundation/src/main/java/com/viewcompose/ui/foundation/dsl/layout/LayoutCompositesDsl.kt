package com.viewcompose.ui.foundation

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.border
import com.viewcompose.ui.modifier.clip
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.elevation
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.minHeight
import com.viewcompose.ui.modifier.interactionIndication
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.node.UiInteractionIndication
import com.viewcompose.ui.unit.UiDp

/**
 * Creates a themed card whose [variant] selects fill, border, and elevation roles.
 *
 * A non-null [onClick] makes the complete card one interaction target. The resolved indication is
 * installed only while enabled, and caller modifiers are applied after component appearance.
 *
 * @sample com.viewcompose.ui.foundation.samples.layoutDslSample
 * @receiver active tree builder receiving the card composite
 * @param onClick optional callback invoked synchronously for an accepted card click
 * @param variant semantic filled, elevated, or outlined appearance
 * @param enabled whether the optional action accepts input and displays enabled feedback
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered caller configuration applied after card appearance and behavior
 * @param content children emitted synchronously inside the card box scope
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
                m.interactionIndication(
                    UiInteractionIndication.StateLayer(stateLayerColorsFor(CardDefaults.contentColor())),
                ).clickable(onClick)
            } else {
                m
            }
        }
        .then(modifier)
    ProvideLocal(LocalContentColor, CardDefaults.contentColor()) {
        Box(
            key = key,
            modifier = semanticModifier,
            content = content,
        )
    }
}

/**
 * Creates one structured list row with optional leading, supporting, and trailing regions.
 *
 * A non-null [onClick] makes the entire row clickable. This eager composite does not virtualize
 * itself; place it in a keyed lazy-item session for large collections.
 *
 * @sample com.viewcompose.ui.foundation.samples.layoutDslSample
 * @receiver active tree builder receiving the list-item composite
 * @param headlineText primary label displayed in the central text column
 * @param supportingText optional secondary label below the headline
 * @param overlineText optional compact label above the headline
 * @param leadingContent optional content emitted before the text column
 * @param trailingContent optional content emitted after the text column
 * @param onClick optional callback invoked synchronously for an accepted row click
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered caller configuration applied after list-item sizing and behavior
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
                m.interactionIndication(
                    UiInteractionIndication.StateLayer(stateLayerColorsFor(Theme.colors.onSurface)),
                ).clickable(onClick)
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
 * Arranges optional app bars, body content, and a floating action region into one page.
 *
 * The body fills the space left between bars. The floating action content overlays its bottom-end
 * corner and does not reduce body constraints. This eager layout does not own navigation or inset
 * policy; callers apply those concerns through the supplied regions and [modifier].
 *
 * @sample com.viewcompose.ui.foundation.samples.layoutDslSample
 * @receiver active tree builder receiving the scaffold composite
 * @param topBar optional content occupying the page's top region
 * @param bottomBar optional content occupying the page's bottom region
 * @param floatingActionButton optional content overlaid at the body's bottom-end corner
 * @param containerColor packed ARGB fill for the full scaffold bounds
 * @param contentColor packed ARGB value provided to all scaffold regions
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered caller configuration applied to the full scaffold container
 * @param content body content emitted synchronously into the central box scope
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
