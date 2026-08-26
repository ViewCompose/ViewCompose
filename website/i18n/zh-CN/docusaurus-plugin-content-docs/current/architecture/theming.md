---
translation_source: architecture/theming.md
translation_source_hash: cd0c459ddb6616e70408677d51a1962bddf9472d3e54a60ed4aecd4c9f09d456
translation_status: current
---

# 主题运行时架构

## 1. 所有权边界

UI Foundation 持有与设计系统无关的主题作用域。`UiTheme` 安装一个不可变的
`UiThemeTokens` 快照，`Theme` 在构建树时读取该快照，`UiThemeOverride` 派生嵌套快照而不
修改父级。主题作用域只持有语义默认值；受控状态、回调、身份、导航、生命周期和 Android
资源所有权都不属于主题。

设计系统模块把各自输入转换为 Foundation token 和私有组件 recipe。
`viewcompose-material3` 映射 Android Material/AppCompat 资源或静态 Material 值；
`viewcompose-oneui7` 提供自己的静态 token 与 recipe。Android Renderer 不会看到任何设计系统
身份。完整依赖、版本、降级和组件一致性矩阵仍由 [Material 3](../modules/viewcompose-material3/README.md)
与 [One UI 7](../modules/viewcompose-oneui7/README.md) 模块手册维护。

## 2. 主题快照模型

`UiThemeTokens` 包含八个家族：

| 家族 | 所有权 |
| --- | --- |
| `colors` | Surface、Content、Emphasis、Status、Inverse 和 Container 等语义角色。 |
| `stateColors` | 已解析的 Enabled、Disabled、Pressed、Focused、Checked 和 Selected 角色。 |
| `typography` | 15 个语义 Display、Headline、Title、Body 和 Label 角色。 |
| `shapes` | 从 Extra Small 到 Extra Large 以及 Full 的逻辑形状层级。 |
| `controls` | 与设计系统无关的有效尺寸和可见尺寸策略。 |
| `interactions` | Pressed、Focused 和 Hovered 状态层透明度。 |
| `overlays` | 跨组件的模态与 Scrim 策略。 |
| `metadata` | 用于刷新和诊断的来源、暗色状态、修订号与溯源信息。 |

顶层快照刻意不为每个组件预计算完整样式。组件 Defaults 只派生自身需要的值。新的语义项会
在一次改动中成为权威入口：Defaults 与演示同步迁移、历史别名删除，并由
`ThemeTokenUsageAuditTest` 证明所有非保留 token 都有消费者。保留的调色板角色会显式列入
白名单，不会为了提高使用数量而强行分配给无关组件。

## 3. 解析与优先级

标准数据路径是：

`主题快照 -> 组件 Defaults 或命名 recipe -> NodeSpec -> Renderer`

优先级路径是：

1. 显式实例外观；
2. 最近的组件自有 `XxxOverrides` Provider；
3. 外层同类组件 Provider；
4. 命名设计系统 recipe 或 Foundation 组件 Defaults；
5. 当前语义主题快照；
6. 没有 Provider 时的框架中性默认值。

`UiThemeOverride` 为一个子树替换完整 token 家族。变换重载会各读取一次当前家族，执行指定
变换，并安装合并后的不可变结果。替换颜色而不替换 `stateColors` 时，会根据新色彩方案重新
派生状态角色。结果的溯源只把真正替换的家族标为应用 Override。

组件自有 Override 是按字段合并的稀疏值，不是第二套主题模型。Basic Primitive 消费完整的
已解析 Style，因此不接收稀疏 Override。不属于 `UiThemeTokens` 的应用自有 token 系统应使用
应用自有 `uiLocalOf` 和 `ProvideLocal`，而不是 Renderer 分支或 Foundation 全局 recipe 注册表。

## 4. Renderer 与组件边界

语义值在节点到达渲染引擎前完成解析。Text 发出完整的已解析样式。可交互组件把语义 Content
角色与交互透明度组合后发出已解析的 ARGB 状态颜色。需要同时区分触控范围和可见表面的组件会
发出两个独立的已解析尺寸。Renderer 只应用这些快照；它不会重建主题语义、检查 Material 或
One UI 身份，也不会读取 Android 主题属性。

这条边界使中性 Renderer 无需 Material Components 也能工作，并阻止主题适配器向 Reconciliation、
Measurement、Drawing 或 Input Behavior 增加分支。当精确视觉行为需要时，命名设计系统可以
持有复合结构，但输出仍必须跨过共享的中性 NodeSpec、Basic Primitive 或原生行为核心边界。

## 5. Android 资源与刷新生命周期

Material Android Host 为根容器、原生子节点、`AndroidView` 和 Overlay 解析同一个稳定主题
Context，再提供由该 Context 派生的 token。动态颜色是一项 Context 解析策略，不是全局调色板
变更。

中性 Android Host 持有配置监听并发布 `Environment.resourceRevision`。Android-backed
`Material3Theme` 消费该修订号、刷新稳定 Wrapper，再映射出新的不可变快照。应用以命令式方式
修改 Locale、Theme 或 Resource Overlay 且没有配置回调时，应显式刷新 Host 自有的
`AndroidResourceRefreshController`。低层 `Material3ThemeRefreshController` 只保留给没有安装
标准资源环境的自定义 Host。

每个 Activity 根节点持有独立 RenderSession。多个根节点可以观察同一份应用偏好状态，但不能
共享 Remember 的主题 Provider。System 模式从各根节点 Context 解析；显式 Light 或 Dark 模式
可以选择相同的确定性 token 生产器。

## 6. 诊断与证据

`UiThemeMetadata.provenance` 标识静态默认值、Android XML、动态颜色和局部 Override。
`DesignSystemDiagnostics.current` 从同一作用域补充 Recipe Set 身份、组件 Backend、Conformance、
Capability Path 和降级证据。诊断只描述当前解析结果，不负责选择 recipe。

确定性测试覆盖 Provider 恢复与嵌套、颜色和状态颜色重新派生、token 消费、recipe 边界隔离、
Material 降级映射、配置修订号以及 Android Host/Context 一致性。运行：

```bash
./gradlew :viewcompose-ui-foundation:test :viewcompose-material3:test \
  :viewcompose-material3-android:testDebugUnitTest
```

然后使用聚焦指南分别验证[应用模式切换](../guides/theming.md)、
[动态颜色与 Android 刷新](../guides/theming-dynamic-color.md)和
[局部子树 Override](../guides/theming-local-overrides.md)。未来的设备矩阵与组件一致性工作由
[路线图](../project/roadmap.md)持有，本架构页不承载临时交付优先级。
