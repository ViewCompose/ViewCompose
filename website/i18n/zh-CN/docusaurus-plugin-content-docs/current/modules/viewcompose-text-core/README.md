---
translation_source: modules/viewcompose-text-core/README.md
translation_source_hash: 84107565e779ccf8d0d86b371da43d9ccedf2b15724d7ccdfb5415e6a1f323e4
translation_status: current
---

# Text Core 文本内核模块

`viewcompose-text-core` 是 ViewCompose 的平台无关文本编辑模型。它定义不可变富文本文档、
方向性 selection、IME composition 快照、事务编辑缓冲区、带 undo/redo 的可观察文本字段状态、
输入转换、标准化 Receive Content 契约和带版本的保存编解码器。

本模块不包含 Android 类型。Android `Editable`、`InputConnection`、剪贴板、拖放和富文本
span adapter 位于 renderer/host 模块，并负责与这些契约相互转换。

## 构件与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-text-core:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。富文本和外部内容契约在 alpha 版本间可能演进。
- 平台：Kotlin/JVM library，目标 Java 11。
- 直接 ViewCompose 依赖：`viewcompose-runtime`，用于可观察的 `TextFieldState`。
- 平台边界：禁止 Android、View、resource 和 lifecycle 类型。

## offset 与 range 契约

所有文本 offset 都是 UTF-16 code-unit 索引。这与 Android `Editable` 和 `InputConnection`
一致，但不是 Unicode code point 或 grapheme cluster 索引。

`TextRange(start, end)` 会保留方向：从右向左选择时 `start > end`。需要有序范围的文档操作
使用 `min` 和 `max`。`TextRange` 本身只验证非负值；`TextDocument` 和 `TextFieldValue` 在接收
range 时验证其是否超出所属文本。

## 富文本文档

```kotlin
val document = textDocument {
    append("ViewCompose", TextSpanStyle(fontWeight = 700))
    append(" text")
    addParagraphStyle(
        range = TextRange(0, length),
        style = ParagraphStyle(lineHeightPx = 24f),
    )
    appendAttachment(
        InlineTextAttachment(
            id = "diagram",
            mimeType = "image/png",
            contentDescription = "Architecture diagram",
        ),
    )
}
```

`TextDocument` 不可变，并持有以下内容的复制快照：

- 纯文本，包括附件位置的 `INLINE_ATTACHMENT_CHARACTER` 对象替换字符；
- 字符级 `TextSpanRange`；
- 段落级 `ParagraphStyleRange`；
- 非文本内容的 `InlineAttachmentRange` 元数据。

样式范围必须有序且不超出文档。允许样式重叠，由平台 adapter 决定解析顺序。每个附件 offset
必须唯一，并指向对象替换字符。附件 URI 的加载与实际渲染不属于 text-core 职责。

替换文档内容时，会保留编辑范围外的 annotation、平移后续 range、只保留重叠样式中未被覆盖
的片段、把插入内容的 annotation 平移到目标位置，并移除占位符被替换的附件。

## 文本字段状态与编辑事务

```kotlin
val state = TextFieldState()

state.edit {
    replaceAll("Hello")
    selection = TextRange(0, 5)
}

state.edit {
    replace(selection.min, selection.max, "ViewCompose")
}

state.undo()
```

`TextFieldState` 是不可变 `TextFieldValue` 快照的稳定可观察 owner。其编辑缓冲区与历史栈限制在
所属 UI 线程使用。

- `edit` 是一个原子的业务编辑事务，并且不会经过用户输入 transformation。
- `TextFieldBuffer` 会保留文档 annotation，并在替换后迁移 selection/composition。
- 文档变化会结束活跃 IME composition、添加一个 undo 记录并清空 redo。
- 仅 selection 变化不会添加历史。
- 同一次 IME composition 的多次平台更新会在 composition 提交时合并为一个 undo 单元。
- undo/redo 恢复文档时不会恢复临时 IME composition。
- `historyLimit` 只限制 undo 栈，默认 100 条。

## 输入转换

`InputTransformation` 接收平台用户编辑提案的隔离 buffer。它可以改写提案，也可以通过
`revertAllChanges()` 拒绝：

```kotlin
val policy = InputTransformation.digitsOnly()
    .then(InputTransformation.maxCodePoints(6))

state.updateFromInput(proposedValue, policy)
```

链式 policy 共用同一个 buffer，并按声明顺序执行。`maxCodePoints` 按 Unicode code point 而非
UTF-16 unit 计数，因此不会拆开合法 surrogate pair。`digitsOnly` 使用 Kotlin 字符数字分类。

## Receive Content 接收内容

`ReceivedContent` 把剪贴板、拖放、输入法、自动填充或应用 payload 归一化为
`TextDocument`、来源、MIME 集合和原始平台 item 数量。

`ReceiveContentConfiguration` 持有非空 MIME 白名单及可选 `ReceiveContentTransformation`。
transformation 返回要插入的文档，或返回 `null` 拒绝整个 payload。MIME 值按结构比较，lambda
按身份比较。默认接受 `text/*` 和 `image/*`。

平台 adapter 仍负责 MIME 协商与 payload 归一化。接受的文档应通过与普通用户输入相同的事务
路径进入 `TextFieldState`，确保历史和输入 policy 一致。

## 保存与恢复

```kotlin
val saved: Map<String, Any?> = TextDocumentSaveCodec.encode(document)
val restored: TextDocument = TextDocumentSaveCodec.decode(saved)
```

带版本的 codec 使用 string、number、boolean、list 和 string-key map 保存文本、样式、段落和
附件。解码会校验完整结构，对不支持的版本、无效 enum、类型不兼容、错误 range 或附件约束
直接失败，不会静默丢弃富文本信息。IME composition 不属于文档保存格式。

## 相关文档

- [完整文本输入指南](https://docs.viewcompose.com/zh-CN/guides/text-input)
- [生命周期与状态保存架构](https://docs.viewcompose.com/zh-CN/architecture/lifecycle-and-saved-state)
- [状态快照架构](https://docs.viewcompose.com/zh-CN/architecture/state-snapshots)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-text-core` API 树](https://docs.viewcompose.com/api/viewcompose-text-core/current/)。

## 兼容性说明

`0.1.0-alpha01` 建立了 UTF-16 offset、不可变文档 annotation、编辑 buffer 迁移、IME
composition 历史合并、Receive Content 归一化和保存格式 version 1。不要持久化
`TextFieldState`、`TextFieldBuffer`、活跃 composition range、transformation 实例或平台
adapter；只持久化兼容 codec 明确编码的值。
