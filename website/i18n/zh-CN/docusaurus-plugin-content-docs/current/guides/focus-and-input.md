---
translation_source: guides/focus-and-input.md
translation_source_hash: 52fb3b4d11198b94493275be5a434d6bc130ea855a66b94780c908e19a3bfd6f
translation_status: current
---

# 焦点与硬件按键输入

## 1. 架构

焦点契约分为三层：

1. `viewcompose-ui-contract` 拥有平台无关的焦点方向、requester 状态、焦点属性和按键事件值。
2. `viewcompose-renderer-android` 把这些契约绑定到 Android `View` 焦点与按键分发。
3. `viewcompose-ui-foundation` 通过 `LocalFocusManager.current` 暴露当前渲染 Session。

`FocusRequester` 不直接持有 View。节点挂载、重新绑定、回滚和销毁时，由渲染器连接或断开
connector。

## 2. 公共 API

- `Modifier.focusable(enabled)`
- `Modifier.focusRequester(requester)`
- `Modifier.focusProperties { ... }`
- `Modifier.focusGroup(enabled)`
- `Modifier.onFocusChanged { state -> ... }`
- `Modifier.onPreviewKeyEvent { event -> ... }`
- `Modifier.onKeyEvent { event -> ... }`
- `LocalFocusManager.current`
- `FocusRequester.requestFocus(direction)`
- `FocusRequester.saveFocusedChild()` / `restoreFocusedChild()`

`onPreviewKeyEvent` 从声明树最外层祖先向焦点目标传播。未消费事件再通过 `onKeyEvent` 从目标
向声明祖先冒泡。

## 3. 示例

```kotlin
val emailFocus = remember { FocusRequester() }
val passwordFocus = remember { FocusRequester() }
val focusManager = LocalFocusManager.current

Column {
    TextField(
        state = email,
        modifier = Modifier
            .focusRequester(emailFocus)
            .focusProperties {
                next = passwordFocus
                down = passwordFocus
            },
    )
    TextField(
        state = password,
        modifier = Modifier
            .focusRequester(passwordFocus)
            .onKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    focusManager.clearFocus(force = true)
                    true
                } else {
                    false
                }
            },
    )
}
```

## 4. 恢复

`saveFocusedChild()` 记录焦点 connector 的声明节点 key。目标暂时移除或回收时，
`restoreFocusedChild()` 保持等待，直到相同 requester 再次连接到具有该 key 的目标。

该 API 恢复框架节点复用或重新挂载中的焦点。跨页面或进程的持久恢复仍由应用状态驱动，并在
挂载后显式请求焦点。

## 5. Android 边界

- 硬件按键事件与 IME 文本编辑分离；软键盘组合输入继续走 `TextFieldState` 和
  `InputConnection` bridge。
- 未声明显式 `focusProperties` 目标时，回退 Android 原生焦点搜索。
- `AndroidView` 拥有任意原生监听器。若 `nativeView` 回调替换 `View.OnKeyListener`，也会
  替换该目标的框架按键分发；需要 preview/bubble 语义时，应由声明式按键 modifier 单独拥有。
