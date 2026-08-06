---
translation_source: modules/viewcompose-constraintlayout-androidx/README.md
translation_source_hash: 952512563448f0fd4a80a8fd3f273df1a5d2b099de1fd7cc2545f8345e730887
translation_status: current
---

# AndroidX ConstraintLayout 集成模块

`viewcompose-constraintlayout-androidx` 为 ViewCompose 提供声明式 ConstraintLayout Node、Child
Constraint Modifier、可复用 Constraint Set 和 AndroidX Virtual Helper。

公开 API 根包为 `com.viewcompose.constraintlayout`。Maven 后缀用于表达 AndroidX 后端，不保留
已退役的 `com.viewcompose.widget.constraintlayout` 分类。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-constraintlayout-androidx:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。DSL 与 Native Mapping 已可用，高级 Helper 对齐仍可能演进。
- 平台：Android 7.0（API 24）及以上。
- 可选：`viewcompose-ui-foundation` 不依赖该产物。
- UI Contract 与 UI Foundation 会被传递暴露，因为它们的 Modifier、单位和 Builder 类型出现在
  公开 DSL 中；Runtime 保持为实现依赖。
- 原生引擎：AndroidX ConstraintLayout 及 Guideline、Barrier、Flow、Group、Layer、Placeholder。

## 内联约束

在 `ConstraintLayout` 内创建 Reference，用 `Modifier.constrainAs` 绑定 Child，并在 Constraint Scope
连接 Source Anchor：

```kotlin
ConstraintLayout {
    val (title, body) = createRefs("title", "body")
    Text("Title", Modifier.constrainAs(title) {
        startToStart(parent)
        topToTop(parent)
    })
    Text("Body", Modifier.constrainAs(body) {
        startToStart(title)
        topToBottom(title, margin = 8.dp)
    })
}
```

Reference 是一个 Layout 内的 String Identity。DSL 不验证空值与重复 ID。同一 Source Anchor 被重复
设置时，后者替换前者。`start`/`end` 跟随 Layout Direction；Top、Bottom、Baseline 是物理/原生 Anchor。

## 尺寸与定位

Width/Height 支持 Wrap Content、Fill-to-constraints、Match Parent 和固定 dp。Minimum/Maximum dp、
Percent Dimension、Constrained Wrap Content、Bias、Dimension Ratio、Baseline-to-edge 与 Circular
Positioning 映射到 AndroidX ConstraintSet。

Renderer 会把 Percent Dimension 与 Guideline Fraction 限制到 `0f..1f`。其他数值与 Ratio String
不在 DSL 层验证，直接交给 AndroidX。Circular Angle 使用 Android ConstraintLayout 的顺时针角度语义。

## 可复用 Constraint Set

`constraintSet { ... }` 构建不发射 UI 的不可变 `ConstraintSetSpec`，再传给
`ConstraintLayout(constraintSet = set)`。随后合并 Inline Constraint/Helper，同 ID 时 Inline 优先。
同一 Builder 中重复 Constraint ID 会替换旧值；Helper 在 Renderer 按 ID 合并前保持声明顺序。

## Virtual Helper 虚拟辅助对象

- Guideline 使用固定 dp Offset 或受限 Parent Fraction。
- Barrier 使用 Margin 与 Gone-widget Policy 跟踪逻辑/物理极值。
- Chain 保持 Reference 顺序，并验证 Weight 数量一致。
- Flow 映射 Orientation、Wrap、Style、Bias、Alignment、Gap、Padding 与最大换行数。
- Group 传播 Visibility 与 Elevation。
- Layer 传播 Visibility、Elevation、Rotation、Scale、Translation 与可选 Pivot。
- Placeholder 承载一个引用 Child，并定义空状态 Visibility。

Inline Helper 必须在 `ConstraintLayout { ... }` 求值期间调用，否则立即失败。Reusable Builder 版本不依赖
Thread-local Scope。Flow、Group、Layer 至少需要一个 Reference；Barrier 与 Chain 的空集合目前继续下传。

## Native 重建与失败

Native Container 会合并 Rebuild Request，并在需要时于 Measure/Layout 前应用最新 Merge Spec。Child 与
Helper String ID 会映射为稳定 Android View ID。Virtual Helper View 跟随最新 Helper Set 同步，不作为
DSL Child 暴露。

缺失 Reference、重复 Inline ID、覆盖与环形 Graph 会各记录一次日志。缺失 Link 会被跳过，其余 Constraint
继续应用。Native `ConstraintSet.applyTo` 失败会被捕获并记录，使 Render Session 存活，但 Layout 可能保留
部分或旧 Native State。应把这些 Warning 当作编写错误，并在真机测试复杂 Graph。

## 性能建议

- Graph 稳定而 Child Content 变化时复用 Constraint Set。
- 保持 Reference ID 与 Helper 声明顺序稳定，避免 Native Helper 抖动。
- 不需要约束求解时使用更简单 Container；ConstraintLayout 会引入 Solver Pass。
- 避免由高频 State 重建大型 Helper Graph。

## 相关文档

- [UI Foundation 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-ui-foundation)
- [Renderer 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-renderer-android)
- [UI Contract 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-ui-contract)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-constraintlayout-androidx` API 树](https://docs.viewcompose.com/api/viewcompose-constraintlayout-androidx/current/)。

## 兼容性说明

`0.1.0-alpha01` 建立 String Reference、Inline-over-external Merge、完整 Anchor/Dimension Mapping、
Virtual Helper、合并 Native Rebuild，以及错误 Graph 的 Warning 恢复。它不提供平台无关 Constraint Solver。
