---
translation_source: guides/text-input.md
translation_source_hash: d7a39ff475659f298ae2a752cd726e2fd802e695e37b661287322c4d502dbbd9
translation_status: current
---

# ViewCompose 文本输入

## 1. 范围

当前模型是在 Android View 引擎上构建的完整文档编辑状态，拥有：

- 不可变 `TextDocument` 内容、行内样式与链接；
- 段落对齐、行高、缩进和项目符号；
- URI 行内附件、方向选区和临时 IME composition；
- 原子程序编辑、输入变换和 undo/redo 历史；
- 键盘选项、语义 IME action 和 autofill hint；
- 统一剪贴板、拖放与 IME Receive Content；
- 完整文档和选区的保存恢复。

框架不实现自定义文本布局引擎，也不替换 `InputConnection`。Android `Spannable`、
`ContentInfoCompat` 和 URI 解码保留在 renderer，不泄漏到 text-core。

## 2. 所有权

`TextFieldState` 是唯一真相源。`TextField`、`TextArea`、typed field 和 `SearchBar` 只接受
稳定状态实例。

```kotlin
val state = rememberTextFieldState("initial")

TextField(
    state = state,
    inputTransformation = InputTransformation.maxCodePoints(40),
)
```

富文本展示与编辑使用同一文档：

```kotlin
val document = textDocument {
    append("ViewCompose", TextSpanStyle(fontWeight = 700))
    append("\n")
    appendAttachment(
        InlineTextAttachment(
            id = "preview",
            mimeType = "image/png",
            uri = "content://example/preview",
        ),
    )
}
val state = rememberTextFieldState(document)

RichText(document)
TextArea(state = state)
```

旧的 `value: String + onValueChange(String)` API 已移除。重新引入第二条核心路径会丢失 selection
和 composition，因此不允许。

## 3. 状态模型

`TextFieldValue` 包含 `TextDocument`、方向性 `TextRange` selection 和可选 composing range；
`value.text` 是派生便捷属性。offset 统一使用 UTF-16，以精确对应 Android `Editable` 与
`InputConnection`。

平台无关且不可变的 `TextDocument` 包含 UTF-16 缓冲、字符样式/链接 `TextSpanRange`、段落
语义 `ParagraphStyleRange` 和指向 `INLINE_ATTACHMENT_CHARACTER` 的附件 range。

通过 `TextDocumentBuilder` 和 `textDocument { ... }` 构建。替换操作保留变更范围外的注解、
裁剪相交注解、移动后续 range，并原子插入替换文档的注解。

```kotlin
state.edit {
    replace(0, length, "replacement")
    selectAll()
}
```

`InputTransformation` 仅运行于平台提出的用户编辑；程序编辑不会被字段过滤器静默拒绝。
composition 更新合并成一个 undo 单元；undo/redo 会清除活跃 composing range，因为 IME Session
无法安全重放。

## 4. Receive Content

每个可编辑字段登记 `ReceiveContentConfiguration.Default`，接收 `text/*` 和 `image/*`。同一
listener 处理剪贴板粘贴、拖放、IME `commitContent` 和应用调用的
`ViewCompat.performReceiveContent`。

styled/HTML 文本从 `Spanned` 转为 `TextDocument`，URI item 转为 `InlineTextAttachment`。
不支持的 clip item 返回平台作为剩余 payload，不会静默丢弃。

```kotlin
TextArea(
    state = state,
    receiveContent = ReceiveContentConfiguration(
        mimeTypes = setOf("text/*", "image/png"),
        transformation = { received ->
            audit(received.source, received.mimeTypes)
            received.document
        },
    ),
)
```

Receive Content 插入经过 `InputTransformation`，形成一个 undo 单元，替换当前 selection，并
终止活跃 IME composition。

## 5. Android bridge

renderer 创建 `ViewComposeEditText` 与 `AndroidTextFieldController`，并保持：

1. 原生编辑同步执行，文本、selection 和 composition 作为一个 snapshot 读取。
2. `InputConnection` 变更和 batch edit 是事务边界。
3. 原生编辑更新 `TextFieldState`，随后的重组不把相同值写回。
4. 外部状态变化使用最小 `Editable.replace()` 范围。
5. 框架文档 span 重新应用时不删除 IME/平台 span。
6. 框架写入恢复 selection/composition 并抑制反馈回调。
7. 只有需要时才因 input type 或 editor option 变化重启 input connection。
8. `EditorInfo` 发布配置的 Receive Content MIME type。
9. renderer 回滚重新绑定旧文档、selection 与 composition snapshot。

View 继续提供 IME、光标手柄、剪贴板、硬件键盘、无障碍、bidi、拼写检查、autofill 和手写笔。
`RichText` 与 `TextField` 共用 `TextDocument -> Spannable` adapter。无法解析的图片或非图片
附件显示为行内占位符，同时保留完整文档元数据。

## 6. 持久化

`rememberTextFieldState` 使用宿主 saveable-state registry，保存文本、样式、段落、附件与
selection 起止；不保存 IME composition、undo/redo、焦点和键盘可见性，因为它们属于当前
window 与输入 Session。

## 7. 测试契约

每次 text bridge 变化必须覆盖原生文本/selection 同步、composing 后 commit、输入变换、富文档
Spannable round-trip、剪贴板和 URI Receive Content、IME MIME 发布、外部最小编辑、renderer
回滚，以及不含 composition 的完整文档保存恢复。

代表性的中文和日文 IME、硬件键盘、TalkBack、autofill 服务与手写笔仍要求真机覆盖。

## 8. 边界

glyph shaping、bidi、断行、光标几何、selection handle、拼写检查和无障碍文本遍历继续委托
原生 View 文本引擎。框架不尝试编译器级文本 lowering 或自定义段落 renderer。
