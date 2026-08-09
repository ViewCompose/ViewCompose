---
translation_source: guides/theming.md
translation_source_hash: 909e05a6650152a52a3e5d52c4da7920f48d10fc48fa794868b12b817f1898fa
translation_status: current
---

# ViewCompose 主题系统

## 1. 文档定位

本文档是主题系统规范版，定义：

1. 主题模型边界
2. 默认值解析链路
3. 局部覆盖规则
4. 新增主题能力时的落地约束

历史长版见：

- [THEMING_FULL_2026-03-06.md](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/THEMING_FULL_2026-03-06.md)

## 2. 当前主题模型

`UiThemeTokens` 当前核心字段保持为：

1. `colors`
2. `stateColors`
3. `typography`
4. `shapes`
5. `controls`
6. `interactions`
7. `overlays`
8. `metadata`

关键原则：

1. 顶层主题只承载语义 token，不承载每个组件的完整 resolved style。
2. 组件默认值在 `Defaults` 层按需从 `Theme` 派生，不做全量预计算。
3. 组件显式参数优先级高于主题默认值。

当前 token 语义补充：

1. `colors` 同时承载基础色、`on*` 前景色、`*Container` 容器色、轮廓色、逆表面色与 ripple。
2. `stateColors` 承载文本、普通控件、激活控件和交互高亮的 default/disabled/pressed/focused/checked/selected 状态。
3. `typography` 提供完整 15 角色 `display*/headline*/title*/body*/label*` 比例。
4. `shapes` 提供语义化 `extraSmall / small / medium / large / extraLarge / full` 六级形状；
   每个绝对形状完整表达四角、圆角/切角与绝对/百分比尺寸，`full` 表达相对边界的胶囊或圆形。
5. `controls` 仍是框架自有尺寸 token，不承诺与 Android 原主题系统一一对齐。
6. `interactions` 承载与设计系统无关的按下、聚焦和悬停状态层透明度。组件 Defaults 会把透明度
   与自身启用态内容角色组合后再发出已解析颜色；Renderer 不解释主题角色或透明度策略。
7. `overlays` 当前由语义 token 承载跨组件蒙层配置。
8. `metadata` 标记 token 来源、深色状态与配置修订号，用于生命周期刷新和诊断，不参与组件默认值推导。

## 2.3 Token 使用闭环

公开 token 不允许长期停留在“已定义但无消费”的状态。当前规则固定为二选一：

1. 至少被一个 core defaults/composite 默认值明确消费。
2. 被列入 whitelist，并在文档中说明原因。

当前 whitelist 仅保留暂时没有核心组件直接消费的 `reserved semantic palette`：

1. 扩展表面：`onBackground / surfaceDim / surfaceBright / surfaceContainer*`
2. 第三强调色：`tertiary / onTertiary / tertiaryContainer / onTertiaryContainer`
3. 预留错误容器：`errorContainer / onErrorContainer`，直到组件存在语义正确的容器错误处理
4. 反色与蒙层：`inversePrimary / scrim / surfaceTint`
5. 业务语义：`success / warning / info`

说明：本轮不强绑到现有核心组件，避免为了提高使用率污染语义。

为防止回流，仓库有 `ThemeTokenUsageAuditTest` 守卫：

1. 新增 token 时，若未消费也未加入 whitelist，测试必须直接失败。
2. defaults 若从语义 token 回退到旧 alias，也会在审计时暴露。

## 2.1 语义主入口规则

主题 token 扩展默认采用“语义主入口 + 一次性收口”：

1. 新字段一旦成为主语义入口，defaults 与 demo 必须同轮迁移。
2. 若旧字段只是历史别名，应在收口轮次直接移除，不继续长期并存。
3. 文档必须写明哪些字段属于正式语义入口，哪些字段仅是 reserved token。
4. 新增默认值逻辑只允许读取正式语义字段，不允许引入别名回流。

## 2.2 硬编码禁用清单

以下语义色禁止在 `Defaults` 里直接写字面量，必须走 `Theme.colors`：

1. 错误态（如 `0xFFB3261E`）统一使用 `Theme.colors.error`。
2. 徽标/提醒色统一使用 `Theme.colors.error` 或其他语义色，不允许组件私有常量重复声明。
3. 语义色文本前景统一通过 `contentColorFor(semanticColor)` 推导，不手写黑白常量。

## 3. 默认值链路

标准链路必须保持：

`Theme -> Defaults -> NodeSpec -> Renderer`

约束：

1. 不把主题直接变成通用 `Modifier`。
2. 不在 renderer 中写业务语义默认值。
3. 不在 DSL 层散落重复主题推导逻辑。
4. 复合组件内部文本必须把完整文本样式写入 `NodeSpec`，不能只下发 `textSizeSp`。
5. renderer 只负责应用 `NodeSpec` 中已经解析好的文本样式，不重新发明主题语义。
6. 组件需要分离有效触控目标与可见 Surface 时，主题同时提供两个尺寸，Defaults 把它们解析进
   `NodeSpec`，renderer 只应用已解析几何；应用显式 Surface Modifier 仍具有最高优先级。
7. Button、IconButton、边界明确的交互式组合控件和 SegmentedControl 状态层遵循同一链路：
   组件 Defaults 选择语义内容角色，`interactions` 提供状态透明度，`NodeSpec` 携带已解析
   ARGB 颜色，Renderer 应用“按下优先于聚焦、聚焦优先于悬停”的通用选择器。
   SegmentedControl 分别携带选中与未选中角色集合。

## 4. 局部覆盖（Override）规则

局部覆盖能力保留，但必须是稀疏覆盖：

1. 只覆盖必要字段
2. 未覆盖字段回落到上层主题或默认值
3. 覆盖逻辑通过 `LocalContext` 作用域传播
4. 对外统一通过 `UiLocal/uiLocalOf/ProvideLocal(s)/UiLocals.current` 使用，避免专用包装 API 漂移

适用场景：

1. 局部品牌色/强调色
2. 局部文本样式调整
3. 单区域对比度或可读性增强

非目标：

1. 把 override 做成“每个组件所有字段都能填”的全量配置
2. 用 override 替代组件参数

## 4.1 业务自定义 Local 扩展

当业务侧 token 体系与框架默认主题不一致时，允许按下面方式扩展：

1. 在业务模块通过 `uiLocalOf { ... }` 定义自有 token。
2. 在局部子树通过 `ProvideLocal(...)` 或 `ProvideLocals(...)` 注入。
3. 在组件内部通过 `UiLocals.current(...)` 读取。
4. 新增 Local 能力时优先复用统一 API，不再新增专用 `ProvideXxx` 包装。

边界约束：

1. 业务 Local 只承载语义值，不承载 renderer 平台实现细节。
2. Local 作用域恢复与 snapshot 传播语义必须保持（lazy/overlay 不回退）。

## 5. Material 3 Design System 边界

`viewcompose-material3` 只做 Android Material/AppCompat 语义到框架语义的映射，内部固定为：

`Material3ThemeSnapshotReader -> Material3ThemeTokenMapper -> UiThemeTokens`

其中：

1. `SnapshotReader` 负责批量读取 Android / AppCompat / Material 主题字段。
2. `ThemeTokenMapper` 负责把平台字段映射到框架 token，并处理 fallback。
3. bridge 不直接产出组件级默认值，不绕过 `Defaults` 层。
4. `viewcompose-android` 的 `ComponentActivity/Fragment.setUiContent` 默认解析并提供 Material 3 Theme；根容器、框架原生 View、`AndroidView` 与 Overlay 共用同一个解析上下文。
5. `setUiContent` 默认在平台支持时套用 Material 动态色；可通过 `Material3DynamicColorPolicy.Disabled` 显式关闭。底层直接组合时，使用 `viewcompose-material3` 的 `Material3ThemeBridge.resolveContext(...)` 与 `Material3Theme(...)`。
6. 组合内使用 Android 主题时会监听配置变化并重新读取 token；离开组合后注销回调，`metadata.revision` 随刷新递增。
7. 运行时调用 `setTheme/applyStyle` 后，可把 `Material3ThemeRefreshController` 传给 `setUiContent`，再在主线程调用 `refresh()`；控制器会重新解析动态色上下文并触发主题子树刷新。

当前 bridge 覆盖矩阵：

1. `colors`
   - 已桥接：`background / onBackground / surface / surfaceVariant / primary / secondary / tertiary / error`
   - 已桥接：`surfaceDim / surfaceBright / surfaceContainerLowest/Low/Container/High/Highest`
   - 已桥接：`onPrimary / onSecondary / onTertiary / onError`
   - 已桥接：`primaryContainer / secondaryContainer / tertiaryContainer / errorContainer`
   - 已桥接：`onPrimaryContainer / onSecondaryContainer / onTertiaryContainer / onErrorContainer`
   - 已桥接：`outline / outlineVariant / inverseSurface / inverseOnSurface / inversePrimary`
   - 已桥接：`onSurface / onSurfaceVariant`
   - 已桥接：`ripple`（优先读 `colorControlHighlight`）
   - `surfaceTint` 按 Material 3 颜色角色回落到 `primary`，不再错误借用 AppCompat `colorAccent`
2. `stateColors`
   - 已桥接：`android:textColorPrimary / textColorSecondary`
   - 已桥接：AppCompat `colorControlNormal / colorControlActivated / colorControlHighlight`
   - 标准状态：`disabled / pressed / focused / checked / selected`
   - 这些 Bridge 值仍是通用状态角色。Checkbox、RadioButton、Switch 与 Slider 的启用态默认值
     从 `colors.primary` 解析选中色，Slider 的非激活轨道从 `colors.secondaryContainer` 解析。
     这可避免 AppCompat Accent 别名混入 Material 语义配色，同时保留对
     `controlActivated` 的显式访问。
3. `typography`
   - 已桥接：全部 15 个 Material 3 `textAppearanceDisplay*/Headline*/Title*/Body*/Label*` 角色
   - 家族回退：旧 Android `textAppearanceLarge/Medium/Small` 只作用于 Title/Body/Label，
     不会折叠 Display 或 Headline 角色
   - 已桥接字段：`fontSizeSp / fontWeight / fontFamily / letterSpacingEm / lineHeightSp / includeFontPadding`
4. `shapes`
   - 已桥接：`shapeAppearanceCornerExtraSmall / Small / Medium / Large / ExtraLarge`
   - 已桥接：四角独立尺寸、`rounded/cut` corner family、dimension/fraction corner size
   - Android 的物理 left/right 会按当前布局方向转换为框架的逻辑 start/end
5. `overlays`
   - 已桥接：`android:backgroundDimAmount -> scrimOpacity`
6. `controls`
   - Android 原主题系统没有与 `compact / medium / large` 一一对应的统一来源
   - 因此由 `Material3ThemeDefaults` 提供固定的标准尺寸配置；Bridge 替换资源支持的颜色、
     排版和形状时继续保留该配置
   - Material 3 配置还会为 Checkbox、RadioButton、Switch 与 Slider 选择 48dp 最小有效高度。
     UI Foundation 会在调用方 Modifier 之前消费这个中性 Token，因此应用显式指定的精确尺寸
     仍具有最终权限
7. `interactions`
   - `Material3ThemeDefaults` 提供按下 `0.10`、聚焦 `0.10`、悬停 `0.08` 的透明度
   - Android Bridge 替换资源支持的语义颜色时继续保留该回退策略，因为 Android 主题没有暴露
     一套统一、完整的组件状态层透明度族

不做：

1. 在 bridge 层写组件业务默认值
2. 在 bridge 层引入组件级条件分支
3. 为了“看起来全覆盖”而猜测性映射控件尺寸

实现约束：

1. Material Bridge 的 fallback 必须显式落到 `Material3ThemeDefaults.light/dark()`，不能借用
   UI Foundation 的中性回退，也禁止散落字面量。
2. 新增桥接字段时，必须同时定义“读取来源 + fallback 规则 + token 归属”。
3. Bridge 新能力若改变可视结果，必须补 `Material3ThemeBridgeTest` 或 Material 3 侧桥接测试。

主动刷新示例：

```kotlin
val themeRefreshController = Material3ThemeRefreshController()

setUiContent(themeRefreshController = themeRefreshController) {
    // content
}

setTheme(R.style.AppTheme_Alternate)
themeRefreshController.refresh()
```

## 6. One UI 7 Alpha Design System 边界

`viewcompose-oneui7` 是显式选择的替代 Design System 产物，不会替换标准 Material 聚合入口。
它提供静态 Light/Dark `UiThemeTokens`，以及限定五组件 Alpha 所需的 Recipe 和自有组合组件。

```kotlin
setUiContent {
    OneUi7Theme(tokens = OneUi7ThemeDefaults.light()) {
        OneUi7Button(text = "Continue", onClick = { continueFlow() })
    }
}
```

这条边界有意不同于 Material Bridge：

1. `OneUi7ThemeDefaults` 不读取 Android 或 Samsung Resource，其中数值是 ViewCompose 对固定
   One UI 7 公开指南的解释。
2. `OneUi7Theme` 安装一份完整、不可变的 Foundation Token 与私有 Recipe 快照。
3. Button 与 Surface 使用共享 Basic Primitive；结构不同时，Switch、TextField 装饰和纯文字
   NavigationBar 仍由 Design System 自有组合组件实现。
4. Android Renderer 只接收已解析的通用 Node，不判断 One UI 身份。
5. 运行时切换使用新 Provider 替换根与 Session，不修改全局 Design System 对象。
6. `viewcompose-android` 为兼容性继续默认使用 Material 3；应用显式选择
   `viewcompose-oneui7`。

支持范围、一致性标签、降级和发布限制见
[One UI 7 五组件 Alpha 模块手册](../modules/viewcompose-oneui7/README.md)。

## 7. 与组件和 Modifier 的边界

1. 主题负责默认值来源
2. 组件参数负责语义表达
3. `Modifier` 负责通用外层修饰

对应规范：

- [Modifier 模型](../architecture/modifier.md)
- [NodeSpec 模型](../architecture/node-spec.md)

## 8. 新增主题能力的必经清单

新增主题字段或覆盖能力时，至少完成：

1. 模型归属判断：`tokens` / `defaults` / 组件参数
2. 优先级规则定义：默认值与显式参数冲突时谁生效
3. renderer 验证：样式变化可触发预期 patch/rebind
4. demo 验证：Light/Dark + 局部覆盖场景
5. 测试补齐：单测或 instrumentation 至少覆盖一种回归路径

当前主题语义的权威 demo 验证入口为 `Diagnostics -> 主题诊断`。`Foundations` 中的 theme/overrides/typography 页面继续保留为教学与示例入口，不承担最终人工回归口径。

## 9. 当前阶段重点

1. 保持主题模型稳定，不回退到“组件全量 token 预计算”。
2. 动态色、完整 15 角色排版、完整绝对形状映射与配置生命周期已落地；继续补多窗口/厂商主题
   的设备矩阵。
3. Button 与原生紧凑输入控件的触控目标现已采用测试保护的有效尺寸策略。Button、IconButton、
   Chip、FAB 变体、可点击 Surface/Card/ListItem/DropdownMenuItem 与 SegmentedControl 会从
   组件内容角色解析标准按下、聚焦和悬停状态层，而不会把 Material 策略放入 Android Renderer。
   Chip 触控目标扩展、TextField 浮动/Focus 行为、带显式 Ripple 覆盖的导航控件，以及原生输入
   控件的精确几何仍必须按测试优先处理；完整 Token Bridge 本身不会自动提供这些结构行为。
4. 与 `ROADMAP` 中 Overlay、Input、容器场景联动完善主题回归。

后续结构改造的顺序、证据与回退规则记录在英文的
[Material 3 设计收敛计划](https://docs.viewcompose.com/project/plans/material3-design-convergence)。

路线图见：

- [roadmap.md](../project/roadmap.md)
