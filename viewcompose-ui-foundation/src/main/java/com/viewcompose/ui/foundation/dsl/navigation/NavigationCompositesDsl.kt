package com.viewcompose.ui.foundation

import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.elevation
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NavigationBarItem
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.NavigationBarNodeProps
import com.viewcompose.ui.node.spec.uiFontFamily
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.UiSp

/**
 * Top app bar composite.
 */
fun UiTreeBuilder.TopAppBar(
    title: String,
    navigationIcon: (UiTreeBuilder.() -> Unit)? = null,
    actions: (RowScope.() -> Unit)? = null,
    containerColor: Int = TopAppBarDefaults.containerColor(),
    titleColor: Int = TopAppBarDefaults.titleColor(),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val semanticModifier = Modifier
        .fillMaxWidth()
        .height(TopAppBarDefaults.height())
        .backgroundColor(containerColor)
        .padding(horizontal = TopAppBarDefaults.horizontalPadding())
        .then(modifier)
    Row(
        key = key,
        verticalAlignment = VerticalAlignment.Center,
        modifier = semanticModifier,
    ) {
        if (navigationIcon != null) {
            navigationIcon()
        }
        Text(
            text = title,
            style = TopAppBarDefaults.titleStyle(),
            color = titleColor,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(left = TopAppBarDefaults.titleStartPadding()),
        )
        if (actions != null) {
            Row(
                verticalAlignment = VerticalAlignment.Center,
            ) {
                actions()
            }
        }
    }
}

/**
 * Bottom app bar composite.
 */
fun UiTreeBuilder.BottomAppBar(
    containerColor: Int = BottomAppBarDefaults.containerColor(),
    key: Any? = null,
    modifier: Modifier = Modifier,
    content: RowScope.() -> Unit,
) {
    val semanticModifier = Modifier
        .fillMaxWidth()
        .height(BottomAppBarDefaults.height())
        .backgroundColor(containerColor)
        .elevation(BottomAppBarDefaults.elevation())
        .padding(horizontal = BottomAppBarDefaults.horizontalPadding())
        .then(modifier)
    Row(
        key = key,
        verticalAlignment = VerticalAlignment.Center,
        modifier = semanticModifier,
        content = content,
    )
}

/**
 * Item collection scope for NavigationBar.
 */
@UiDslMarker
class NavigationBarScope internal constructor() {
    private val items = mutableListOf<NavigationBarItem>()

    /**
     * Adds one navigation item.
     */
    fun Item(
        label: String,
        icon: ImageSource.Resource,
        selectedIcon: ImageSource.Resource? = null,
        badgeCount: Int? = null,
    ) {
        items += NavigationBarItem(
            label = label,
            icon = icon,
            selectedIcon = selectedIcon,
            badgeCount = badgeCount,
        )
    }

    internal fun build(): List<NavigationBarItem> = items.toList()
}

/**
 * Emits a bottom NavigationBar node.
 */
fun UiTreeBuilder.NavigationBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    containerColor: Int = NavigationBarDefaults.containerColor(),
    selectedIconColor: Int = NavigationBarDefaults.selectedIconColor(),
    unselectedIconColor: Int = NavigationBarDefaults.unselectedIconColor(),
    selectedLabelColor: Int = NavigationBarDefaults.selectedLabelColor(),
    unselectedLabelColor: Int = NavigationBarDefaults.unselectedLabelColor(),
    indicatorColor: Int = NavigationBarDefaults.indicatorColor(),
    rippleColor: Int = NavigationBarDefaults.rippleColor(),
    iconSize: UiDp = NavigationBarDefaults.iconSize(),
    labelSizeSp: UiSp = NavigationBarDefaults.labelSizeSp(),
    labelStyle: UiTextStyle = NavigationBarDefaults.labelStyle(),
    badgeColor: Int = NavigationBarDefaults.badgeColor(),
    badgeTextColor: Int = NavigationBarDefaults.badgeTextColor(),
    key: Any? = null,
    modifier: Modifier = Modifier,
    items: NavigationBarScope.() -> Unit,
) {
    val builtItems = NavigationBarScope().apply(items).build()
    emit(
        type = NodeType.NavigationBar,
        key = key,
        spec = NavigationBarNodeProps(
            items = builtItems,
            selectedIndex = selectedIndex,
            onItemSelected = onItemSelected,
            containerColor = containerColor,
            selectedIconColor = selectedIconColor,
            unselectedIconColor = unselectedIconColor,
            selectedLabelColor = selectedLabelColor,
            unselectedLabelColor = unselectedLabelColor,
            indicatorColor = indicatorColor,
            rippleColor = rippleColor,
            iconSize = iconSize,
            labelSizeSp = labelSizeSp,
            labelFontWeight = labelStyle.fontWeight,
            labelFontFamily = uiFontFamily(labelStyle.fontFamily),
            labelLetterSpacingEm = labelStyle.letterSpacingEm,
            labelLineHeightSp = labelStyle.lineHeightSp,
            labelIncludeFontPadding = labelStyle.includeFontPadding,
            badgeColor = badgeColor,
            badgeTextColor = badgeTextColor,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(NavigationBarDefaults.height())
            .then(modifier),
    )
}
