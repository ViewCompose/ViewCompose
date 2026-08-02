# ViewCompose Widget Roadmap

## 1. 文档定位

本文档定义 `ViewCompose` 的控件体系规划。

目标不是把 Android 原生控件的所有 setter 一比一搬进 DSL，而是明确：

1. 哪些控件必须做成虚拟控件
2. 每个控件应该暴露哪些“高频、稳定、语义化”的属性
3. 哪些属性应交给 `Modifier`
4. 哪些视觉能力应交给主题系统
5. 哪些复杂控件不值得框架内建，应该继续走 `AndroidView`

关联文档：

- 主题分层见 [THEMING.md](../guides/theming.md)
- 局部主题覆盖见 [THEME_OVERRIDES.md](../../THEME_OVERRIDES.md)
- 整体架构见 [ARCHITECTURE.md](../architecture/overview.md)

## 2. 结论

控件体系后续必须遵守这条总原则：

> 虚拟控件负责“常用语义和稳定默认值”，`Modifier` 负责“通用布局与通用视觉”，主题系统负责“默认样式和局部复用”，`AndroidView` 负责“复杂原生能力兜底”。

不建议做的事：

- 为了“完整”去映射 Android 每个原生 setter
- 每个控件都暴露大量 raw color / raw drawable / raw gravity 参数
- 把主题系统变成“给每个 setter 提供默认值”

这会直接让 DSL 失控。

## 3. 控件属性设计原则

### 3.1 属性按 5 层划分

| 层级 | 说明 | 例子 | 归属 |
| --- | --- | --- | --- |
| 内容属性 | 控件展示的数据 | `text`、`value`、`items`、`checked` | 组件参数 |
| 状态属性 | 控件所处状态 | `enabled`、`selected`、`isError`、`readOnly` | 组件参数 |
| 行为属性 | 控件交互行为 | `onClick`、`onValueChange`、`onCheckedChange` | 组件参数 |
| 语义样式属性 | 高频、稳定、可主题化的样式选择 | `variant`、`size`、`singleLine`、`keyboardType` | 组件参数 |
| 通用视觉/布局属性 | 所有控件都可能需要的能力 | `padding`、`margin`、`background`、`border`、`cornerRadius`、`alpha` | `Modifier` |

第六层兜底方式：

- 少数复杂或低频能力，直接交给 `AndroidView`

### 3.2 不要直接暴露所有原生属性

例如 Android 原生 `TextView` 有很多 setter，但框架不应该直接提供：

- `setShadowLayer`
- `setElegantTextHeight`
- `setLetterSpacing`
- `setBreakStrategy`
- 各种 raw bitmask 风格配置

是否应该暴露的判断标准只有三个：

1. 业务高频使用
2. 能形成稳定 DSL 语义
3. 值得进入主题 / 默认值体系

只满足“原生有这个 setter”，不够成为 DSL 属性。

### 3.3 优先暴露语义，不暴露实现细节

例如：

- 暴露 `variant = Outlined`，不要要求业务自己配 `borderWidth + borderColor`
- 暴露 `size = Medium`，不要要求业务自己每次写高度和 padding
- 暴露 `keyboardType = Number`，不要直接暴露复杂 `inputType bitmask`

### 3.4 主题系统只服务默认值

主题系统应该提供：

- 默认颜色
- 默认字体
- 默认圆角
- 默认间距 / 尺寸
- 组件默认 state 样式

但不应该替代：

- 单个控件的特殊展示
- 少量临时实验样式
- 所有原生属性控制

### 3.5 `Modifier` 的职责必须稳定

后续统一约束：

- 通用布局：放 `Modifier`
- 通用视觉：放 `Modifier`
- 组件语义：放组件参数
- 主题默认值：放 Theme / Defaults

## 4. 三档优先级定义

### 4.1 Priority 1

这些控件是 v1 必须虚拟映射的。

要求：

- 必须有稳定 `NodeType`
- 必须有清晰 DSL
- 必须接入主题默认值
- 必须具备可预测的属性设计

### 4.2 Priority 2

这些控件对框架有价值，但可以等基础 runtime / renderer / theme 稳定后再做。

要求：

- 先有设计，不急着马上实现
- 可以依赖现有基础控件 / 容器 / `AndroidView` 过渡

### 4.3 Priority 3

这些控件不建议优先做成框架内建虚拟控件。

建议：

- 默认继续用 `AndroidView`
- 只有后面遇到大量重复使用、且主题 / 生命周期 / 状态集成需求很强时，再重新评估

## 5. Priority 1: v1 必须虚拟映射

### 5.1 布局与基础容器

| 控件 | 原生承载 | 必备属性 | 主题关系 | 当前判断 |
| --- | --- | --- | --- | --- |
| `Box` | `FrameLayout` / 自定义容器 | `contentAlignment`、children | 依赖 `Surface/Shape` 语义 | 必须 |
| `Row` | `LinearLayout` / 自定义容器 | `spacing`、`arrangement`、`alignment` | 间距与默认内容色可受主题影响 | 必须 |
| `Column` | `LinearLayout` / 自定义容器 | `spacing`、`arrangement`、`alignment` | 同上 | 必须 |
| `Surface` | `Box` 的语义包装 | `variant`、`tonal`、`enabled` | 强依赖主题 | 必须 |
| `Spacer` | `View` | 尺寸、weight | 不直接依赖主题 | 必须 |
| `Divider` | `View` | `orientation`、`thickness` | 依赖 divider token | 必须 |

说明：

- `Surface` 虽然不是原生控件，但它是主题系统和容器系统的关键粘合层，必须进入 `P1`
- 当前用 `Box` + modifier 模拟卡片是过渡方案，不是最终最佳 API

### 5.2 文本与媒体

| 控件 | 原生承载 | 必备属性 | 主题关系 | 当前判断 |
| --- | --- | --- | --- | --- |
| `Text` | `TextView` | `text`、`style`、`maxLines`、`overflow`、`textAlign` | 强依赖 `Typography` / `ContentColor` | 必须 |
| `Image` | `ImageView` | `source`、`contentScale`、`contentDescription`、`tint` | `tint` 可走主题 / local | 必须 |
| `Icon` | `ImageView` / `TextView` | `source`、`contentDescription`、`tint` | 强依赖 `ContentColor` | 必须 |

不建议 `P1` 暴露的属性：

- 大量图片底层加载器参数
- 复杂矩阵变换
- raw drawable state 列表

这类能力应交给图片加载层或 `AndroidView`。

### 5.3 按钮与动作

| 控件 | 原生承载 | 必备属性 | 主题关系 | 当前判断 |
| --- | --- | --- | --- | --- |
| `Button` | `Button` / `TextView` | `text`、`onClick`、`enabled`、`variant`、`size` | 强依赖按钮组件默认值 | 必须 |
| `IconButton` | `ImageButton` / 自定义 | `icon`、`onClick`、`enabled`、`variant`、`size` | 强依赖圆角 / ripple / content color | 必须 |

当前按钮类控件在 v1 应优先支持的语义：

- `variant`
- `size`
- `enabled`
- `leadingIcon` / `trailingIcon` 可视为 v1.1，但推荐尽快补齐

### 5.4 文本输入

| 控件 | 原生承载 | 必备属性 | 主题关系 | 当前判断 |
| --- | --- | --- | --- | --- |
| `TextField` | `EditText` | `value`、`onValueChange`、`label`、`placeholder`、`supportingText`、`isError`、`enabled`、`readOnly`、`singleLine`、`maxLines`、`variant`、`size` | 强依赖文本输入主题 | 必须 |
| `PasswordField` | `EditText` | `value`、`onValueChange`、`label`、`placeholder`、`enabled`、`visualTransformation` | 强依赖输入状态主题 | 必须 |
| `NumberField` | `EditText` | `value`、`onValueChange`、`label`、`enabled`、`min/max` 可后置 | 同上 | 必须 |
| `EmailField` | `EditText` | `value`、`onValueChange`、`label`、`enabled` | 同上 | 必须 |
| `TextArea` | `EditText` | `value`、`onValueChange`、`label`、`placeholder`、`minLines`、`maxLines`、`enabled` | 同上 | 必须 |

输入类控件的属性设计必须避免直接暴露：

- raw `inputType` bitmask
- 原生全部 IME flag
- 大量 cursor / handle / extract UI setter

推荐统一成语义属性：

- `keyboardType`
- `imeAction`
- `readOnly`
- `visualTransformation`
- `maxLength`

### 5.5 选择与滑动输入

| 控件 | 原生承载 | 必备属性 | 主题关系 | 当前判断 |
| --- | --- | --- | --- | --- |
| `Checkbox` | `CheckBox` | `checked`、`onCheckedChange`、`enabled`、`text` | 强依赖 checked / unchecked / disabled 默认值 | 必须 |
| `Switch` | `Switch` | `checked`、`onCheckedChange`、`enabled`、`text` | 强依赖 thumb / track / label 主题 | 必须 |
| `RadioButton` | `RadioButton` | `checked`、`onCheckedChange`、`enabled`、`text` | 强依赖选中态默认值 | 必须 |
| `Slider` | `SeekBar` | `value`、`onValueChange`、`min`、`max`、`enabled`、`steps` | 强依赖 track / thumb / active/inactive 色 | 必须 |

说明：

- 这四类控件目前已经开始独立主题化，这是正确方向
- 后续不要再回退到一个共用 `inputControl` 大域

### 5.6 反馈与加载

| 控件 | 原生承载 | 必备属性 | 主题关系 | 当前判断 |
| --- | --- | --- | --- | --- |
| `LinearProgressIndicator` | `ProgressBar` | `progress`、`indeterminate`、`enabled` | 依赖 active / track 色 | 必须 |
| `CircularProgressIndicator` | `ProgressBar` | `progress`、`indeterminate`、`enabled` | 同上 | 必须 |

原因：

- 加载和反馈是业务高频
- 主题需求明确
- 原生承载简单

### 5.7 列表与宿主

| 控件 | 原生承载 | 必备属性 | 主题关系 | 当前判断 |
| --- | --- | --- | --- | --- |
| `LazyColumn` | `RecyclerView` | `items`、`key`、`contentPadding`、`spacing`、item content | 弱依赖主题，强依赖状态与 identity | 必须 |
| `AndroidView` | 用户提供原生 View | `factory`、`update`、`key` | 依赖 Android Theme Bridge | 必须 |

## 6. Priority 2: 框架稳定后再做

### 6.1 横向/网格类列表

| 控件 | 原生承载 | 核心属性 | 为什么是 P2 |
| --- | --- | --- | --- |
| `LazyRow` | `RecyclerView` | `items`、`key`、`contentPadding`、`spacing` | 复用 `LazyColumn` 设计，但不是 v1 必需 |
| `LazyGrid` | `RecyclerView` | `items`、`key`、`columns`、`spacing` | 布局和回收语义更复杂 |
| `VerticalScroll` / `HorizontalScroll` | `ScrollView` / `HorizontalScrollView` | `enabled`、内容容器 | 价值高，但不如 lazy 列表优先 |

### 6.2 组合导航与筛选控件

| 控件 | 原生承载 | 核心属性 | 为什么是 P2 |
| --- | --- | --- | --- |
| `TabPager` | `TabLayout + ViewPager2` | `pages`、`selectedIndex`、`onSelected`、样式 token | 组合控件，当前已有实验实现，但不应作为 v1 基线依赖 |
| `SegmentedControl` | 自定义容器 | `items`、`selectedIndex`、`enabled`、`onSelected` | 已有实现，但属于高级组合选择控件 |
| `Chip` / `ChipGroup` | `Chip` / 自定义 | `selected`、`onSelected`、`variant`、`size` | 设计空间大，主题和状态复杂 |

### 6.3 复杂输入与选择

| 控件 | 原生承载 | 核心属性 | 为什么是 P2 |
| --- | --- | --- | --- |
| `Dropdown` / `Spinner` | `Spinner` | `items`、`selected`、`onSelected`、`enabled` | 原生交互不够现代，最好等输入体系稳定后统一设计 |
| `AutoCompleteTextField` | `AutoCompleteTextView` | `value`、`suggestions`、`onValueChange`、`onSelected` | 同时涉及输入、弹层、列表 |
| `RatingBar` | `RatingBar` | `value`、`steps`、`enabled` | 常见但不是基础路径 |
| `SearchBar` | `SearchView` / 组合控件 | `query`、`onQueryChange`、`active` | 适合在文本输入体系稳定后做 |

### 6.4 结构性反馈组件

专项规划见 [OVERLAY_COMPONENTS_ROADMAP.md](../../OVERLAY_COMPONENTS_ROADMAP.md)。

| 控件 | 原生承载 | 核心属性 | 为什么是 P2 |
| --- | --- | --- | --- |
| `Dialog` | `Dialog` / `FragmentDialog` | `visible`、`onDismiss`、content | 生命周期与宿主绑定更复杂 |
| `BottomSheet` | `BottomSheetBehavior` / `DialogFragment` | `visible`、`peekHeight`、`expanded` | 依赖容器与宿主状态 |
| `Snackbar` | `Snackbar` | `message`、`action`、`duration` | 更像宿主服务，不是纯控件 |

## 7. Priority 3: 默认继续使用 AndroidView

| 控件 | 原生承载 | 处理方式 | 原因 |
| --- | --- | --- | --- |
| `WebView` | `WebView` | `AndroidView` | 生命周期、性能和安全边界复杂 |
| `MapView` | 各类 SDK View | `AndroidView` | 强 SDK 绑定 |
| `VideoView` / `PlayerView` | 原生或三方播放器 | `AndroidView` | 媒体会话与生命周期复杂 |
| `SurfaceView` / `TextureView` / `GLSurfaceView` | 原生 | `AndroidView` | 渲染线程和宿主强绑定 |
| `PreviewView` / Camera 预览 | CameraX | `AndroidView` | 强平台耦合 |
| `DatePicker` / `TimePicker` / `NumberPicker` / `CalendarView` | 原生 | `AndroidView` | 使用频率不够高，平台差异大 |
| `FragmentContainerView` | 原生 | `AndroidView` 或宿主层 | 更接近导航/宿主，不应先塞进控件体系 |

原则：

- 这些控件不是“不支持”
- 而是“不优先纳入框架语义层”

## 8. 每类控件的属性设计约束

### 8.1 Text

v1 应支持：

- `text`
- `style`
- `maxLines`
- `overflow`
- `textAlign`

建议暂缓：

- 富文本 spans 全能力
- 原生所有排版细节 setter

### 8.2 Image / Icon

v1 应支持：

- `source`
- `contentDescription`
- `contentScale`
- `tint`
- `placeholder / error / fallback` 语义

建议暂缓：

- 图片加载器策略
- 复杂裁切矩阵
- 动图播放控制

补充约束：

- `Image/Icon` 本身属于 `P1` 控件层
- 远程图片加载属于单独的资源加载层，应通过可插拔 loader 接入，不应把 DSL 直接绑死到具体库

### 8.3 Button / IconButton

v1 应支持：

- `text` / `icon`
- `onClick`
- `enabled`
- `variant`
- `size`
- `leadingIcon / trailingIcon`

建议暂缓：

- loading
- split button
- drop-down action

### 8.4 TextField Family

v1 应支持：

- `value`
- `onValueChange`
- `label`
- `placeholder`
- `supportingText`
- `isError`
- `enabled`
- `readOnly`
- `singleLine`
- `maxLines`
- `keyboardType`
- `imeAction`
- `variant`
- `size`

建议暂缓：

- 掩码输入规则
- 原生所有 IME flag
- 富文本编辑

当前 v1 约束：

- `keyboardType` 目前由 `TextFieldType` 语义枚举承载
- `label / placeholder / supportingText` 已进入正式 DSL，并由 `TextField` 语义容器负责展示

### 8.5 Checkbox / Switch / RadioButton / Slider

v1 应支持：

- 当前值
- 值变化回调
- `enabled`
- 文本标签（适用时）

主题层必须考虑：

- checked / unchecked
- enabled / disabled
- active / inactive
- thumb / track / indicator / label

### 8.6 ProgressIndicator

v1 应支持：

- `progress`
- `indeterminate`
- `enabled`
- `variant`

主题层必须考虑：

- active
- track
- disabled / low emphasis

## 9. 主题和控件设计如何结合

这是后续最重要的约束。

### 9.1 优先把高频样式收成语义参数

例如：

- `Button(variant, size)`
- `TextField(variant, size, isError)`
- `Checkbox(checked, enabled)`

不要让业务直接靠大量 `Modifier` 拼出这些语义。

### 9.2 主题系统负责默认 look

当前已实现：默认值从 `Theme.colors` 即时派生，不再读预计算 token。

例如：

- 默认按钮主色 → `Theme.colors.primary`
- 默认输入框 border / container → 从 `Theme.colors` 派生
- 默认 checkbox / slider 状态色 → 从 `Theme.colors` 派生

### 9.3 组件参数只解决“语义选择”

例如：

- 选哪个 variant
- 当前是不是 error
- 当前是不是 enabled

### 9.4 `Modifier` 解决通用视觉和布局

例如：

- `padding`
- `margin`
- `background`
- `border`
- `cornerRadius`
- `alpha`
- `visibility`

### 9.5 `AndroidView` 作为复杂度隔离层

当一个控件满足下面任一条件时，应优先考虑 `AndroidView`：

- 生命周期复杂
- 平台耦合很强
- 属性表过大
- 与主题系统弱相关

## 10. 当前实现与路线建议

### 10.1 当前已经具备的 P1 基线

当前仓库已经补齐的 `P1` 基础控件与核心语义包括：

- `Box / Row / Column / Surface / Spacer / Divider`
- `Text(maxLines, overflow, textAlign)`
- `Image / Icon`
- `Button / IconButton`
- `TextField / PasswordField / EmailField / NumberField / TextArea`
- `Checkbox / Switch / RadioButton / Slider`
- `LinearProgressIndicator / CircularProgressIndicator`
- `LazyColumn(contentPadding, spacing, key, item sessions)`
- `AndroidView`

其中补齐的关键语义包括：

- `Image` 的 `placeholder / error / fallback`
- `Button` 的 `leadingIcon / trailingIcon`
- `TextField` 的 `label / placeholder / supportingText / readOnly / maxLines / imeAction`

结论：

- `P1` 基础控件层已经闭环
- 后续主线应切到 `P2` 能力，或继续收敛主题 / 状态 / 交互细节，而不是继续新增 `P1` 控件种类

### 10.2 当前 P1 的剩余工作

按当前实现状态，`P1` 不再有“缺少基础控件”的阻塞项。

后续如果仍在 `P1` 范围内继续打磨，优先级更高的是：

1. 收紧现有控件的交互细节和状态语义
2. ~~完善主题 token 与组件默认值映射~~ → ✅ 已完成：Defaults 已改为从 `Theme.colors` 即时派生
3. 补更细的测试和调试能力

### 10.3 当前已存在但应视为 P2 的控件

当前已实现但不建议继续扩散的：

- `TabPager`
- `SegmentedControl`

不是说它们不该存在，而是：

- 它们属于组合控件
- 不应优先于基础输入 / 媒体 / 反馈控件继续扩张

## 11. 推荐执行顺序

1. 先把 `P1` 缺口补齐
2. 同时按 [THEMING.md](../guides/theming.md) 收敛主题默认值分层
3. 等基础控件、主题、状态都稳定后，再进入 `P2`
4. `P3` 保持 `AndroidView` 策略，不主动扩展

## 12. 最终约束

后续新增任何控件前，必须先回答：

1. 它属于 `P1 / P2 / P3` 哪一档？
2. 哪些属性是组件参数，哪些必须走 `Modifier`？
3. 哪些默认值必须进入主题系统？
4. 是否真的值得做虚拟控件，而不是 `AndroidView`？

如果这四个问题没有回答清楚，不应直接实现。
