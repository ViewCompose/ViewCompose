---
translation_source: guides/shadows.md
translation_source_hash: 036171c8286fee6358ceb05bccbfc92382a3d88451090c58a3e4968e08dc5f75
translation_status: current
---

# 添加精确的外阴影和内阴影

Material 或平台高程语义使用 `elevation`，兄弟节点顺序使用 `zIndex`。只有设计需要精确的颜色、
模糊、扩散、偏移、形状或有序图层时，才使用 `dropShadow(s)` 或 `innerShadow(s)`。

## 构建一个精确阴影表面

内容和阴影轮廓必须一致时，应给两者传入同一个显式形状。单数 Modifier 是单层便捷入口，复数
Modifier 保留列表顺序；空列表是 no-op。

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ShadowGuideSamples.kt" region="shadow-card" sample_id="guide.shadow-card" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.ShadowCard() {
    val cardShape = UiShape.rounded(20.dp)

    Surface(
        modifier = Modifier
            .shape(cardShape)
            .dropShadows(
                shadows = listOf(
                    UiShadow(
                        color = 0x33000000,
                        blurRadius = 12.dp,
                        offsetY = 5.dp,
                    ),
                    UiShadow(
                        color = 0x223B82F6,
                        blurRadius = 18.dp,
                        spreadRadius = 2.dp,
                        offsetX = (-4).dp,
                    ),
                ),
                shape = cardShape,
            )
            .innerShadow(
                shadow = UiShadow(
                    color = 0x44000000,
                    blurRadius = 8.dp,
                    offsetY = 3.dp,
                ),
                shape = cardShape,
            )
            .padding(20.dp),
    ) {
        Text("Exact outer and inner shadows")
    }
}
```

外阴影紧邻原生子内容之前绘制。内阴影在子节点的背景、内容、子树和前景之后绘制，但不会进入
命中测试。两者都不改变测量或布局，因此要用 Margin 或父级间距为外部模糊保留可见空间。普通
框架容器允许溢出；Lazy 容器会按设计在视口边缘裁切。

## 保持栅格工作稳定

平移、缩放、旋转、透明度、普通失效以及不变的尺寸会复用同一栅格标识。尺寸、密度、布局方向、
形状或图层变化会产生新标识。优先为变换和透明度添加动画；持续改变模糊、扩散、形状或尺寸前，
必须准备真机与内存预算证据。

## 启用并验收 Android 后端

将 `viewcompose-shadow-android` 打包进应用，其服务注册会在第一次使用时启用渲染。没有该产物时，
这些 Modifier 会按设计成为 no-op，树中的其他内容仍会正常渲染。只有应用启动阶段必须在第一个
装饰节点之前确定后端可用性时，才需要显式安装。

验收 `Catalog -> Graphics -> Outer shadows`、`Inner shadows` 与 `Lazy/diagnostics`。确认图层顺序、
透明输入、预期的视口裁切以及重复绘制后的缓存命中。后端选择、缓存预算、平台回退、诊断与基准
约束由 [Android 阴影模块手册](../modules/viewcompose-shadow-android/README.md)负责；Renderer 顺序与
可选后端所有权由 [Modifier 架构](../architecture/modifier.md)负责。
