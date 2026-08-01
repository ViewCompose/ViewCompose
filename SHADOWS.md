# ViewCompose Advanced Shadows

## 1. 能力定位

高级阴影建立在 Android 原生 View 内容之上，但不依赖 `View.elevation` 的 OEM 阴影实现。
它适合需要精确颜色、blur、spread、二维 offset、非矩形 shape 或有序多层合成的场景。

三类概念保持独立：

1. `elevation`：Material/平台高程语义，映射 `View.elevation`。
2. `zIndex`：兄弟节点绘制顺序。
3. `dropShadow(s)/innerShadow(s)`：像素规格明确的视觉装饰，不改变布局和输入。

## 2. 公开 API

```kotlin
val shape = UiShape.rounded(20.dp)

Surface(
    modifier = Modifier
        .shape(shape)
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
            shape = shape,
        )
        .innerShadow(
            shadow = UiShadow(
                color = 0x44000000,
                blurRadius = 8.dp,
                offsetY = 3.dp,
            ),
            shape = shape,
        ),
) {
    Content()
}
```

`dropShadow` 与 `innerShadow` 是单层便捷入口；复数入口接受有序列表。后声明的内阴影层
绘制在先声明层之上。空列表是 no-op。

## 3. Android 绘制模型

`viewcompose-shadow-android` 是可选后端，不替换 TextView、EditText、RecyclerView，也不为每层阴影创建
额外业务 View。renderer 把逻辑规格提交给 Decoration SPI，阴影后端解析像素规格，框架父容器按以下顺序绘制：

```text
outer shadow decoration
native child background/content/subtree/foreground
inner shadow decoration
```

外阴影跟随 child 的最终位置、matrix 与 alpha；仅 translation/scale/rotation/alpha 变化时
复用原栅格。内阴影位于视觉前景，但不进入 hit test，因此不会拦截点击、焦点、IME 或手势。

阴影不参与 measure/layout。调用侧需要为外阴影保留视觉间距；普通容器允许 child 装饰溢出，
Lazy viewport 仍负责裁切离开视口的内容。

renderer 与 Android host 不依赖阴影模块。依赖中没有 `viewcompose-shadow-android` 时，阴影 modifier
稳定降级为 no-op，其余渲染能力继续工作；加入模块后可由 ServiceLoader 自动发现，也可在应用启动时显式调用：

```kotlin
ShadowDecorationLayer.install()
```

`setUiContent` 的必要根容器直接具备通用装饰协议，不再额外嵌套阴影 FrameLayout。普通页面没有活跃
阴影 child 时，父容器只做一次布尔快速判断并直接进入原生 `drawChild`，不会逐 child 查询阴影标签；
没有非零 `zIndex` 时也不会启用自定义 child drawing order 或创建排序索引。

## 4. Shape 与多层语义

1. 显式传入 `shape` 时，阴影使用该轮廓。
2. 未传入时依次使用节点 `shape`、`cornerRadius`，最后回退矩形。
3. 支持 rounded/cut 与四角独立尺寸；density 与 RTL 在 Android 边界统一解析。
4. 同一次 `dropShadows/innerShadows` 调用中的所有层共享 shape，并严格保留列表顺序。
5. `spreadRadius` 可为负值，但收缩后的有效 mask 为空时不会产生可见结果。

## 5. 缓存与后端

静态外阴影和内阴影各自使用进程级有界栅格缓存。key 覆盖：

- View 尺寸
- density 与 layout direction
- shape
- 每层 color/blur/spread/offset

过大栅格会按预算跳过，淘汰和超预算次数可通过 `ShadowDecorationLayer.cacheStats()` 与
`innerCacheStats()` 读取。内存压力或测试可调用 `clearCache()`；业务页面不应在普通重组中清空缓存。

后端策略：

| 策略 | 当前行为 |
| --- | --- |
| `Auto` | 默认选择 `ExactBitmap` |
| `ExactBitmap` | 直接绘制缓存位图，API 24+ 精确基线 |
| `RenderNodeDisplayList` | API 29+ 显式实验；软件 Canvas/API 不满足或运行失败时回退 Bitmap |

Samsung SM-G991B / Android 13 的首轮发布态配对基准没有证明 RenderNode 在列表和复杂布局中
具备稳定收益，因此不得把实验策略改成默认值。动态 `RenderEffect` blur 仍是研究项。

## 6. 性能使用规则

1. 静态规格和稳定尺寸最容易命中缓存；Lazy 项应使用稳定 key 与共享 content type。
2. 动画优先修改 translation/scale/rotation/alpha，这些属性不会改变栅格 key。
3. 不要逐帧动画 blur、spread、shape 或尺寸；这些变化会持续创建新 key，并增加离屏内存压力。
4. 大面积、多层或动态模糊必须先建立设备基准和内存预算，不能直接进入默认组件。
5. 后端结论只接受同设备、同构建、同工作负载的多轮数据，并使用 Compose 控制组归一化。

基准与首轮数据见 [PERFORMANCE.md](./PERFORMANCE.md) 和
[ADVANCED_SHADOW_EXECUTION_PLAN_2026-07.md](./docs/ADVANCED_SHADOW_EXECUTION_PLAN_2026-07.md)。

## 7. 诊断与验证

人工入口：

1. `Catalog -> Graphics -> 外阴影`
2. `Catalog -> Graphics -> 内阴影`
3. `Catalog -> Graphics -> Lazy/诊断`

诊断页可以切换 Auto/Bitmap/RenderNode、清空缓存、刷新实际 backend/选择原因，并观察外/内
cache hit、miss、eviction、oversized skip 与缓存字节数。页面退出时会恢复 `Auto`，避免实验策略
泄漏到其他 Demo。

自动化入口：

```bash
./gradlew :app:testDebugUnitTest

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.AdvancedShadowDemoDeviceTest
```

设备测试验证单/多层规格、内阴影输入透明性、Auto 实际后端和 Lazy 重复绘制缓存命中。
