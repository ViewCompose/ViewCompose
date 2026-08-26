---
translation_source: architecture/text-input.md
translation_source_hash: cd094bb84bb94526334d125fc543a47300d217dc2933df039caa07b95f1bfd2c
translation_status: current
---

# 文本输入运行时架构

## 1. 所有权边界

`viewcompose-text-core` 拥有平台无关的文档与编辑模型：

- 不可变 `TextDocument` 内容、Span、Paragraph、Link 和内联附件元数据；
- 方向性 `TextRange` 选区和临时 Composition Range；
- `TextFieldState`、事务 Buffer、有界撤销/重做以及输入 Transformation；
- 归一化 Receive Content 值和带版本的文档保存 Codec。

UI Foundation 负责记忆状态以及 `BasicTextField`、`TextField`、`SearchBar` DSL。Android
Renderer 负责 `Editable`、`Spannable`、`InputConnection`、剪贴板、拖放、Autofill 和 IME
适配。该架构不替换原生文本布局，也不让 Android Span 或 Content Payload 类型进入 Text
Core。

## 2. 文档与 Offset 模型

`TextDocument` 存储 UTF-16 文本以及字符、段落和内联附件范围。公开 Offset 使用 UTF-16，
因为它与 Android `Editable` 和 `InputConnection` 精确对应；它不是 Unicode Code Point 或
Grapheme Cluster 索引。`TextRange` 保留方向，因此从末端到起点的选区不会在存储时归一化。

文档替换会裁剪与替换区域相交的注解、移动后续范围，并原子插入替换文档的注解。每个内联
附件拥有一个对象替换字符和不可变元数据。URI 解码与 Drawable 生命周期留在平台 Adapter。

`TextFieldValue` 组合一份文档、选区与可选组合区。`value.text` 只是便捷投影，不是第二数据
源。`TextFieldState.edit` 发布一个完整值；`updateFromInput` 在提交前通过
`InputTransformation` 评估平台输入提案。

## 3. 编辑与历史事务

文本编辑限制在 UI 线程。应用编辑绕过 Input Transformation；文档变化会结束组合输入、建立
一个撤销项并清空重做。只有选区变化时不增加历史。平台组合输入会在 IME Commit 时合并成一个
撤销单元。撤销与重做会恢复文档和选区，但不会复活已失效的组合 Session。

该区分避免应用替换被校验策略拒绝，也避免中间文本、选区或历史状态变为可观察值。

## 4. Android 桥接不变量

Renderer 创建原生 `ViewComposeEditText`，并通过 `AndroidTextFieldController` 绑定。桥接必须
遵守：

1. 原生编辑保持同步；文本、选区和组合区作为一个快照读取。
2. InputConnection Mutation 与 Batch Edit 建立事务边界。
3. 原生编辑更新 `TextFieldState`；由此产生的重组不能把相同值写回。
4. 外部状态变更使用最小 `Editable.replace()` 范围。
5. 刷新框架文档 Span 时不能删除 IME 或平台拥有的 Span。
6. 框架写入在抑制反馈回调时恢复选区与组合区。
7. 只有确有必要时，Input Type 或 Editor Option 变化才重启当前 Input Connection。
8. `EditorInfo` 发布配置的 Receive Content MIME 类型。
9. Renderer 回滚重新绑定上一个已提交的文档、选区和组合区。

原生 View 继续拥有 Glyph Shaping、Bidi、断行、光标与选区 Handle、硬件键盘、拼写检查、
Autofill、无障碍文本遍历和手写笔输入。

## 5. Receive Content 与富文本显示

剪贴板粘贴、拖放、IME `commitContent` 和应用 `ViewCompat.performReceiveContent` 进入同一条
归一化路径。Styled/HTML 文本转成 `TextDocument`；URI Item 转成内联附件。支持的内容作为一个
输入事务和撤销单元替换当前选区。不支持的平台 Item 会作为 Remaining Payload 返回，而不是
被静默丢弃。

`RichText` 和可编辑输入框共享同一个 Document-to-`Spannable` Adapter。无法解析或非图片 URI
会绘制 Placeholder，同时保留文档元数据，因此异步图片加载不会取得文本状态所有权。

## 6. 持久化与 Session 生命周期

`rememberTextFieldState` 使用 Host Saveable State Registry。它持久化带版本的文档编码与方向
选区；不会持久化当前 IME 组合区、撤销/重做栈、焦点或键盘可见性，这些值属于一个活动 Window
和 Input Connection。

状态实例必须在一个逻辑编辑器生命周期内保持稳定。即使可见文本相等，在重组时重建它也会破坏
选区、组合输入、历史和原生 Controller 身份。

## 7. 验证边界

确定性测试负责文档替换、Transformation、历史、保存恢复、`Spannable` 往返、Receive Content
插入、Editor Metadata、最小外部 Patch 和 Renderer 回滚。代表性的中文/日文 IME、硬件键盘、
TalkBack、Autofill Service 和手写笔输入仍需设备验收，因为其行为属于平台实现。

具体任务见[编辑与 IME 策略](../guides/text-input.md)以及
[富文本与外部内容](../guides/text-input-rich-text.md)。未来交付优先级归项目
[路线图](../project/roadmap.md)所有，不进入本架构契约。
