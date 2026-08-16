package com.viewcompose.ui.foundation

import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.alpha
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.clip
import com.viewcompose.ui.modifier.elevation
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.interactionIndication
import com.viewcompose.ui.modifier.minWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.modifier.width
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.UiInteractionIndication
import com.viewcompose.ui.unit.UiDp

/**
 * Emits a standard confirmation dialog composite.
 *
 * AlertDialog reuses the raw [Dialog] lifecycle while owning its surface, title, body, optional
 * icon, and actions. Appearance resolves from [AlertDialogDefaults], nested
 * [ProvideAlertDialogOverrides] scopes, and instance [overrides].
 *
 * @sample com.viewcompose.ui.foundation.samples.remainingComponentOverridesSample
 * @receiver active tree builder submitting the dialog request
 * @param visible whether this render keeps the dialog request active
 * @param title dialog title text
 * @param text supporting text
 * @param confirmButtonText label for the required confirmation action
 * @param onConfirm callback invoked synchronously when confirmation is clicked
 * @param dismissButtonText optional label for a secondary dismiss action
 * @param onDismiss callback paired with [dismissButtonText]
 * @param icon optional decorative leading icon
 * @param overrides sparse instance appearance applied after scoped AlertDialog overrides
 * @param requestKey stable request identity within the current render session
 * @param dismissOnBackPress whether platform Back requests dismissal
 * @param dismissOnClickOutside whether an outside click requests dismissal
 * @param onDismissRequest callback invoked by platform dismissal; the owner removes [visible]
 */
fun UiTreeBuilder.AlertDialog(
    visible: Boolean,
    title: String,
    text: String,
    confirmButtonText: String,
    onConfirm: () -> Unit,
    dismissButtonText: String? = null,
    onDismiss: (() -> Unit)? = null,
    icon: ImageSource? = null,
    overrides: AlertDialogOverrides = AlertDialogOverrides.None,
    requestKey: String = "alert_dialog",
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
) {
    val appearance = AlertDialogDefaults.resolve(overrides)
    Dialog(
        visible = visible,
        requestKey = requestKey,
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        onDismissRequest = onDismissRequest,
    ) {
        // The composite owns Material-like layout and token defaults, while Dialog owns overlay lifecycle.
        Box(
            modifier = Modifier
                .minWidth(appearance.minWidth)
                .backgroundColor(appearance.containerColor)
                .shape(appearance.shape)
                .clip()
                .padding(appearance.contentPadding),
        ) {
            Column(
                horizontalAlignment = HorizontalAlignment.Center,
            ) {
                if (icon != null) {
                    Icon(
                        source = icon,
                        tint = appearance.iconTint,
                        size = appearance.iconSize,
                    )
                    Spacer(modifier = Modifier.padding(bottom = appearance.iconBottomSpacing))
                }
                Text(
                    text = title,
                    style = appearance.titleStyle,
                    color = appearance.titleColor,
                )
                Spacer(modifier = Modifier.padding(bottom = appearance.titleToTextSpacing))
                Text(
                    text = text,
                    style = appearance.textStyle,
                    color = appearance.textColor,
                )
                Spacer(modifier = Modifier.padding(bottom = appearance.textToButtonsSpacing))
                Row(
                    spacing = appearance.buttonSpacing,
                    arrangement = MainAxisArrangement.End,
                    modifier = Modifier.align(HorizontalAlignment.End),
                ) {
                    if (dismissButtonText != null && onDismiss != null) {
                        Button(
                            text = dismissButtonText,
                            onClick = onDismiss,
                            variant = ButtonVariant.Text,
                        )
                    }
                    Button(
                        text = confirmButtonText,
                        onClick = onConfirm,
                        variant = ButtonVariant.Text,
                    )
                }
            }
        }
    }
}

/**
 * Shows lightweight text in a non-focusable popup anchored to a rendered semantics id.
 *
 * The tooltip does not steal input focus. The owner controls [visible] and removes it after
 * [onDismissRequest]; [anchorId] must resolve in the same host window.
 *
 * @sample com.viewcompose.ui.foundation.samples.feedbackDslSample
 * @receiver active tree builder submitting the popup request
 * @param text tooltip message displayed in one themed surface
 * @param visible whether this render keeps the tooltip request active
 * @param anchorId semantics id of the rendered anchor view
 * @param alignment preferred placement relative to the anchor
 * @param overflowPolicy flip and clamp behavior near window edges
 * @param windowMargin minimum logical distance from the window edge
 * @param dismissOnClickOutside whether an outside click requests dismissal
 * @param onDismissRequest optional callback invoked for presenter dismissal requests
 * @param requestKey stable request identity within the current render session
 */
fun UiTreeBuilder.PlainTooltip(
    text: String,
    visible: Boolean,
    anchorId: String,
    alignment: PopupAlignment = PopupAlignment.BelowStart,
    overflowPolicy: PopupOverflowPolicy = PopupOverflowPolicy.FlipThenClamp,
    windowMargin: UiDp = 8.dp,
    dismissOnClickOutside: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    requestKey: String = "tooltip",
) {
    Popup(
        visible = visible,
        anchorId = anchorId,
        requestKey = requestKey,
        alignment = alignment,
        overflowPolicy = overflowPolicy,
        windowMargin = windowMargin,
        dismissOnClickOutside = dismissOnClickOutside,
        focusable = false,
        onDismissRequest = onDismissRequest,
    ) {
        // Tooltip content stays as one surface so presenters can apply consistent positioning and elevation.
        Box(
            contentAlignment = BoxAlignment.Center,
            modifier = Modifier
                .backgroundColor(TooltipDefaults.containerColor())
                .shape(TooltipDefaults.shape())
                .clip()
                .padding(
                    horizontal = TooltipDefaults.horizontalPadding(),
                    vertical = TooltipDefaults.verticalPadding(),
                ),
        ) {
            Text(
                text = text,
                style = TooltipDefaults.textStyle(),
                color = TooltipDefaults.contentColor(),
            )
        }
    }
}

/**
 * Shows themed menu content in a focusable popup anchored to [anchorId].
 *
 * [expanded] controls request presence; outside clicks request dismissal but do not mutate caller
 * state. The caller must set [expanded] to `false` from [onDismissRequest].
 *
 * @sample com.viewcompose.ui.foundation.samples.feedbackDslSample
 * @receiver active tree builder submitting the popup request
 * @param expanded whether this render keeps the dropdown request active
 * @param anchorId semantics id of the rendered anchor view
 * @param onDismissRequest callback invoked when the popup requests removal
 * @param alignment preferred placement relative to the anchor
 * @param overflowPolicy flip and clamp behavior near window edges
 * @param windowMargin minimum logical distance from the window edge
 * @param requestKey stable request identity within the current render session
 * @param modifier ordered caller configuration applied to the menu surface
 * @param content eager menu content emitted into the popup column
 */
fun UiTreeBuilder.DropdownMenu(
    expanded: Boolean,
    anchorId: String,
    onDismissRequest: () -> Unit,
    alignment: PopupAlignment = PopupAlignment.BelowStart,
    overflowPolicy: PopupOverflowPolicy = PopupOverflowPolicy.FlipThenClamp,
    windowMargin: UiDp = 8.dp,
    requestKey: String = "dropdown_menu",
    modifier: Modifier = Modifier,
    content: ColumnScope.() -> Unit,
) {
    Popup(
        visible = expanded,
        anchorId = anchorId,
        requestKey = requestKey,
        alignment = alignment,
        overflowPolicy = overflowPolicy,
        windowMargin = windowMargin,
        dismissOnClickOutside = true,
        onDismissRequest = onDismissRequest,
    ) {
        // modifier is applied to the menu surface, while items keep their internal padding and height defaults.
        Box(
            modifier = Modifier
                .minWidth(DropdownMenuDefaults.minWidth())
                .backgroundColor(DropdownMenuDefaults.containerColor())
                .shape(DropdownMenuDefaults.shape())
                .clip()
                .elevation(DropdownMenuDefaults.elevation())
                .then(modifier),
        ) {
            Column(
                modifier = Modifier.padding(vertical = DropdownMenuDefaults.verticalPadding()),
                content = content,
            )
        }
    }
}

/**
 * Creates one full-width action row for a dropdown menu.
 *
 * Disabled items retain layout and muted appearance but install neither click handling nor
 * interaction indication.
 *
 * @sample com.viewcompose.ui.foundation.samples.feedbackDslSample
 * @receiver active tree builder receiving the menu item
 * @param text primary single-line item label
 * @param onClick callback invoked synchronously for an accepted item click
 * @param leadingIcon optional image displayed before the label
 * @param trailingText optional supporting value displayed at the row end
 * @param enabled whether the item accepts input and uses enabled appearance
 * @param modifier ordered caller configuration applied after item geometry and behavior
 */
fun UiTreeBuilder.DropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    leadingIcon: ImageSource? = null,
    trailingText: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Build the full modifier chain first so caller modifiers are appended after enabled/disabled behavior.
    val itemModifier = Modifier
        .fillMaxWidth()
        .height(DropdownMenuDefaults.itemHeight())
        .padding(horizontal = DropdownMenuDefaults.itemHorizontalPadding())
        .then(
            if (enabled) {
                Modifier
                    .interactionIndication(
                        UiInteractionIndication.StateLayer(
                            stateLayerColorsFor(DropdownMenuDefaults.contentColor()),
                        ),
                    )
                    .clickable(onClick)
            } else {
                Modifier.alpha(DropdownMenuDefaults.disabledAlpha())
            },
        )
        .then(modifier)
    Row(
        verticalAlignment = VerticalAlignment.Center,
        modifier = itemModifier,
    ) {
        if (leadingIcon != null) {
            Icon(
                source = leadingIcon,
                tint = DropdownMenuDefaults.contentColor(),
                size = DropdownMenuDefaults.iconSize(),
            )
            Spacer(modifier = Modifier.width(DropdownMenuDefaults.iconToTextSpacing()))
        }
        Text(
            text = text,
            style = DropdownMenuDefaults.textStyle(),
            color = DropdownMenuDefaults.contentColor(),
            modifier = Modifier.weight(1f),
        )
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = DropdownMenuDefaults.textStyle(),
                color = DropdownMenuDefaults.trailingTextColor(),
            )
        }
    }
}
