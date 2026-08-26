---
translation_source: guides/text-input-rich-text.md
translation_source_hash: 1d7652133bc74cec88e1124ebd6bed4fb52ad096fc5b8e72930f1642178d0be7
translation_status: current
---

# 编辑与显示富文本

样式、段落、Link 或内联附件元数据必须在编辑后保留时，使用 `TextDocument`。模型不可变且与
平台无关；Android `Spannable` 只是 Adapter 细节。替换与 Offset 不变量见
[文本输入架构](../architecture/text-input.md)。

## 构建一份文档

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TextInputGuideSamples.kt" region="text-input-rich-text" sample_id="guide.text-input-rich-text" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.RichTextEditor() {
    val initialDocument = textDocument {
        append("ViewCompose", TextSpanStyle(fontWeight = 700))
        append(" editor\n")
        appendAttachment(
            InlineTextAttachment(
                id = "preview",
                mimeType = "image/png",
                uri = "content://example/preview",
                contentDescription = "Preview",
            ),
        )
    }
    val state = rememberTextFieldState(initialDocument)

    Column {
        RichText(state.document)
        TextField(
            state = state,
            linePolicy = TextFieldLinePolicy.MultiLine(minLines = 3, maxLines = 8),
        )
    }
}
```

`RichText` 与 `TextField` 使用同一套文档到原生 Adapter。编辑会用新的不可变文档更新状态；
显示端直接观察 `state.document`。

## 保留注解与附件

所有 Offset 都是 UTF-16 索引。使用 `TextDocumentBuilder.length` 和 `TextRange`，不要自行计算
用户感知字符数。替换会保留未受影响的注解、裁剪被覆盖范围、移动后续范围；只有附件的对象替换
字符被替换时，才会移除附件。

内联附件保存身份、MIME 类型、可选 URI 和无障碍说明。URI 加载是 Best Effort。没有 Decoder、
URI 无法解析或载荷不是图片时，会显示内联 Placeholder，但不会删除文档中的元数据。

## 接收外部内容

每个可编辑输入框默认接受 `text/*` 与 `image/*`。需要收窄 MIME 协商或同步校验归一化文档时，
提供 `ReceiveContentConfiguration`：

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TextInputGuideSamples.kt" region="text-input-receive-content" sample_id="guide.text-input-receive-content" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.SharedContentField() {
    val state = rememberTextFieldState()
    val policy = ReceiveContentConfiguration(
        mimeTypes = setOf("text/*", "image/png"),
        transformation = { received ->
            received.document.takeIf { it.text.length <= 4_000 }
        },
    )

    TextField(
        state = state,
        linePolicy = TextFieldLinePolicy.MultiLine(),
        receiveContent = policy,
    )
}
```

剪贴板粘贴、拖放、IME `commitContent` 和应用 Receive Content 调用共享一个 Listener。同步
Transformation 返回文档表示插入，返回 `null` 表示拒绝归一化 Payload；它不能保留平台数据、
加载 URI、阻塞 I/O 或启动工作。接受的内容经过 `InputTransformation`，替换选区、结束组合输入
并形成一个撤销单元。不支持的 Clip Item 会作为 Remaining Content 返回平台。

## 验证任务

通过 `./gradlew :samples:tutorials:compileDebugKotlin` 编译，然后验证：

1. 在 Styled Range 前方和内部插入文本；未受影响的样式必须正确移动或裁剪；
2. 在附件旁编辑；身份与说明必须继续绑定同一个对象字符；
3. 使用不可用的附件 URI；必须出现 Placeholder，且文档元数据不丢失；
4. 重建 Activity；富文本注解、附件和选区必须恢复；
5. 粘贴 Styled Text 并拖入图片；每份接受的 Payload 必须作为一次可撤销编辑替换选区，混合载荷
   中不支持的 Item 仍可返回平台；
6. 输入双向和补充平面 Unicode 文本；选区和编辑必须使用原生 UTF-16 位置，不能崩溃或拆分代理项对。

样式被压平、共享状态包含 Android 类型、附件元数据丢失或把 Code Point Offset 当作文档索引，
都属于集成失败。
