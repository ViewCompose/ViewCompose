package com.viewcompose.ui.foundation

import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.SemanticsCollectionInfo
import com.viewcompose.ui.modifier.SemanticsCollectionSelectionMode
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.elevation
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.semantics
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NavigationBarItem
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.NavigationBarNodeProps
import com.viewcompose.ui.node.spec.uiFontFamily
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.UiSp

/**
 * Emits a top app bar with independent title, navigation, and action content roles.
 *
 * Appearance resolves from [TopAppBarDefaults], nested [ProvideTopAppBarOverrides] scopes, and
 * instance [overrides]. Navigation and action slots receive their resolved content colors;
 * IconButton instances inside those slots retain final precedence through their own overrides.
 *
 * @sample com.viewcompose.ui.foundation.samples.remainingComponentOverridesSample
 * @receiver active tree builder receiving the app bar
 * @param title single-line title text
 * @param navigationIcon optional leading navigation content
 * @param actions optional trailing actions built in a horizontal scope
 * @param overrides sparse instance appearance applied after scoped top-app-bar overrides
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after resolved bar geometry and background
 */
fun UiTreeBuilder.TopAppBar(
    title: String,
    navigationIcon: (UiTreeBuilder.() -> Unit)? = null,
    actions: (RowScope.() -> Unit)? = null,
    overrides: TopAppBarOverrides = TopAppBarOverrides.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val appearance = TopAppBarDefaults.resolve(overrides)
    val semanticModifier = Modifier
        .fillMaxWidth()
        .height(appearance.height)
        .backgroundColor(appearance.containerColor)
        .padding(horizontal = appearance.horizontalPadding)
        .then(modifier)
    Row(
        key = key,
        verticalAlignment = VerticalAlignment.Center,
        modifier = semanticModifier,
    ) {
        if (navigationIcon != null) {
            ProvideAppBarIconColor(appearance.navigationIconColor, navigationIcon)
        }
        Text(
            text = title,
            style = appearance.titleStyle,
            color = appearance.titleColor,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(left = appearance.titleStartPadding),
        )
        if (actions != null) {
            ProvideAppBarIconColor(appearance.actionIconColor) {
                Row(
                    verticalAlignment = VerticalAlignment.Center,
                ) {
                    actions()
                }
            }
        }
    }
}

/**
 * Emits a bottom app bar and provides its resolved content role to descendants.
 *
 * @sample com.viewcompose.ui.foundation.samples.remainingComponentOverridesSample
 * @receiver active tree builder receiving the app bar
 * @param overrides sparse instance appearance applied after scoped bottom-app-bar overrides
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after resolved bar geometry and background
 * @param content row content built synchronously with the resolved content color
 */
fun UiTreeBuilder.BottomAppBar(
    overrides: BottomAppBarOverrides = BottomAppBarOverrides.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
    content: RowScope.() -> Unit,
) {
    val appearance = BottomAppBarDefaults.resolve(overrides)
    val semanticModifier = Modifier
        .fillMaxWidth()
        .height(appearance.height)
        .backgroundColor(appearance.containerColor)
        .elevation(appearance.elevation)
        .padding(horizontal = appearance.horizontalPadding)
        .then(modifier)
    ProvideAppBarIconColor(appearance.contentColor) {
        Row(
            key = key,
            verticalAlignment = VerticalAlignment.Center,
            modifier = semanticModifier,
            content = content,
        )
    }
}

private fun UiTreeBuilder.ProvideAppBarIconColor(
    color: Int,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalContentColor, color) {
        ProvideIconButtonOverrides(
            IconButtonOverrides(
                contentColor = color,
                disabledContentColor = colorWithAlpha(color, 0.38f),
            ),
            content,
        )
    }
}

/**
 * Item collection scope for NavigationBar.
 */
@UiDslMarker
class NavigationBarScope internal constructor() {
    private val items = mutableListOf<NavigationBarItem>()
    private val keys = linkedSetOf<Any>()

    /**
     * Adds one stable navigation destination.
     *
     * [key] owns destination identity across reorder, locale, and label changes. A disabled item
     * remains visible and accessible but does not invoke the bar selection callback.
     *
     * @param key unique logical destination identity
     * @param label user-visible localized destination label
     * @param icon drawable resource used while unselected
     * @param selectedIcon optional drawable resource used while selected
     * @param badgeCount optional non-negative badge value formatted by the renderer
     * @param enabled whether this destination accepts selection input
     * @throws IllegalArgumentException when [key] duplicates another item in this scope
     */
    fun Item(
        key: Any,
        label: String,
        icon: ImageSource.Resource,
        selectedIcon: ImageSource.Resource? = null,
        badgeCount: Int? = null,
        enabled: Boolean = true,
    ) {
        require(keys.add(key)) { "NavigationBar keys must be unique. Duplicate key: $key" }
        items += NavigationBarItem(
            key = key,
            label = label,
            icon = icon,
            selectedIcon = selectedIcon,
            badgeCount = badgeCount,
            enabled = enabled,
        )
    }

    internal fun build(): List<NavigationBarItem> = items.toList()
}

/**
 * Emits a bottom navigation bar backed by caller-owned destination selection.
 *
 * [selectedIndex] is a snapshot for this render. The renderer invokes [onItemSelected]
 * synchronously with a requested destination index; the caller publishes accepted state in a
 * later render. Appearance resolves once from instance, scoped, and semantic defaults.
 *
 * Every destination requires a stable key. Selection changes patch only the affected keyed
 * presentations, and parent/item accessibility metadata uses logical order even when physical
 * placement follows RTL.
 *
 * @sample com.viewcompose.ui.foundation.samples.stableSelectionItemIdentitySample
 * @receiver active tree builder that receives the emitted NavigationBar node
 * @param selectedIndex currently selected destination index, or `-1` only when the bar is empty
 * @param onItemSelected callback receiving a requested destination index on the renderer thread
 * @param overrides sparse instance appearance applied after scoped [ProvideNavigationBarOverrides]
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after the resolved bar height
 * @param items ordered, explicitly keyed destination declarations collected synchronously
 * @throws IllegalArgumentException for duplicate keys or an index outside the declared destinations
 */
fun UiTreeBuilder.NavigationBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    overrides: NavigationBarOverrides = NavigationBarOverrides.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
    items: NavigationBarScope.() -> Unit,
) {
    val appearance = NavigationBarDefaults.resolve(overrides)
    val builtItems = NavigationBarScope().apply(items).build()
    emit(
        type = NodeType.NavigationBar,
        key = key,
        spec = NavigationBarNodeProps(
            items = builtItems,
            selectedIndex = selectedIndex,
            onItemSelected = onItemSelected,
            containerColor = appearance.containerColor,
            selectedIconColor = appearance.selectedIconColor,
            unselectedIconColor = appearance.unselectedIconColor,
            selectedLabelColor = appearance.selectedLabelColor,
            unselectedLabelColor = appearance.unselectedLabelColor,
            indicatorColor = appearance.indicatorColor,
            rippleColor = appearance.rippleColor,
            iconSize = appearance.iconSize,
            labelSizeSp = appearance.labelStyle.fontSizeSp,
            labelFontWeight = appearance.labelStyle.fontWeight,
            labelFontFamily = uiFontFamily(appearance.labelStyle.fontFamily),
            labelLetterSpacingEm = appearance.labelStyle.letterSpacingEm,
            labelLineHeightSp = appearance.labelStyle.lineHeightSp,
            labelIncludeFontPadding = appearance.labelStyle.includeFontPadding,
            badgeColor = appearance.badgeColor,
            badgeTextColor = appearance.badgeTextColor,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(appearance.height)
            .semantics {
                collectionInfo = SemanticsCollectionInfo(
                    rowCount = 1,
                    columnCount = builtItems.size,
                    selectionMode = SemanticsCollectionSelectionMode.Single,
                )
            }
            .then(modifier),
    )
}
