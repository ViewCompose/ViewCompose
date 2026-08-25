---
translation_source: architecture/modifier.md
translation_source_hash: c0642810660780fa571fd777fbd81f4f108b09270f071aed15f449a9392c0e98
translation_status: current
---

# Modifier 架构

## 1. 文档定位

本文档定义 `Modifier`、组件 `NodeSpec`、`Theme/Defaults` 的当前边界。

目标是保证新增能力时落点明确，避免语义混放。

## 2. 当前基线（2026-08）

1. identity 入口统一为 `Modifier`（`Modifier.Empty` 已移除）
2. 文本语义类历史 modifier（如 `textColor/textSize`）已退场
3. `weight/align/FlexibleSpacer` 仅通过 `RowScope/ColumnScope/BoxScope` 暴露
4. 系统栏/键盘 Inset 适配可使用物理 `Modifier.systemBarsInsetsPadding(...)` / `Modifier.imeInsetsPadding(...)` 或感知方向的 `Relative` 形式（若 Activity 使用 `adjustResize`，通常不再叠加 IME Padding，避免双重位移）
5. 列表容器策略已收口为容器参数：`reusePolicy`（`sharePool`）与 `motionPolicy`（`disableItemAnimator/animateInsert/animateRemove/animateMove/animateChange`）；Pager 驻留与直接用户输入仍是 Pager 参数
6. 焦点编辑器可见性是真实 Android 滚动所有者的不变量，不是 Modifier 或容器 Boolean。LazyColumn、LazyVerticalGrid 与 ScrollableColumn 即使禁用直接用户滚动，也会保留原生子矩形传播
7. HorizontalPager 与 VerticalPager 只负责离散页面选择。可能被 IME 遮挡的页面必须声明页内滚动所有者；Pager 绝不把页内坐标解释成页面运动
8. 背景资源支持 `Modifier.backgroundDrawableRes(resId)`；与 `backgroundColor` 同时存在时，drawable 优先；当同时存在 `cornerRadius` 时自动裁剪内容，`clip()` 仍可作为通用强制裁剪开关
9. 内容尺寸动画支持 `Modifier.animateContentSize(...)`；renderer 会在 patch 前自动插入 `AnimatedSizeHost`，以“真实测量尺寸插值”参与父布局重排（非 graphicsLayer 视觉缩放），并保留 `AnimationSpec` 的 easing/spring/keyframes/repeat 语义（含 reverse 终态）
10. 约束 parent-data 支持 `Modifier.layoutId(...)`、`Modifier.constrainAs(...)`、`Modifier.constrain(...)`；仅对 `ConstraintLayout` 子节点生效
11. 图形绘制 modifier 已接入：`Modifier.drawBehind`、`Modifier.drawWithContent`、`Modifier.drawWithCache`（以及短写 `draw/drawCache`）；执行顺序按 modifier 链稳定，`drawWithContent` 可显式控制内容透传；底层执行保证 `DrawRoundRect` 四角半径与 `Drawable + DrawPaint` 组合语义不丢失
12. 声明式焦点与硬件键盘输入已接入：`focusable/focusRequester/focusProperties/focusGroup/onFocusChanged/onPreviewKeyEvent/onKeyEvent` 映射原生 View 焦点搜索，并由 `LocalFocusManager` 提供会话级移动与清除能力
13. 统一嵌套滚动协议已接入：`Modifier.nestedScroll(connection, dispatcher)` 通过透明宿主映射 AndroidX nested-scrolling parent/child 链，覆盖 pre/post scroll、pre/post fling、Lazy/Pager/普通滚动容器与自定义 drag/transform pan
14. 高级阴影已接入：`dropShadow/dropShadows` 绘制在节点内容之前，`innerShadow/innerShadows` 绘制在完整内容之后；均支持有序多层、独立 shape、blur/spread/offset/color，并与 `elevation/zIndex` 解耦
15. `Modifier.semantics` 承载设计系统中立的无障碍状态。集合父节点声明逻辑维度与选择基数，子节点声明逻辑位置与跨度；RTL 只改变物理排列，不改变这些索引，item 的 `selected`/`heading` 仍由同一语义配置中的唯一字段表达
16. 原生 View Padding 只有一个 Renderer 所有者。容器专属 Content Padding、已解析的
    `Modifier.padding` 与选定的系统栏/IME Insets 边会先合成再写入 View；Binder 不得在 Patch
    或环境重绑期间覆盖其他层的贡献。
17. 物理 `padding/margin/offset` 与 Inset 选择器保持物理语义；对应的 `Relative` API 会在每次
    Bind 时根据 VNode 捕获的布局方向解析 start/end。同一族内，后声明的物理或相对值会整体
    替换先声明的值。

## 3. 源码驱动的能力参考

面向应用的完整清单从生产源码生成，并发布在
[能力参考](https://docs.viewcompose.com/reference/)中。原始 Kotlin KDoc 与 Java Javadoc 继续由
[版本化 API Reference](https://docs.viewcompose.com/api/)提供。本文档只负责行为边界与架构
不变量，不再复制符号表或维护一套独立数量。

生成模型遵循以下契约：

1. 从已发布产物的生产源码集中发现 public/protected 的 DSL、Modifier、组件、宿主、集成和
   工具入口；
2. 每个入口只属于一个用户能力分组，并携带符号、重载数、产物、命名空间、发布通道、模块
   手册和版本化 API 根路径；
3. internal 包、private/internal 声明、Demo、测试、生成代码和 Renderer 专用辅助能力不会
   进入应用目录；
4. 网站、清单数量和 Governance V2 陈旧输出门禁消费同一个已提交 JSON 模型；源码、签名、
   版本或结构化所有权改变后未重新生成时，`verifyDocumentationGovernanceV2` 会失败；
5. 维护者通过 `./gradlew updateDocumentationCapabilityReference` 主动刷新已提交模型，
   并审查生成结果的语义差异。

精确的能力、样例和相关文档链接只来自有效的 Governance V2 记录。在冻结的所有权债务迁移
完成前，生成页面会单独报告结构化所有者覆盖率，不猜测也不隐藏缺口。

### 3.1 高级阴影示例与约束


```kotlin
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
        ),
) {
    Content()
}
```

1. 要求像素级 blur/spread/offset/color 或多层合成时使用 `dropShadow(s)`；Material 高程语义继续使用 `elevation`。
2. 需要稳定轮廓时推荐同时为内容和阴影传入同一个 `UiShape`；未显式传入时使用节点 `shape/cornerRadius`，再回退矩形。
3. 阴影不扩张布局 bounds。外阴影需要调用侧保留视觉空间，并避免在非 viewport 祖先上启用不必要裁切。
4. 高频动画优先变换节点的 translation/scale/rotation/alpha；逐帧动画 blur、spread、shape 或尺寸会产生新的栅格 key。
5. 完整后端、缓存和诊断规则见 [shadows.md](../guides/shadows.md)。

### 3.2 生成与一致性

1. 生产源码扫描器与能力参考生成器使用同一个模型，不存在第二套 Modifier 扫描或手写数量。
2. 每个面向应用的入口都有且只有一个生成目录分组；重复或缺失的结构化能力所有权继续作为
   Governance V2 债务显式展示，不会混入本文档。
3. 已提交目录具有确定性，并在文档校验中进行逐字节比较。
4. 模块手册负责产物契约，能力参考负责发现，Dokka 继续负责完整签名与 KDoc。

## 4. 角色边界

### 4.1 Modifier（通用外层修饰）

适合放入 `Modifier` 的能力：

1. 尺寸与占位：`size/width/height/minWidth/minHeight/maxWidth/maxHeight/aspectRatio/padding/paddingRelative/margin/marginRelative`
2. 外观修饰：`backgroundColor/backgroundDrawableRes/border/cornerRadius/alpha/elevation`
3. 可见性与层级：`visibility/offset/offsetRelative/zIndex`
4. 通用交互、Renderer-neutral `interactionIndication`、焦点、按键与无障碍
5. 通过 `testTag` 提供测试标识
6. 物理方向或感知布局方向的系统栏与 IME Padding
7. `nativeView` 逃生通道
8. 绘制、手势、嵌套滚动、阴影和布局尺寸动画修饰

集合复用与动画仍是容器策略，而非 Modifier 数据。焦点编辑器可见性没有 Opt-in 参数；它遵循最近
真实滚动所有者的原生子矩形契约。

### 4.2 Scoped Modifier（父容器相关 parent-data）

只在特定父容器内成立的能力，通过作用域暴露：

1. `RowScope.weight`
2. `RowScope.align`
3. `ColumnScope.weight`
4. `ColumnScope.align`
5. `BoxScope.align`
6. `ConstraintLayout` 子项约束 parent-data：`layoutId/constrainAs/constrain`

### 4.3 NodeSpec（组件语义）

组件自身语义进入组件参数与 `NodeSpec`，例如：

1. `Text`：`color/style/maxLines/overflow/textAlign`
2. `Image`：`contentScale/tint/placeholder/error/fallback`
3. `Button`：`variant/size/enabled/leadingIcon/trailingIcon`
4. `TextField`：`label/placeholder/supportingText/readOnly/imeAction/isError`

通用反馈不会仅仅因为由原生 View 绘制就变成组件字段。
`Modifier.interactionIndication(UiInteractionIndication.StateLayer(...))` 按 Modifier 顺序携带
完整的按下、聚焦和悬停颜色。高层组件先从设计系统 recipe 和类型化 overrides 解析该值，再
安装它。拥有多个内部目标的原生后端组件可以在 NodeSpec 中保留类型化的已选/未选状态层快照，
因为单个外层 Modifier 无法识别这些内部目标。

### 4.4 Theme / Defaults（默认值来源）

默认值链路固定为：

`Theme -> 设计系统 recipe 或 Defaults -> 类型化 overrides -> NodeSpec/Modifier -> Renderer`

约束：

1. 不把主题默认值直接编码为通用 `Modifier`
2. 不在 renderer 写组件业务默认值

## 5. 新能力落点判断

新增一个属性时，按顺序判断：

1. 是否对大多数节点都稳定成立的外层修饰？
2. 是否父容器相关的布局数据？
3. 是否某个组件自身语义？
4. 是否默认值来源（主题/默认样式）？

命中哪一类，就落到对应层，不跨层混放。

## 6. 反模式清单

1. 在通用 `Modifier` 新增组件专属语义字段
2. 在全局 `Modifier` 暴露父容器特定能力
3. 为了快速接入把第一方长期语义回流到动态 map
4. 把主题覆盖当作组件参数替代方案

## 7. Compose 对齐原则

`ViewCompose` 不复刻 Compose runtime/compiler，但在 API 分层上保持对齐：

1. `Modifier` = 通用修饰链
2. parent-data = scope API
3. 组件语义 = 参数/`NodeSpec`
4. 主题 = 默认值来源

`maxWidth`、`maxHeight` 与 `aspectRatio` 表达可移植意图，不是原始 Android Setter。Android
Renderer 会把它们折叠为包裹完整节点的一个合成 `LayoutConstraintHost`，声明顺序不会产生多层
Wrapper。契约层会拒绝非正数或非有限值，声明的 Exact/Minimum 超过声明 Maximum 也会在渲染前
失败。测量时父级传入的精确约束保持权威；其余情况下应用声明 Maximum，并在 Min/Max 区间可行时
保持宽高比。自定义 Renderer 必须提供同样的单边界行为，才能接受这些元素。

## 8. 变更门禁

涉及 `Modifier` 边界变化时，至少完成：

1. 本文档同步
2. 对应 `NodeSpec/renderer` 路径回归
3. demo 验证与必要 UI 测试

流程规则见 [workflow.md](../project/workflow.md)。

## 9. 关联文档

1. [NodeSpec 模型](node-spec.md)
2. [主题指南](../guides/theming.md)
3. [架构总览](overview.md)
4. [焦点与输入指南](../guides/focus-and-input.md)
5. [嵌套滚动指南](../guides/nested-scroll.md)
