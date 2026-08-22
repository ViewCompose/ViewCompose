---
translation_source: architecture/decisions/0020-separate-animation-value-and-velocity-domains.md
translation_source_hash: a05d94f45608bc3b743d4138c233afd82a2a931b2c9c92bc6bb1e02f175372f6
translation_status: current
---

# ADR-0020：分离动画值域与速度域

- 状态：已接受
- 日期：2026-08-22
- 替代：ADR-0019 中单类型 `AnimationConverter<T>`、`AnimationVelocity<T>`、
  `AnimationState<T>`、`AnimationResult<T>` 与 `Animatable<T>` 的泛型细节

## 背景

ADR-0019 正确要求了类型化物理速度、目标缓冲区转换、解析式 Spring 采样、保留速度的重定向、
Decay、Bounds 和结构化结果。但它的临时类型词汇假定动画值与速度始终使用同一个 Kotlin 类型。

这个假设对合法的内置值域并不成立。打包 ARGB 值是一个 `Int`，物理速度却是四个可独立为负的
通道变化率。把这些变化率重新打包为 `Int` 会丢失符号和通道值域含义。整数位置也会重建为
`Int`，但其小于一个单位的速度必须保持为 `Float`，否则 Spring 延续与 Decay 会发生量化。
在公开结果中使用无类型 `FloatArray` 虽能保留数值，却会丢掉编译期的维度与单位安全。

Phase 1 尚未发布，因此保留这套临时单类型 API 会把已知无效的基础公开出去。Alpha 兼容策略要求
硬切，而不是并行 Adapter 或让速度载荷的含义依赖值类型。

## 决策

动画值和速度使用相互独立的泛型值域：

```kotlin
interface AnimationConverter<T, V> {
    val vectorSize: Int
    val zeroVelocity: V
    val visibilityThreshold: V

    fun convertToVector(value: T, destination: FloatArray)
    fun convertFromVector(vector: FloatArray): T
    fun convertVelocityToVector(velocity: V, destination: FloatArray)
    fun convertVelocityFromVector(vector: FloatArray): V
}

data class AnimationVelocity<V>(val valuePerSecond: V)

data class AnimationState<T, V>(
    val value: T,
    val velocity: AnimationVelocity<V>,
    val playTimeNanos: Long,
)

data class AnimationResult<T, V>(
    val endState: AnimationState<T, V>,
    val endReason: AnimationEndReason,
)
```

`AnimatableCore<T, V>`、Composition `Animatable<T, V>`、`TargetAnimation<T, V>` 与
`DecayAnimation<T, V>` 端到端携带两个类型。公开 Mutation Callback 和结果绝不把 `V` 擦除为
原始 Vector 或 `Any`。

内置映射如下：

| 值域 `T` | 速度域 `V` | 含义 |
| --- | --- | --- |
| `Float` | `Float` | 每秒值域单位 |
| `Int` | `Float` | 不受位置量化影响的每秒整数值域单位 |
| 打包 ARGB `Int` | `ArgbChannels` | 有符号 Alpha、Red、Green、Blue 切向分量 |
| `UiDp` | `UiDp` | 每秒密度无关像素 |

`ArgbChannels` 是公开不可变切向值，包含有符号 `alpha`、`red`、`green` 与 `blue` 分量。它
不是 Color，不能传给需要打包 ARGB 值的位置。`AnimationVelocity<ArgbChannels>` 把这些分量
解释为每秒通道单位，Converter Threshold 则把同一形状解释为通道单位差值。

值转换和速度转换使用同一个稳定 `vectorSize`，但每个方向拥有独立方法。端点、Bounds、值、
速度和阈值 Vector 每个 Evaluator 或 Mutation 只分配一次并复用。除了 Callback 契约要求的公开
不可变 State/Result 对象，内置标量求值不会在每帧增加引擎自有对象。

`visibilityThreshold` 属于 `V`，因为它同时提供分量形状和值域单位容差。位置平衡检查把转换后的
值位移与转换后的阈值分量比较；速度平衡检查把同一分量除以 ADR-0019 规定的 `0.016` 秒窗口。
每个阈值分量都必须有限且严格大于零。

这是一次硬切。不存在单参数 `AnimationConverter<T>` Alias、推断同域的兼容 Overload、打包颜色
速度 Adapter 或已弃用的单类型 `Animatable<T>`。内置 Converter 与 `rememberAnimatable` 仍能通过
类型推断保持简洁；自定义 Converter 必须显式声明两个值域。

ADR-0019 的其余决策继续有效，包括物理方程、取消、Last-writer Mutation 所有权、Bounds、终止
原因、Motion 缩放、Transition 所有权、工具隔离、Q3 分类和性能预算。

## 影响

- 无效的值/速度配对会在编译期失败，不再在运行时破坏 Motion。
- 整数与打包颜色的重定向可以保留有符号小数速度。
- 天然使用同一类型的自定义值域可以把同一类型写两次，无需额外 Wrapper。
- Alpha 调用方必须更新自定义 Converter 和显式 `Animatable` 类型声明；这里没有要保留的已发布稳定契约。
- 后续通用 Transition、Seek、手势接力和检查 API 会继承一个精确速度域，不需要新增功能专属的
  Vector 逃生口。

## 被否决的替代方案

### 复用 `T` 并在文档中说明例外

否决原因：文档无法让负的四通道速度表示为打包颜色 `Int`，运行时分支也会削弱编译安全。

### 把 `FloatArray` 公开为速度

否决原因：调用方可能提供错误维度、修改保留数据或混淆单位；这也会把引擎 Scratch 表示泄漏到
公开 State。

### 保留单泛型，仅增加 `ColorVelocity`

否决原因：整数动画同样存在位置量化问题，未来值域也可能拥有不同的切向类型。稳定抽象应是
Converter 关系，而不是颜色特例。

## 验证

Phase 1 必须为同域和异域 Converter 编译 Q3 Sample；往返保留有符号 ARGB 速度；在 Spring
重定向和 Decay 中保留整数小数速度；拒绝维度错误与非有限阈值；并通过 ADR-0019 要求的分配、
确定性 Clock、取消、Bounds、Demo、设备和同机性能证据。
