---
translation_source: guides/text-input.md
translation_source_hash: a35c99f3d9b2305fe0790bc2f6424d3cd29fa26d94ee613101b4e2dcaa5a115a
translation_status: current
---

# 编辑与校验文本

每个逻辑编辑器使用一个稳定的 `TextFieldState`。它是富文本文档、方向性选区、当前 IME
组合区以及撤销/重做历史的唯一权威所有者。本任务覆盖普通输入框和搜索提交；持久状态与
Android 桥接不变量见[文本输入架构](../architecture/text-input.md)。

## 绑定可编辑状态

`rememberTextFieldState` 会在 Host 重建后保留文档和选区。直接读取它的可观察属性，不要把
文本复制到另一个由回调维护的值中。

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TextInputGuideSamples.kt" region="text-input-editing" sample_id="guide.text-input-editing" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.EditableSearchForm(onSearch: (String) -> Unit) {
    val query = rememberTextFieldState()
    val name = rememberTextFieldState()

    Column {
        SearchBar(
            state = query,
            placeholder = "Search",
            onSearch = onSearch,
        )
        TextField(
            state = name,
            label = "Display name",
            supportingText = "Up to 24 characters",
            inputTransformation = InputTransformation.maxCodePoints(24),
        )
        Button(
            text = "Undo",
            enabled = name.canUndo,
            onClick = { name.undo() },
        )
    }
}
```

只有提供 `onSearch` 时，`SearchBar` 才会选择 Search IME Action，并把最新的 `state.text`
传给回调。它自身不会防抖、清空或提交。`TextField` 负责选择外观、输入用途和行策略，不会
创建第二份编辑状态。

## 区分用户编辑与应用编辑

`InputTransformation` 只评估平台提出的用户编辑。需要顺序组合时使用 `then`；后一个
Transformation 会看到前一个的结果。`maxCodePoints` 按 Unicode Code Point 计数，不会拆开
合法代理项对。

应用变更使用一个显式
`state.edit { replace(0, length, replacement); selectAll() }` 事务。

一次 `edit` 只发布一次状态变更并建立一个撤销单元。只有选区变化时不增加历史。不要让程序
编辑经过输入策略：应用校验与平台输入过滤的所有权不同。

## 选择组件层级

- 带 Label 的应用表单和已解析 Design System 默认值使用 `TextField`。
- 单行查询及可选 Search 提交使用 `SearchBar`。
- 只有 Design System 已经解析出完整 `BasicTextFieldStyle` 时才使用 `BasicTextField`；它不会
  读取 Theme 或组件 Local。

注解、内联附件、剪贴板、拖放或 IME 内容必须在编辑后保留时，使用
[富文本与外部内容](./text-input-rich-text.md)。

## 配置键盘与 IME Action

`TextFieldInputProfile` 组合键盘选项与 Autofill 语义；`TextFieldLinePolicy` 独立拥有可视行
策略。使用这些值，不要建立 Password、Email、Number 或 Text Area 组件 Wrapper。

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TextInputGuideSamples.kt" region="text-input-ime" sample_id="guide.text-input-ime" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.EmailSubmissionField(onSubmit: (String) -> Unit) {
    val email = rememberTextFieldState()

    TextField(
        state = email,
        label = "Email",
        inputProfile = TextFieldInputProfile(
            keyboardOptions = TextFieldKeyboardOptions(
                keyboardType = TextFieldType.Email,
                imeAction = TextFieldImeAction.Done,
            ),
            autofillHints = TextFieldInputProfile.Email.autofillHints,
        ),
        onKeyboardAction = { action ->
            if (action == TextFieldImeAction.Done) {
                onSubmit(email.text)
                true
            } else {
                false
            }
        },
    )
}
```

只有应用已处理的 Action 才返回 `true`；`false` 会保留原生降级。修改 Input Type 或 Editor
Option 可能重启当前原生连接，因此只有产品状态变化时才修改 Profile。相等输入下的重组必须保留
选区与组合输入。

## 验证任务

通过 `./gradlew :samples:tutorials:compileDebugKotlin` 编译，然后验证：

1. 输入并选择文本；重组不能移动光标或结束当前组合输入；
2. 通过键盘超过 Code Point 上限；输入提案必须直接被拒绝，不能短暂发布无效值；
3. 触发 Search；回调必须收到最新可见查询；
4. 执行并撤销一次应用编辑；文档与选区必须作为一个快照恢复；
5. 确认预期键盘、Autofill Category 与 Action；提交只读取一次最新文本，未处理 Action 保留原生降级；
6. 重建 Activity；文档和选区恢复，但组合区和撤销历史不恢复。

并行 `String`、光标丢失、提交旧值、程序编辑被 Transformation 处理或恢复 IME Session，都
属于集成失败。
