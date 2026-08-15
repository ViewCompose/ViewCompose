package com.viewcompose.ui.foundation

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.SemanticsCollectionInfo
import com.viewcompose.ui.modifier.SemanticsCollectionSelectionMode
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.interactionIndication
import com.viewcompose.ui.modifier.semantics
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.SegmentedControlItem
import com.viewcompose.ui.node.UiImageRequestOptions
import com.viewcompose.ui.node.UiInteractionIndication
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.node.spec.IconButtonNodeProps
import com.viewcompose.ui.node.spec.SegmentedControlNodeProps
import com.viewcompose.ui.node.spec.uiFontFamily

/**
 * Displays a themed text action and invokes [onClick] for an accepted enabled click.
 *
 * The current theme resolves visual hierarchy, typography, padding, shape, effective minimum
 * height, and the centered visible container height. A design system may therefore retain a 48dp
 * semantic and touch target while drawing a smaller container. Explicit visual modifiers remain
 * authoritative over the component surface. State remains caller-owned; this component stores no
 * selection or progress value.
 *
 * @sample com.viewcompose.ui.foundation.samples.buttonSample
 * @receiver active tree builder that receives the emitted Button node
 * @param text visible action label
 * @param onClick callback invoked synchronously on the renderer thread for an enabled click
 * @param leadingIcon optional resource icon placed before [text]
 * @param trailingIcon optional resource icon placed after [text]
 * @param variant visual hierarchy used to resolve container, content, and border roles
 * @param size interaction-density tier used for target, visible container, padding, icon, and text
 * @param enabled whether input is accepted and enabled color roles are used
 * @param overrides sparse instance appearance applied after scoped [ProvideButtonOverrides] values
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended to the emitted Button node
 */
fun UiTreeBuilder.Button(
    text: String,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageSource.Resource? = null,
    trailingIcon: ImageSource.Resource? = null,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = true,
    overrides: ButtonOverrides = ButtonOverrides.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val appearance = ButtonDefaults.resolve(
        variant = variant,
        size = size,
        enabled = enabled,
        instance = overrides,
    )
    emitButton(
        text = text,
        onClick = onClick,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        appearance = appearance,
        key = key,
        modifier = modifier,
    )
}

private fun UiTreeBuilder.emitButton(
    text: String,
    onClick: (() -> Unit)?,
    leadingIcon: ImageSource.Resource?,
    trailingIcon: ImageSource.Resource?,
    enabled: Boolean,
    appearance: ResolvedButtonAppearance,
    key: Any?,
    modifier: Modifier,
) {
    emit(
        type = NodeType.Button,
        key = key,
        spec = ButtonNodeProps(
            text = text,
            enabled = enabled,
            onClick = onClick,
            textColor = appearance.contentColor,
            textSizeSp = appearance.textStyle.fontSizeSp,
            fontWeight = appearance.textStyle.fontWeight,
            fontFamily = uiFontFamily(appearance.textStyle.fontFamily),
            letterSpacingEm = appearance.textStyle.letterSpacingEm,
            lineHeightSp = appearance.textStyle.lineHeightSp,
            includeFontPadding = appearance.textStyle.includeFontPadding,
            backgroundColor = appearance.containerColor,
            borderWidth = appearance.borderWidth,
            borderColor = appearance.borderColor,
            shape = appearance.shape,
            minHeight = appearance.minimumHeight,
            paddingHorizontal = appearance.horizontalPadding,
            paddingVertical = appearance.verticalPadding,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            iconTint = appearance.contentColor,
            iconSize = appearance.iconSize,
            iconSpacing = appearance.iconSpacing,
            visualHeight = appearance.visualHeight,
        ),
        modifier = Modifier
            .interactionIndication(UiInteractionIndication.StateLayer(appearance.stateLayerColors))
            .then(modifier),
    )
}

/**
 * Emits an icon-only button whose image uses the current scoped loader.
 *
 * A non-null [icon] is loaded with [ImageContentScale.Inside]; a resource remains directly
 * renderable when no loader is installed. Click semantics are emitted only while [enabled] is
 * `true` and [onClick] is non-null. The component applies its required square size and click
 * semantics before appending [modifier].
 *
 * @sample com.viewcompose.ui.foundation.samples.imageLoadingSample
 * @receiver active tree builder that receives the emitted button node
 * @param icon primary icon source, or `null` for no icon content
 * @param contentDescription accessibility description, or `null` for decorative content
 * @param onClick callback invoked synchronously for an enabled click, or `null` for no action
 * @param variant visual button variant used to resolve theme colors and shape
 * @param size control size used for bounds, padding, and theme defaults
 * @param requestOptions immutable decode, cache, transition, and adapter-extension policy
 * @param enabled whether the button accepts clicks and uses enabled theme tokens
 * @param overrides sparse instance appearance applied after scoped [ProvideIconButtonOverrides]
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after required size and click semantics
 */
fun UiTreeBuilder.IconButton(
    icon: ImageSource?,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    variant: ButtonVariant = ButtonVariant.Text,
    size: ButtonSize = ButtonSize.Medium,
    requestOptions: UiImageRequestOptions = UiImageRequestOptions(),
    enabled: Boolean = true,
    overrides: IconButtonOverrides = IconButtonOverrides.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val appearance = IconButtonDefaults.resolve(
        variant = variant,
        size = size,
        enabled = enabled,
        instance = overrides,
    )
    val semanticModifier = Modifier
        .size(
            width = appearance.size,
            height = appearance.size,
        )
        .interactionIndication(UiInteractionIndication.StateLayer(appearance.stateLayerColors))
        .then(
            if (enabled && onClick != null) {
                Modifier.clickable(onClick)
            } else {
                Modifier
            },
        )
        .then(modifier)
    emit(
        type = NodeType.IconButton,
        key = key,
        spec = IconButtonNodeProps(
            contentDescription = contentDescription,
            contentScale = ImageContentScale.Inside,
            tint = appearance.contentColor,
            source = icon,
            placeholder = null,
            error = null,
            fallback = null,
            imageLoader = ImageLoading.current,
            requestOptions = requestOptions,
            enabled = enabled,
            backgroundColor = appearance.containerColor,
            borderWidth = appearance.borderWidth,
            borderColor = appearance.borderColor,
            shape = appearance.shape,
            contentPadding = appearance.contentPadding,
        ),
        modifier = semanticModifier,
    )
}

/**
 * Emits a mutually exclusive segmented selection control backed by caller-owned state.
 *
 * [selectedIndex] is snapshotted for this render. The renderer invokes [onSelectionChange]
 * synchronously with the requested index; the caller must publish the accepted state in a later
 * render. Appearance resolves once from instance, scoped, and semantic defaults in that order.
 *
 * @sample com.viewcompose.ui.foundation.samples.stableSelectionItemIdentitySample
 * @receiver active tree builder that receives the emitted SegmentedControl node
 * @param items ordered stable segment snapshots with label and per-item enabled state
 * @param selectedIndex currently selected item index, or `-1` only when [items] is empty
 * @param onSelectionChange callback receiving a requested item index on the renderer thread
 * @param size interaction-density tier used for typography, padding, and minimum height
 * @param enabled whether selection input is accepted and enabled appearance roles are used
 * @param overrides sparse instance appearance applied after scoped [ProvideSegmentedControlOverrides]
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after the resolved minimum height
 * @throws IllegalArgumentException for duplicate keys or a selected index outside [items]
 */
fun UiTreeBuilder.SegmentedControl(
    items: List<SegmentedControlItem>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    size: SegmentedControlSize = SegmentedControlSize.Medium,
    enabled: Boolean = true,
    overrides: SegmentedControlOverrides = SegmentedControlOverrides.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val appearance = SegmentedControlDefaults.resolve(size, enabled, overrides)
    emit(
        type = NodeType.SegmentedControl,
        key = key,
        spec = SegmentedControlNodeProps(
            items = items,
            selectedIndex = selectedIndex,
            onSelectionChange = onSelectionChange,
            enabled = enabled,
            backgroundColor = appearance.containerColor,
            indicatorColor = appearance.indicatorColor,
            shape = appearance.shape,
            textColor = appearance.contentColor,
            selectedTextColor = appearance.selectedContentColor,
            textSizeSp = appearance.textStyle.fontSizeSp,
            fontWeight = appearance.textStyle.fontWeight,
            fontFamily = uiFontFamily(appearance.textStyle.fontFamily),
            letterSpacingEm = appearance.textStyle.letterSpacingEm,
            lineHeightSp = appearance.textStyle.lineHeightSp,
            includeFontPadding = appearance.textStyle.includeFontPadding,
            paddingHorizontal = appearance.horizontalPadding,
            paddingVertical = appearance.verticalPadding,
            unselectedStateLayerColors = appearance.unselectedStateLayerColors,
            selectedStateLayerColors = appearance.selectedStateLayerColors,
        ),
        modifier = Modifier
            .height(appearance.minimumHeight)
            .semantics {
                collectionInfo = SemanticsCollectionInfo(
                    rowCount = 1,
                    columnCount = items.size,
                    selectionMode = SemanticsCollectionSelectionMode.Single,
                )
            }
            .then(modifier),
    )
}
