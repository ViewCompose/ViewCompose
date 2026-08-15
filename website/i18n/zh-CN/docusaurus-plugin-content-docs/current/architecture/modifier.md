---
translation_source: architecture/modifier.md
translation_source_hash: 3bd43c9f960fcca79c624d57dd10ad81c801916f0bd965a4ac1ee88967edf4e9
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
5. 列表容器策略已收口为容器参数：`reusePolicy`（`sharePool`）与 `motionPolicy`（`disableItemAnimator/animateInsert/animateRemove/animateMove/animateChange`）
6. 键盘焦点跟随已收口为垂直容器参数：`focusFollowKeyboard`；当前覆盖 `LazyColumn`、`LazyVerticalGrid`、`VerticalPager`、`ScrollableColumn`
7. `LazyRow`、`HorizontalPager`、`ScrollableRow` 不暴露 `focusFollowKeyboard`，避免“可调用但无效”的 API 漂移
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

## 3. API 清单（全量扫描）

### 3.1 扫描基线（`src/main`）

本节 API 清单来自仓库实时扫描，命令口径固定为：

```bash
rg "^\s*(public\s+)?(internal\s+)?fun\s+(<[^>]+>\s*)?Modifier\.([A-Za-z0-9_]+)\(" --glob "**/src/main/**/*.kt"
rg "^\s*(public\s+)?(internal\s+)?fun\s+(RowScope|ColumnScope|BoxScope|ConstraintLayoutScope)\."
```

当前扫描结果（2026-08）：

1. `fun Modifier.*` 声明总数（含重载、含 scoped 内部定义）：`91`
2. `fun Modifier.*` 唯一 API 名称数：`77`
3. scoped modifier 声明总数：`5`（`RowScope/ColumnScope/BoxScope`）
4. renderer internal modifier 扩展：`1`（仅内部解析能力）

### 3.2 分组说明（按架构边界）

1. `ui-contract 通用修饰`：平台无关的基础 `Modifier` 契约入口。
2. `gesture 动作输入`：手势 DSL，依赖手势状态对象与策略内核。
3. `graphics 绘制`：绘制阶段 API（含 `draw*` 短写）。
4. `graphics 阴影装饰`：平台无关阴影规格，由 Android decoration layer 执行。
5. `animation 尺寸动画`：`animateContentSize` 布局尺寸过渡入口。
6. `host-android interop`：Android 平台互操作能力（`nativeView/android*`）。
7. `renderer internal 解析/策略`：仅 renderer 内部使用，不给业务侧依赖。

### 3.3 Global Modifier APIs（含 internal）

| API | 模块/命名空间 | 可见性 | 用途备注 | 生效范围 | 补充说明 |
| --- | --- | --- | --- | --- | --- |
| `padding` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置内容内边距 | 全局 | 3 个重载（all/horizontal+vertical/四边） |
| `paddingRelative` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置感知方向的内容内边距 | 全局 | 逻辑 start/end，物理 top/bottom |
| `margin` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置外边距（layout params 侧） | 全局 | 3 个重载 |
| `marginRelative` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置感知方向的 LayoutParams 外边距 | 全局 | 逻辑 start/end，物理 top/bottom |
| `size` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 同时设置宽高 | 全局 | 固定像素语义（框架单位） |
| `width` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置宽度 | 全局 | 与父容器布局规则共同生效 |
| `height` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置高度 | 全局 | 与父容器布局规则共同生效 |
| `minWidth` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置最小宽度约束 | 全局 | 作用于 `View.minimumWidth` |
| `minHeight` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置最小高度约束 | 全局 | 作用于 `View.minimumHeight` |
| `maxWidth` / `maxHeight` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置最大尺寸 | 布局感知 | 使用一个 Renderer 所有的 Constraint Host |
| `aspectRatio` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置宽高比 | 布局感知 | 与 Min/Max 和输入约束共同解析 |
| `fillMaxWidth` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 宽度填充父容器 | 全局 | 语义等价 `width(MATCH_PARENT)` |
| `fillMaxHeight` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 高度填充父容器 | 全局 | 语义等价 `height(MATCH_PARENT)` |
| `fillMaxSize` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 宽高同时填充父容器 | 全局 | 语义等价 `size(MATCH_PARENT, MATCH_PARENT)` |
| `offset` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置平移偏移 | 全局 | 映射 `translationX/translationY` |
| `offsetRelative` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置感知方向的平移偏移 | 全局 | 正水平值朝逻辑 end 移动 |
| `layoutId` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 标记子项布局 ID | 指定容器 | 主要用于 `ConstraintLayout` 子项匹配 |
| `systemBarsInsetsPadding` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 应用系统栏 inset 内边距 | 全局（容器感知） | 可按四边开关 |
| `systemBarsInsetsPaddingRelative` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 应用感知方向的系统栏 inset 内边距 | 全局（容器感知） | 逻辑 start/end 选择器 |
| `imeInsetsPadding` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 应用软键盘 inset 内边距 | 全局（容器感知） | 默认仅 bottom=true |
| `imeInsetsPaddingRelative` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 应用感知方向的软键盘 inset 内边距 | 全局（容器感知） | 逻辑 start/end 选择器；默认仅 bottom=true |
| `backgroundColor` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置背景色 | 全局 | 与 `backgroundDrawableRes` 同时存在时优先级较低 |
| `backgroundDrawableRes` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置 drawable 资源背景 | 全局 | 与 `cornerRadius` 组合时自动裁剪 |
| `border` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置边框宽度与颜色 | 全局 | 依赖 surface style 管线渲染 |
| `cornerRadius` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置圆角半径 | 全局 | 3 个重载（统一/上下/四角） |
| `clip` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 强制裁剪内容到形状边界 | 全局 | 常与圆角/自绘搭配 |
| `alpha` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置透明度 | 全局 | 与 `graphicsLayer.alpha` 冲突时后者优先 |
| `elevation` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置阴影高度 | 全局 | 映射 `View.elevation` |
| `zIndex` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置层级偏移 | 全局 | 当前映射 `translationZ` |
| `graphicsLayer` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 统一设置缩放/旋转/平移/裁剪等图层属性 | 全局 | 高级视觉变换入口 |
| `visibility` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置可见性（Visible/Invisible/Gone） | 全局 | 参与布局占位语义 |
| `clickable` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 基础点击回调 | 全局 | 与 gesture 分发链协同 |
| `focusable` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 声明节点可接收焦点 | 全局 | `focusProperties.canFocus` 可覆盖 |
| `focusRequester` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 将稳定请求器绑定到节点 | 全局 | 节点复用、回滚和释放时自动换绑 |
| `focusProperties` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 声明可聚焦状态与方向目标 | 全局 | 支持 next/previous/四方向 |
| `focusGroup` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 声明键盘导航焦点组 | 容器 | 映射原生 descendant focus 与 navigation cluster |
| `onFocusChanged` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 观察自身/后代焦点状态 | 全局 | 回调 `FocusState` |
| `onPreviewKeyEvent` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 焦点目标前的按键捕获阶段 | 全局 | 从声明式祖先向目标分发 |
| `onKeyEvent` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 按键冒泡阶段 | 全局 | 从目标向声明式祖先分发 |
| `semantics` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置无障碍与测试状态 | 全局 | 包含集合维度、item 逻辑位置、选择态和标题态 |
| `contentDescription` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置无障碍描述 | 全局 | 映射 `View.contentDescription` |
| `testTag` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置测试标记 | 全局 | 供 UI 测试定位 |
| `overlayAnchor` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 设置 overlay 锚点 ID | 指定能力 | 用于 Popup/Tooltip/Dropdown 锚定 |
| `drawBehind` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier`、`viewcompose-graphics` / `com.viewcompose.graphics` | public | 在内容前执行自定义绘制 | 全局 | 两处同名入口；业务侧推荐 `com.viewcompose.graphics` |
| `drawWithContent` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier`、`viewcompose-graphics` / `com.viewcompose.graphics` | public | 自定义内容绘制顺序（可调用内容） | 全局 | 适合混合前景/内容绘制 |
| `drawWithCache` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier`、`viewcompose-graphics` / `com.viewcompose.graphics` | public | 构建并复用绘制缓存 | 全局 | 用于降低高频重绘成本 |
| `draw` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier`、`viewcompose-graphics` / `com.viewcompose.graphics` | public | `drawBehind` 短写 | 全局 | 语义等价别名 |
| `drawCache` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier`、`viewcompose-graphics` / `com.viewcompose.graphics` | public | `drawWithCache` 短写 | 全局 | 语义等价别名 |
| `dropShadow` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 添加单层精确外阴影 | 全局 | 在节点内容前绘制；与 `elevation` 独立 |
| `dropShadows` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 添加有序多层精确外阴影 | 全局 | 同一调用内各层共享显式或节点默认 shape |
| `innerShadow` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 添加单层精确内阴影 | 全局 | 在完整内容后绘制，不参与输入分发 |
| `innerShadows` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | 添加有序多层精确内阴影 | 全局 | 裁切在 shape 内，声明靠后的层后绘制 |
| `pointerInput` | `viewcompose-gesture` / `com.viewcompose.gesture` | public | 原始指针事件处理入口 | 全局 | 可返回 `Consumed` 强拦截后续手势 |
| `combinedClickable` | `viewcompose-gesture` / `com.viewcompose.gesture` | public | 点击/双击/长按组合入口 | 全局 | 无回调时 no-op，不吞事件 |
| `draggable` | `viewcompose-gesture` / `com.viewcompose.gesture` | public | 连续拖拽手势 | 全局 | 通过 `DraggableState` 回调位移 |
| `anchoredDraggable` | `viewcompose-gesture` / `com.viewcompose.gesture` | public | 锚点拖拽/吸附手势 | 全局 | 仅支持 Horizontal/Vertical |
| `transformable` | `viewcompose-gesture` / `com.viewcompose.gesture` | public | 多指缩放/旋转/平移 | 全局 | 由 `TransformableState` 消费增量 |
| `gesturePriority` | `viewcompose-gesture` / `com.viewcompose.gesture` | public | 设置手势优先级 | 全局 | 用于嵌套冲突仲裁 |
| `nestedScroll` | `viewcompose-gesture` / `com.viewcompose.gesture` | public | 声明父子滚动与 fling 消费协议 | 全局 | 透明宿主接入 AndroidX nested-scrolling 链 |
| `animateContentSize` | `viewcompose-animation` / `com.viewcompose.animation` | public | 节点尺寸变化动画 | 全局（布局参与） | 非视觉缩放，真实参与父布局重排 |
| `constrainAs` | `viewcompose-constraintlayout-androidx` / `com.viewcompose.constraintlayout` | public | 按 `ConstraintReference` 声明子项约束 | 指定容器 | 仅 `ConstraintLayout` 子项有效 |
| `constrain` | `viewcompose-constraintlayout-androidx` / `com.viewcompose.constraintlayout` | public | 通过字符串 ID 声明子项约束 | 指定容器 | `constrainAs` 的短写风格入口 |
| `nativeView` | `viewcompose-host-android` / `com.viewcompose.host.android` | public | 直接配置底层 Android `View` | Android interop | 逃生通道，绕过通用语义层 |
| `androidAnimation` | `viewcompose-host-android` / `com.viewcompose.host.android.animation` | public | 配置 Android 动画互操作 | Android interop | 基于 `nativeView` 封装别名 |
| `androidGraphics` | `viewcompose-host-android` / `com.viewcompose.host.android.graphics` | public | 配置 Android 图形互操作 | Android interop | 基于 `nativeView` 封装别名 |
| `resolve` | `viewcompose-renderer-android` / `com.viewcompose.renderer.modifier` | internal | 将 modifier 链解析为 `ResolvedModifiers` | renderer internal | 框架内部 API，业务侧不可依赖 |

### 3.4 作用域限定的 Modifier API

| API | 作用域 | 模块/命名空间 | 可见性 | 用途备注 | 生效范围 | 补充说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `weight` | `RowScope` | `viewcompose-ui-foundation` / `com.viewcompose.ui.foundation` | public | 设置横向线性布局权重 | `Row` 子项 | 仅 `RowScope` 可用，要求 `weight > 0` |
| `align` | `RowScope` | `viewcompose-ui-foundation` / `com.viewcompose.ui.foundation` | public | 设置交叉轴（垂直）对齐 | `Row` 子项 | 参数 `VerticalAlignment` |
| `weight` | `ColumnScope` | `viewcompose-ui-foundation` / `com.viewcompose.ui.foundation` | public | 设置纵向线性布局权重 | `Column` 子项 | 仅 `ColumnScope` 可用，要求 `weight > 0` |
| `align` | `ColumnScope` | `viewcompose-ui-foundation` / `com.viewcompose.ui.foundation` | public | 设置交叉轴（水平）对齐 | `Column` 子项 | 参数 `HorizontalAlignment` |
| `align` | `BoxScope` | `viewcompose-ui-foundation` / `com.viewcompose.ui.foundation` | public | 设置子项在 Box 内对齐 | `Box` 子项 | 参数 `BoxAlignment` |
| `constrainAs / constrain` | `ConstraintLayout` 子项上下文 | `viewcompose-constraintlayout-androidx` / `com.viewcompose.constraintlayout` | public | 声明子项约束 parent-data | `ConstraintLayout` 子项 | 入口是全局 `Modifier` 扩展，但语义仅在 `ConstraintLayout` 生效 |

### 3.5 高级阴影示例与约束

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

### 3.5 一致性校验（扫描对照）

1. 本文档已覆盖扫描得到的全部 `fun Modifier.*`（含 `internal`）。
2. scoped 能力与 global 能力已分表，不混用统计口径。
3. 重复语义入口（`draw*` 在 `ui-contract` 与 `graphics`）已注明推荐命名空间。

## 4. 角色边界

### 4.1 Modifier（通用外层修饰）

适合放入 `Modifier` 的能力：

1. 尺寸与占位：`size/width/height/minWidth/minHeight/maxWidth/maxHeight/aspectRatio/padding/paddingRelative/margin/marginRelative`
2. 外观修饰：`backgroundColor/backgroundDrawableRes/border/cornerRadius/alpha/elevation`
3. 可见性与层级：`visibility/offset/offsetRelative/zIndex`
4. 通用交互与可访问性：`clickable/focusable/focusRequester/focusProperties/focusGroup/onFocusChanged/onPreviewKeyEvent/onKeyEvent/contentDescription`
5. 测试定位：`testTag`
6. 系统栏内边距：`systemBarsInsetsPadding/systemBarsInsetsPaddingRelative`
7. 软键盘内边距：`imeInsetsPadding/imeInsetsPaddingRelative`
8. 逃生通道：`nativeView(key, configure)`
9. 列表性能策略：容器参数 `reusePolicy/motionPolicy`
10. 容器输入跟随策略：垂直容器参数 `focusFollowKeyboard`
11. 内容尺寸过渡：`animateContentSize(animationSpec)`（对节点尺寸变化做布局级动画，spec 语义透传到执行层）
12. 图形绘制阶段：`drawBehind/drawWithContent/drawWithCache`（用于自定义绘制与缓存命令）

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

### 4.4 Theme / Defaults（默认值来源）

默认值链路固定为：

`Theme -> Defaults -> NodeSpec -> Renderer`

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
