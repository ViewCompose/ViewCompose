---
translation_source: guides/focus-and-input.md
translation_source_hash: 6cf36c31aa2d9eaa94c67c6ff8a0a0bee91c554305990381aeac54714a042e8a
translation_status: current
---

# 控制焦点与硬件按键

## 移动和清除焦点

使用 `remember` 保持每个 `FocusRequester` 稳定，将其附加到目标，并在来源上声明方向遍历。
使用 `LocalFocusManager.current` 执行当前 Session 范围的移动或清除操作。调用
`requestFocus()` 之前，requester 必须已附加到已挂载的目标。

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/FocusAndNestedScrollGuideSamples.kt" region="focus-form" sample_id="guide.focus-form" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.CredentialFocusForm() {
    val email = rememberTextFieldState()
    val password = rememberTextFieldState()
    val passwordFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    focusManager.clearFocus(force = true)
                    true
                } else {
                    false
                }
            },
    ) {
        TextField(
            state = email,
            label = "Email",
            modifier = Modifier.focusProperties {
                next = passwordFocus
                down = passwordFocus
            },
        )
        TextField(
            state = password,
            label = "Password",
            modifier = Modifier.focusRequester(passwordFocus),
        )
        Button(
            text = "Focus password",
            onClick = { passwordFocus.requestFocus() },
        )
    }
}
```

`onPreviewKeyEvent` 从最外层声明式祖先传播到焦点目标。未消费事件随后通过
`onKeyEvent` 从目标向上冒泡。硬件按键与软键盘组合输入相互独立；后者继续通过
`TextFieldState` 和 Android 输入桥接运行。

## 恢复带 key 的子项

临时移除或回收焦点组之前调用 `saveFocusedChild()`，返回后再调用
`restoreFocusedChild()`。恢复使用声明式节点 key；在匹配目标挂载之前会一直保持待处理。
页面或进程恢复仍由应用状态负责，并在挂载后显式请求焦点。

## 保持 Pager 编辑器可见

垂直滚动所有者通过 Android 原生子矩形链显示焦点编辑器，即使直接用户滚动已禁用也一样。
Pager 拥有整页选择，因此应把可能被遮挡的表单放入页面自己的 `ScrollableColumn`；外层
`VerticalPager` 继续由应用页面状态控制。不存在焦点跟随开关。通过决定哪个编辑器接收焦点
来控制不希望出现的初始移动；不要关闭滚动所有者的可见性契约。所有权、传播和 Android 映射见
[Modifier 架构](../architecture/modifier.md)。
