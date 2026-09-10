---
translation_source: modules/viewcompose-text-core/README.md
translation_source_hash: a9c76cc0cdfd7caa00d225ca58f672abf092bd3083a0992aac69b243914e3360
translation_status: current
schema_version: 2
document_id: module.viewcompose-text-core
doc_type: module
owner:
  kind: module
  id: viewcompose-text-core
version_lane: released
capability_ids:
  - text.editing-state
  - text.input
artifact_ids:
  - viewcompose-text-core
sample_ids:
  - module.text-core-dependency
  - module.text-core-document
  - module.text-core-state
  - module.text-core-transformation
  - module.text-core-save
coordinate: com.viewcompose:viewcompose-text-core:0.1.0-alpha04
minimal_usage_sample_id: module.text-core-state
---

# Text Core 文本内核模块

`viewcompose-text-core` 是 ViewCompose 的平台无关文本编辑模型。它定义不可变富文本文档、
方向性 selection、IME composition 快照、事务编辑缓冲区、带 undo/redo 的可观察文本字段状态、
输入转换、标准化 Receive Content 契约和带版本的保存编解码器。

本模块不包含 Android 类型。Android `Editable`、`InputConnection`、剪贴板、拖放和富文本
span adapter 位于 renderer/host 模块，并负责与这些契约相互转换。

## 构件与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="text-core-module-dependency" sample_id="module.text-core-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-text-core:0.1.0-alpha04")
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

{/* compiled-region source="viewcompose-text-core/src/test/samples/com/viewcompose/text/samples/TextCoreSamples.kt" region="text-core-module-document" sample_id="module.text-core-document" build_target=":viewcompose-text-core:compileTestKotlin" */}
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

{/* compiled-region source="viewcompose-text-core/src/test/samples/com/viewcompose/text/samples/TextCoreSamples.kt" region="text-core-module-state" sample_id="module.text-core-state" build_target=":viewcompose-text-core:compileTestKotlin" */}
```kotlin
val state = TextFieldState()

state.edit {
    replaceAll("Hello")
    selection = TextRange(0, 5)
}

state.edit {
    replace(selection.min, selection.max, "ViewCompose")
}

check(state.text == "ViewCompose")
check(state.undo())
check(state.text == "Hello")
```

`TextFieldState` 是不可变 `TextFieldValue` 快照的稳定可观察 Owner。其编辑缓冲区与历史栈限制在
所属 UI 线程使用。这是 Q3 State API：每次 Edit、Undo 或 Redo 都通过一次 Snapshot Transaction
发布完整 Value 及 `canUndo`/`canRedo` 可用性，因此 Observer 不会收到已提交文本与过期历史状态
混合的结果。

- `edit` 是一个原子的业务编辑事务，并且不会经过用户输入 transformation。
- `TextFieldBuffer` 会保留文档 annotation，并在替换后迁移 selection/composition。
- 文档变化会结束活跃 IME composition、添加一个 undo 记录并清空 redo。
- 仅 selection 变化不会添加历史。
- 同一次 IME composition 的多次平台更新会在 composition 提交时合并为一个 undo 单元。
- undo/redo 恢复文档时不会恢复临时 IME composition。
- `historyLimit` 只限制 undo 栈，默认 100 条。

在尚未发布的工作树中，文本值、撤销/重做列表和组合输入基线构成一个由快照管理的不可变状态。
外层快照被放弃或发生提交冲突时，这些内容均保持不变；固定版本的读取同时看到历史文本和对应的
历史可用性。历史操作复制有界的不可变值引用列表，文档内容继续共享。只改选区的程序编辑保留
待提交的组合输入基线。即使文档与上一次组合更新相同，结束组合输入仍会生成对应的撤销单元；
取消并回到基线则不添加记录。即使两个历史列表均为空，`clearHistory` 也会清除待提交的基线。

## 输入转换

`InputTransformation` 接收平台用户编辑提案的隔离 buffer。它可以改写提案，也可以通过
`revertAllChanges()` 拒绝：

{/* compiled-region source="viewcompose-text-core/src/test/samples/com/viewcompose/text/samples/TextCoreSamples.kt" region="text-core-module-transformation" sample_id="module.text-core-transformation" build_target=":viewcompose-text-core:compileTestKotlin" */}
```kotlin
val policy = InputTransformation.digitsOnly()
    .then(InputTransformation.maxCodePoints(6))
val state = TextFieldState()

state.updateFromInput(
    proposedValue = state.value.copy(
        document = TextDocument.plain("123456"),
        selection = TextRange(6),
    ),
    inputTransformation = policy,
)
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

{/* compiled-region source="viewcompose-text-core/src/test/samples/com/viewcompose/text/samples/TextCoreSamples.kt" region="text-core-module-save" sample_id="module.text-core-save" build_target=":viewcompose-text-core:compileTestKotlin" */}
```kotlin
val original = richTextDocumentSample()
val saved = TextDocumentSaveCodec.encode(original)
val restored = TextDocumentSaveCodec.decode(saved)

check(restored == original)
```

带版本的 codec 使用 string、number、boolean、list 和 string-key map 保存文本、样式、段落和
附件。解码会校验完整结构，对不支持的版本、无效 enum、类型不兼容、错误 range 或附件约束
直接失败，不会静默丢弃富文本信息。IME composition 不属于文档保存格式。

## 相关文档

- [文本输入运行时架构](../../architecture/text-input.md)
- [编辑、校验与提交文本](../../guides/text-input.md)
- [使用富文本与外部文本内容](../../guides/text-input-rich-text.md)
- [生命周期与状态保存架构](https://docs.viewcompose.com/zh-CN/architecture/lifecycle-and-saved-state)
- [状态快照架构](https://docs.viewcompose.com/zh-CN/architecture/state-snapshots)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-text-core` API 树](https://docs.viewcompose.com/api/viewcompose-text-core/current/)。

## 兼容性说明

`0.1.0-alpha02` 建立了 UTF-16 offset、不可变文档 annotation、编辑 buffer 迁移、IME
composition 历史合并、Receive Content 归一化和保存格式 version 1。不要持久化
`TextFieldState`、`TextFieldBuffer`、活跃 composition range、transformation 实例或平台
adapter；只持久化兼容 codec 明确编码的值。

## 当前检出版本的回归证据

2026-09-06 对基线 `d64710459df73f3b42067767bb2f4f273b9eff33` 的审查复现了两个文本历史问题：
结束未变更的组合输入时丢失撤销，以及放弃编辑快照后历史仍被修改。候选版本在相同 13 个模块的
源码集合上通过 854 项独立 JVM 测试，其中包含 12 项新增 `TextHistorySnapshotTest` 用例。
两个文本探针均达到预期，失败从 2 降至 0（这些探针减少 100%），结论为改进。
这证明事务和组合输入语义，不代表真机 IME 或编辑延迟验收。Renderer 的 `TextFieldControllerTest`
负责原生 `finishComposingText` 桥接验证；后续需要真机验证该桥接，并在性能结论前测量大历史记录负载。
