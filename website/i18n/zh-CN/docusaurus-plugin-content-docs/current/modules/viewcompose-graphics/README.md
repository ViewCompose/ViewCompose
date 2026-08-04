---
translation_source: modules/viewcompose-graphics/README.md
translation_source_hash: 32dbdfd0dee40392d00c40a76c10cf26494b27d7be283be760b64a77b787adb7
translation_status: current
---

# Graphics 模块

`viewcompose-graphics` 把 ViewCompose 自定义绘制暴露给 UI Tree。它提供专用 `Canvas` 节点、
Draw-behind 与 Content-aware Modifier、感知缓存的命令构建，以及平台无关 Graphics Contract 的
便捷别名。Android 执行由 Renderer 提供。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-graphics:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。Node 和 Modifier 契约已经审查并测试；Renderer 保真与高级绘制 API 仍可能
  在 Alpha 版本间演进。
- 平台：Android Library 与 ViewCompose 组合集成。
- 它传递暴露 Graphics Core、UI Contract 和 Widget Core。
- 仅需平台无关 Command/Model 代码时使用 `viewcompose-graphics-core`。

## Draw 执行模型

Draw Callback 在 Android View 绘制阶段、UI 线程上录制 `DrawCommand`。它们不是组合回调，可以
在不重组的情况下执行很多次。未缓存的 Callback 应减少分配，不能执行阻塞 I/O 或逐帧启动工作。

`DrawContext.size` 使用物理像素，`density` 是每 dp 的像素数。`Canvas` 节点不会从 Command 推导
固有尺寸；Parent Constraint 或 Layout Modifier 必须提供尺寸。

```kotlin
Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) { context ->
    drawCircle(
        center = Offset(context.size.width / 2f, context.size.height / 2f),
        radius = minOf(context.size.width, context.size.height) / 2f,
        paint = DrawPaint(brush = Brush.SolidColor(0xFF6750A4.toInt())),
    )
}
```

## Draw Modifier 绘制修饰

`drawBehind` 在节点正常内容前录制命令。`drawWithContent` 显式控制下游内容调用：省略
`drawContent()` 会隐藏内容，在调用前后录制可形成背景与前景层。Modifier 顺序仍有影响。

这些函数的 `key` 在 Reconciliation 中标识 Modifier Element，不缓存 Command；逻辑 Draw 行为
不变时应保持稳定。`draw` 是 `drawBehind` 的简洁别名。

## 感知缓存的绘制

`drawWithCache` 为已挂载 Modifier 拥有一个 Renderer Cache。外层 Modifier Key 标识 Element；
内层 `cache(key) { ... }` Key 控制命令复用。Key 必须包含所有可能改变结果的输入，尤其是物理
Size、Density、Theme Token、Resource Identity 与 Layout Direction。

```kotlin
val modifier = Modifier.drawWithCache { context ->
    cache(key = context.size) {
        val outline = path {
            moveTo(0f, 0f)
            lineTo(context.size.width, 0f)
            lineTo(context.size.width, context.size.height)
            close()
        }
        listOf(DrawCommand.DrawPath(outline))
    }
}
```

Cache 是单条目且受 UI 线程约束。只在 Builder 内读取 State 不会自动失效；应把可观察值放入语义
Cache Key。Renderer 释放会释放 Mounted Cache。`drawCache` 是 `drawWithCache` 的别名。

## Android Renderer 行为

Android Executor 使用 `android.graphics.Canvas` 回放命令，并为每次操作创建 `Paint` 请求。Alpha
限制到 `0f..1f`。Android 10 及以上使用 Framework `BlendMode`；旧版本使用最接近的
`PorterDuff` 映射。Blur Filter Chain 会合并 x/y Radius 并通过 `BlurMaskFilter` 应用，不是通用
RenderEffect Pipeline。

`ImageRef.stableId` 当前在类型为 Android `Bitmap` 或 `Drawable` 时解析；不支持的 ID 不绘制。
Bitmap Src/Dst Rect 转成整数 Bounds。Drawable Bounds 会临时替换后恢复。`DrawText` 在 Baseline
Origin 使用 Android `Canvas.drawText`，不增加换行或富文本布局。

这些细节描述当前 Renderer，不是自定义 Backend 保证。精确 API Level 保真应使用 Renderer 测试。

## 测试自定义绘制

- 分开测试 Command 生成和 Android 像素输出。
- 验证 Cache Key 包含测量 Size 与 Density。
- 覆盖 Modifier 顺序和 `drawContent()` 的省略/位置。
- Blend、Gradient、Blur、Bitmap/Drawable 与 Text 保真使用 Renderer 或截图测试。
- 重复 Invalidate 节点，在不重组的情况下分析 Callback 分配。

模块测试验证 Canvas Node 发射，以及 Draw-behind、Content-aware 和 Cache-aware Modifier Element
的编码顺序。

## 相关文档

- [Graphics Core 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-graphics-core)
- [Renderer 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-renderer)
- [Widget Core 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-widget-core)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-graphics` API 树](https://docs.viewcompose.com/api/viewcompose-graphics/current/)。

## 兼容性说明

`0.1.0-alpha01` 建立 UI 线程命令录制、无固有尺寸 Canvas Node、有序 Content Drawing、显式
Single-entry Cache Key、Android Canvas 回放与当前 API Level 回退。它是即时录制模型，不是保留式
Vector Scene Graph。
