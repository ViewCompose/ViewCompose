package com.viewcompose.widget.core

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
import com.viewcompose.ui.modifier.minWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.modifier.width
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.unit.UiDp

/**
 * 发射标准确认对话框组合。
 * Emits a standard confirmation dialog composite.
 *
 * AlertDialog 复用 Dialog overlay，并在 surface 内容中组装标题、正文、可选图标和操作按钮。
 * AlertDialog reuses the Dialog overlay and builds title, body, optional icon, and action buttons inside its surface content.
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
    requestKey: String = "alert_dialog",
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
) {
    Dialog(
        visible = visible,
        requestKey = requestKey,
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        onDismissRequest = onDismissRequest,
    ) {
        // 组合层负责 Material-like 布局和 token 默认值，Dialog 只负责 overlay 生命周期。
        // The composite owns Material-like layout and token defaults, while Dialog owns overlay lifecycle.
        val shape = AlertDialogDefaults.shape()
        Box(
            modifier = Modifier
                .minWidth(AlertDialogDefaults.minWidth())
                .backgroundColor(AlertDialogDefaults.containerColor())
                .shape(shape)
                .clip()
                .padding(AlertDialogDefaults.contentPadding()),
        ) {
            Column(
                horizontalAlignment = HorizontalAlignment.Center,
            ) {
                if (icon != null) {
                    Icon(
                        source = icon,
                        tint = AlertDialogDefaults.iconTint(),
                        size = AlertDialogDefaults.iconSize(),
                    )
                    Spacer(modifier = Modifier.padding(bottom = AlertDialogDefaults.iconBottomSpacing()))
                }
                Text(
                    text = title,
                    style = AlertDialogDefaults.titleStyle(),
                    color = AlertDialogDefaults.titleColor(),
                )
                Spacer(modifier = Modifier.padding(bottom = AlertDialogDefaults.titleToTextSpacing()))
                Text(
                    text = text,
                    style = AlertDialogDefaults.textStyle(),
                    color = AlertDialogDefaults.textColor(),
                )
                Spacer(modifier = Modifier.padding(bottom = AlertDialogDefaults.textToButtonsSpacing()))
                Row(
                    spacing = AlertDialogDefaults.buttonSpacing(),
                    arrangement = MainAxisArrangement.End,
                    modifier = Modifier.align(HorizontalAlignment.End),
                ) {
                    if (dismissButtonText != null && onDismiss != null) {
                        TextButton(
                            text = dismissButtonText,
                            onClick = onDismiss,
                        )
                    }
                    TextButton(
                        text = confirmButtonText,
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}

/**
 * 发射轻量文本 tooltip。
 * Emits a lightweight text tooltip.
 *
 * Tooltip 使用非 focusable Popup，避免抢占输入焦点，同时仍能随 anchorId 定位。
 * Tooltip uses a non-focusable Popup to avoid stealing input focus while still being anchored by anchorId.
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
        // tooltip 内容保持单一 surface，便于 presenter 做统一阴影和定位。
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
 * 发射锚定弹出菜单。
 * Emits an anchored dropdown menu.
 *
 * expanded 控制 Popup 请求是否存在；点击外部固定触发 onDismissRequest 关闭菜单。
 * expanded controls whether the Popup request exists; outside clicks always call onDismissRequest to close the menu.
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
        // modifier 应用于菜单 surface 外层，菜单项仍保持内部统一 padding/高度。
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
 * 发射 dropdown menu 内的单个可选项。
 * Emits one selectable item inside a dropdown menu.
 *
 * disabled 状态通过 alpha 表达并移除 clickable，避免 renderer 收到不可用项点击事件。
 * The disabled state is expressed with alpha and removes clickable so the renderer cannot dispatch disabled item clicks.
 */
fun UiTreeBuilder.DropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    leadingIcon: ImageSource? = null,
    trailingText: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // 这里先构建完整 modifier 链，确保 enabled/disabled 分支后仍能追加调用方 modifier。
    // Build the full modifier chain first so caller modifiers are appended after enabled/disabled behavior.
    val itemModifier = Modifier
        .fillMaxWidth()
        .height(DropdownMenuDefaults.itemHeight())
        .padding(horizontal = DropdownMenuDefaults.itemHorizontalPadding())
        .then(
            if (enabled) {
                Modifier.clickable(onClick)
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
