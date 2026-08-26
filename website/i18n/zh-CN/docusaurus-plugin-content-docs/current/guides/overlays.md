---
translation_source: guides/overlays.md
translation_source_hash: 0c86bbffed62a8482d50b4da7f4d375688289b2ad295c8ecd67e70437b2747de
translation_status: current
---

# 展示 Overlay 与瞬态反馈

Overlay 声明是由状态控制的请求。应用负责决定请求是否存在；Root 作用域 Host 负责平台窗口、
展示与清理。如果尚未安装 Android Presenter，请先阅读 [Overlay 教程](../tutorials/overlays.md)。

## 展示模态 Bottom Sheet

同一个逻辑 Sheet 可见期间应保持稳定的 Key。平台关闭操作会调用回调，但不会修改 `visible`；
请更新创建该请求的同一个应用状态。

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/OverlayGuideSamples.kt" region="overlay-bottom-sheet" sample_id="guide.overlay-bottom-sheet" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.AccountActionsSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        visible = visible,
        requestKey = "account-actions",
        skipPartiallyExpanded = true,
        onDismissRequest = onDismissRequest,
    ) {
        Text("Account actions")
    }
}
```

Theme 值、嵌套的 `ProvideModalBottomSheetOverrides` 与实例 Override 会解析为一份完整外观快照。
Material 3 支持可逆的 Partial State 策略；One UI 7 Presenter 只有一个内建展开状态。Presenter
专属的 Margin、Handle 与手势仍由各自的集成模块拥有。

## 锚定 Dropdown Menu

在已渲染节点上发布 Anchor，并在 Menu 请求中使用同一个标识。逻辑 start/end 遵循 Anchor 的
布局方向。默认 `FlipThenClamp` 策略会在窗口边缘先尝试另一侧，再把 Menu 限制在可见窗口内。

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/OverlayGuideSamples.kt" region="overlay-dropdown-menu" sample_id="guide.overlay-dropdown-menu" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.ProfileMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    Text(
        text = "Profile",
        modifier = Modifier.overlayAnchor("profile-menu-anchor"),
    )
    DropdownMenu(
        expanded = expanded,
        anchorId = "profile-menu-anchor",
        requestKey = "profile-menu",
        alignment = PopupAlignment.BelowEnd,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuItem("Settings", onClick = onDismissRequest)
        DropdownMenuItem("Sign out", onClick = onDismissRequest)
    }
}
```

Popup 打开期间，Android Presenter 会观察全局布局与滚动。Popup 内容拥有自己的 Shape 与
Elevation；`PopupWindow` 传输层不会再添加第二层平台阴影。要保留指定侧可使用 `Clamp`；只有
明确需要精确且不裁切的坐标时才使用 `None`。

## 替换重复的瞬态反馈

Snackbar 与 Toast 共用一条 Host 所有的 FIFO 通道。Identity 是
`(render session, requestKey)`，因此内容相同的重组不会重复入队。同一操作可能反复上报时，应
明确选择 Queue Policy。

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/OverlayGuideSamples.kt" region="overlay-snackbar" sample_id="guide.overlay-snackbar" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.SaveResultSnackbar(
    visible: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    Snackbar(
        visible = visible,
        requestKey = "save-result",
        message = "Saved",
        actionLabel = "Undo",
        queuePolicy = TransientFeedbackQueuePolicy.ReplaceSameKey,
        onAction = onUndo,
        onDismiss = { onDismiss() },
    )
}
```

独立通知使用 `Enqueue`，新的紧急结果使用 `ReplaceCurrent`，同一操作的最新版本使用
`ReplaceSameKey`，可丢弃状态使用 `DropIfBusy`。回调区分 Timeout、Action、Gesture、
Replacement、Removal、Session Cleanup、Dropped 与 Platform Dismissal。Android Toast 的时长
是近似值，因为所有受支持 API Level 并不都提供可靠的隐藏回调。

## 选择并验收 Backend

- 中立 Root 使用 `viewcompose-overlay-android` 提供 Dialog、Popup、Toast、嵌套 Session 与清理；
  Snackbar 和 Modal Bottom Sheet 会保持明确的 Unsupported 状态。
- Material Root 使用 `viewcompose-overlay-material3-android`；`setMaterial3UiContent` 会显式选择
  该 Adapter。
- One UI Root 保留中立 `setUiContent`，只有需要 One UI Snackbar 或 Bottom Dialog 时才显式构造
  `viewcompose-overlay-oneui7-android` Host。

验收 Outside Press 与 Android Back 都会清除调用方状态，打开的 Menu 会跟随移动 Anchor，
相同 Key 的反馈不会重复，并且 Session 释放只清理该 Session 的 Surface。Root 选择、传输与
Session 不变量、Attribution 和 Fallback 规则由
[ADR-0006](../architecture/decisions/0006-root-scoped-overlay-backend-selection.md)负责。实现契约分别由
[中立](../modules/viewcompose-overlay-android/README.md)、
[Material 3](../modules/viewcompose-overlay-material3-android/README.md) 与
[One UI 7](../modules/viewcompose-overlay-oneui7-android/README.md) 模块手册负责。
