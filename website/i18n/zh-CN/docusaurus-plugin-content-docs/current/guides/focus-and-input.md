---
translation_source: guides/focus-and-input.md
translation_source_hash: 234284d804702c13976144ff229e748a441a085a735bd2a3ffc9e3422b6f4806
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

## 5. 焦点编辑器可见性

LazyColumn、LazyVerticalGrid 和 ScrollableColumn 中的焦点编辑器会自动使用 Android 原生
子矩形请求链，不存在焦点跟随开关。最近的垂直滚动所有者只移动足以露出编辑器的距离；即使
`userScrollEnabled = false`，这种程序化移动仍然有效。

Pager 不同：它负责整页选择，而不是页面内部的任意移动。可能被 IME 遮挡的表单应放入页内
滚动所有者：

```kotlin
VerticalPager(currentPage = page, onPageChanged = { page = it }) {
    Page(key = "profile", contentRevision = profile.version) {
        ScrollableColumn {
            Text("Profile")
            TextField(state = name, placeholder = "Name")
        }
    }
}
```

页面边界会在编辑器的页内请求到达 Pager 前停止传播，因此打开 IME 不会选中另一页。若不希望
首次进入时移动，请控制由哪个编辑器获取焦点，而不是关闭滚动所有者的可见性契约。

## 6. Android 边界

- 硬件按键事件与 IME 文本编辑分离；软键盘组合输入继续走 `TextFieldState` 和
  `InputConnection` bridge。
- 未声明显式 `focusProperties` 目标时，回退 Android 原生焦点搜索。
- 对于首次焦点请求早于 IME 完成窗口 Resize 的 Android 版本，Renderer 会在可见视口改变后
  重新发出同一个原生矩形请求，而不会自行计算或写入容器滚动 Offset。
- `AndroidView` 拥有任意原生监听器。若 `nativeView` 回调替换 `View.OnKeyListener`，也会
  替换该目标的框架按键分发；需要 preview/bubble 语义时，应由声明式按键 modifier 单独拥有。
