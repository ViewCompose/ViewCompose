---
translation_source: architecture/decisions/0007-host-owned-android-resource-environment.md
translation_source_hash: 6913c88f7fd1ab945499a0452d96b6a0370df627a1cd077dc92965c314c2cbbe
translation_status: current
---

# ADR-0007：Host 所有的 Android 资源环境

- 状态：已接受
- 日期：2026-08-12

## 背景

ViewCompose 使用 Android View 作为渲染引擎，但应用 DSL 目前要求常用资源属性已经解析为具体值。
例如 `Text` 与 `Button` 接收 `String`，框架却没有提供可感知组合依赖的 Android `getString`、
`getColor`、`getDimension` 或复数资源查询 API。应用可以通过无关的原生对象取得 `Context`，但这种
访问既不是已声明的组合依赖，也不是一致的资源解析契约。

标准 Android Host 也只在安装 Root 时读取一次 `UiEnvironmentValues`。因此，当长期存活的 Host
在 Activity 不重建的情况下自行处理配置变化时，密度、字体缩放、Locale 和布局方向都会过期。
此外，即使当前已建模的环境字段比较结果相同，夜间模式、屏幕方向、屏幕尺寸、密度、Locale 或
其他配置轴仍可能改变资源限定符的解析结果。

Material 3 目前拥有唯一的已挂载 `ComponentCallbacks` 观察器，而且它只刷新自身 Token 快照。
这不是 Material 的独立功能缺口，而是架构缺陷：普通 Android 资源、中立 Host、One UI、产品设计
系统、资源图片以及延迟子 Session 没有共享这次失效。继续把更多资源行为放入 Material 会违反
多设计系统边界。

应用可以让 Locale 或主题选项保存在 `MutableState` 中，并要求每个页面读取这份状态来规避问题。
但这只会刷新显式接线的页面，无法证明 Android 资源限定符、环境值、资源 ID 或保留的子 Session
已经收敛。

## 决策

1. `viewcompose-host-android` 拥有一个设计系统中立的 Android 资源环境。它负责提供带主题的 Root
   `Context`、当前 `Resources`、常用类型化查询函数、Android 逃生口、配置观察、命令式刷新以及
   生命周期清理。
2. 标准 Activity 与 Fragment Host 自动安装该环境。底层自定义 Host 与 Preview Host 显式安装
   同一个 Provider。在有效 Provider 之外查询资源时给出清晰错误，不读取进程全局资源。
3. 第一组公开查询 API 对齐常用只读 Android 资源操作：`stringResource`、格式化字符串、
   `pluralStringResource`、`colorResource`、`dimensionResource`、像素尺寸、布尔值、整数、字符串
   数组与整数数组。DSL 组件继续接收已解析值，不在每个 `Text`、`Button` 和组件 API 上复制资源
   ID 重载。
4. `LocalAndroidContext` 与 `LocalAndroidResources` 是处理少见 Android 资源 API 的受控逃生口。
   它们的值受 Host 作用域约束，不得在所属 Session 之外保留，也不得充当进程全局配置状态。
5. `UiEnvironmentValues` 新增单调递增的 `resourceRevision`。它是平台中立的失效身份，不是持久化
   版本或语义化配置模型。每个发出的 `VNode` 都捕获它，因此即使 Drawable 或图片资源 ID 的
   数值不变，修订变化仍会强制执行原生重绑定。
6. Android 资源环境在每次相关 Android 配置回调之后，以及
   `AndroidResourceRefreshController.refresh()` 之后推进修订。刷新顺序是先更新稳定的带主题
   Context 包装器，再重新读取 Android 环境值，最后发布一个可观察快照。调用与回调都限制在
   Android 主线程。
7. Material 3 不再拥有标准 Host 的配置观察。它在 Host 资源修订改变后重新读取 Token。其他设计
   系统可以消费同一个修订，而 Host、Renderer 与 UI Foundation 不需要知道它们的名称。设计系统
   仍可拥有在 Host 发布新快照前刷新自身稳定主题 Context 包装器的方式。
8. Local 快照把 Android 资源环境与修订传递给 Lazy Item、Pager、Navigation Destination 和
   Overlay Surface。父级重组在新增可见或重新绑定的内容呈现前更新保留的子 Session；子 Session
   不会回退到进程全局资源来源。
9. 当资源图片请求的 Source、Placeholder、Error 或 Fallback 任一项使用资源 ID 时，请求会包含
   捕获的资源修订。因此 Renderer 与图片 Adapter 不会仅因整数 ID 未变化而抑制重新加载。
10. 原生 Preview 与 Compose Preview Bridge 使用自身配置后的 Android Context 作为 DSL 使用的同一
    资源来源。静态 Preview 配置保持确定性，不再发明第二套资源解析器。
11. Demo 提供 Locale、夜间模式、字体缩放/密度、布局方向、尺寸、颜色、复数和资源图片的配置
    控件及资源证据。页面从资源环境派生值，不为触发重组而分别订阅 Demo 语言状态。
12. 该机制会更新资源派生值与可重绑定的原生属性，但不承诺重建构造函数已消费另一种样式身份的
    View。切换 Root 设计系统或其他对构造期敏感的 Root Context 时，仍按既有 Host 契约替换 Root
    及其 Render Session。

## 公开 API 与模块影响

- `viewcompose-ui-contract` 新增 Q2 不可变 `resourceRevision` 环境字段，以及规范化图片请求携带的
  资源修订。这两项都会在 Alpha 版本线改变 Data Class 的二进制契约。
- `viewcompose-ui-foundation` 通过 `Environment` 暴露 Q2 资源修订，并在 Local 快照与发出的
  VNode 中保留它。
- `viewcompose-host-android` 拥有 Q3 Android 资源 Provider、刷新 Controller、查询函数、逃生口、
  生命周期和可编译 Sample。
- `viewcompose-android` 为标准 Root 安装环境，并暴露 Q3 Host 作用域刷新选项和稳定主题 Context
  包装器所需的有限预刷新 Hook。
- `viewcompose-renderer-android`、`viewcompose-image-coil` 与 `viewcompose-image-glide` 在资源 ID
  渲染和加载时遵守修订变化。
- `viewcompose-material3` 与 `viewcompose-material3-android` 消费 Host 修订，在保留 Material
  Context/Token 所有权的同时移除标准 Host 下的并行配置观察器。
- Preview Runtime 模块为配置后的 Preview Context 安装同一个 Host 资源环境。
- `app` 只提供 Demo 与真机验证，不拥有框架资源逻辑。

所有新增的状态持有、Android 边界及配置敏感 API 都是 Q3。适用契约字段包括状态所有权、观察与
重组、Host 生命周期与释放、主线程限制、回调顺序、资源/主题行为、Provider 缺失时的失败、返回
快照的所有权，以及保留 Session 的传播。标量不可变环境字段与只读逃生口 Accessor 为 Q2。

## 影响

- 应用可以写出 `Text(stringResource(R.string.title))` 等已解析值调用，而不需要给每个组件增加
  资源 ID 重载。
- 一次 Host 失效可以协调更新普通资源、环境值、具名设计系统 Token、资源 ID Drawable 和延迟子
  Session 快照。
- Android Engine 拥有平台观察，设计系统只保留自身 Context 与 Token 解释策略。
- 配置回调可能保守地重绑定解析后视觉值没有变化的节点。正确性优先于限定符差异优化；后续只有
  在性能数据证明必要时才收窄失效模型，并且不会改变公开查询 API。
- 使用已变化 Alpha Data Class 的直接构造方与自定义 Renderer 必须重新编译。
- 需要资源 API 或自动配置处理的自定义底层 Host 必须显式安装 Provider；`renderInto` 仍是底层
  挂载入口，不会静默选择 Context。

## 否决方案

### 为每个 DSL 组件增加资源 ID 重载

否决，因为它会在组件之间复制解析策略，使字符串、复数、颜色、尺寸、Drawable 与格式化不断
增加重载，同时仍不能为应用自己的任意计算提供资源 API。

### 把配置观察继续留在 Material 3

否决，因为 Android 资源与配置属于平台 Host。这样会让中立及其他具名设计系统保持过期，并让
Material 意外变成基础底座。

### 要求每个页面观察应用语言或主题状态

否决，因为应用状态是策略输入，并不能证明 Android 资源已经变化。资源 ID 与保留 Session 的值
相等性也无法反映限定符变化。

### 读取 `Resources.getSystem()` 或应用全局 Context

否决，因为系统资源不包含 Root 的应用资源、Locale Override、主题、动态色 Context 包装器或
Preview 配置；进程全局 Context 也会破坏 Root/Session 所有权。

### 在每个 VNode 中保存完整 Android `Configuration`

否决，因为它会把可变 Android 平台对象泄漏到平台中立契约，增加相等性成本，并且仍无法表达
命令式资源/主题突变。单调修订是满足失效要求的最小身份。

### 每次配置回调都重建完整 Root

不作为默认方案，因为它会为能够安全解析和重绑定的变化丢失保留的 View/Session 身份。对于构造
期 Context 身份变化，Root 替换仍然可用且是必需操作。

## 验证与发布

实现遵循当前有效的
[Android 资源环境计划](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/android-resource-environment.md)。
保留该方案要求完成资源查询和生命周期顺序的聚焦单元测试、Renderer/图片修订测试、延迟 Session
传播测试、Preview 测试、覆盖不重建配置变化的 Demo 仪器化测试、API 文档审计、双语言文档门禁，
以及仓库快速/完整质量门禁。
