# UI Testing Roadmap

## 1. 目标

本文档定义 demo 的 UI 测试策略。

当前单元测试已经覆盖了不少运行时和渲染逻辑，但还不能稳定发现下面这类真实 UI 问题：

1. 文案显示不全、被裁剪、被省略
2. 主题 token 映射错误，导致最终颜色不符合预期
3. demo 页面结构调整后，关键 viewcompose-benchmark 入口跑偏
4. 组件在真实 Activity/View 树里的测量与视觉表现和预期不一致

外挂组件专项规划见 [OVERLAY_COMPONENTS_ROADMAP.md](../../OVERLAY_COMPONENTS_ROADMAP.md)。

因此，后续 UI 测试的目标不是替代单元测试，而是补上“真实 Activity + 真实 View 树 + 真实主题切换”的回归校验。

## 2. 分层策略

### 2.1 结构断言

优先落地，成本最低，能最快定位当前 demo 里的问题。

覆盖：

1. 关键按钮和文本是否可见
2. 关键文本是否被 ellipsize 或裁剪
3. viewcompose-benchmark anchor 是否仍在稳定位置

### 2.2 主题断言

第二层直接检查真实 View 上的颜色值，而不是只验证 token 对象。

覆盖：

1. 页面标题是否跟随 Light/Dark mode 切换
2. 关键控件文本色、背景色是否命中主题 token
3. 局部 override 是否真正生效到 View

### 2.3 截图产物

先作为辅助诊断，而不是正式截图回归平台。

策略：

1. instrumentation 测试在关键场景产出截图
2. 截图用于排查“看起来不对但结构断言没报错”的问题
3. 后续如果场景稳定，再评估是否引入正式 screenshot regression

## 3. 当前测试范围

第一批 UI 测试优先覆盖高风险 demo 页面：

1. `Foundations`
   - viewcompose-benchmark 按钮是否被截断
   - Light/Dark 标题颜色是否正确
2. `Input`
   - viewcompose-benchmark 按钮和输入框是否完整可见
   - 带图标按钮是否被截断
3. `State`
   - viewcompose-benchmark anchor 是否稳定
4. `Diagnostics`
   - viewcompose-benchmark 刷新入口是否稳定

### 3.1 延迟 session 容器专项

这类测试现在已经从“可选覆盖”提升为专项必测。

原因很明确：`LazyColumn` 和 `TabPager` 都已经暴露过同构问题：

1. 节点 `key` 稳定
2. `contentToken` 稳定或结构 diff 为空
3. 但父层闭包、localSnapshot 或外层状态已经变化
4. 已绑定 holder/session 没有刷新，导致真实 UI 停留在旧内容

因此，后续 UI 测试不只要测“能不能显示”，还要测：

1. 结构不变时，文案是否仍然更新
2. 结构不变时，局部主题/内容色是否仍然更新
3. 结构不变时，当前可见页/可见项是否立即刷新，而不是等回收重绑后才刷新

## 4. 当前技术方案

使用 instrumentation test，组合三类能力：

1. `ActivityScenario`
   - 启动目标 Activity
2. `Espresso`
   - 触发主题切换等简单交互
3. `UiAutomator`
   - 产出测试截图

辅助断言基于真实 `View` 树遍历：

1. 查找 `TextView`
2. 检查 `GlobalVisibleRect`
3. 检查 `Layout` 的 `ellipsize` / `ellipsisCount`
4. 检查 `currentTextColor`

## 5. 不做什么

当前阶段不做下面这些事：

1. 不引入重量级截图回归平台
2. 不试图覆盖所有 demo 页面
3. 不用截图像素比对替代结构断言
4. 不把 viewcompose-benchmark 测试和 UI 正确性测试混在同一个用例里

## 6. 后续扩展

当第一批 UI 测试稳定后，再继续扩展：

1. `Collections`
   - key reorder 后 item 文案和局部状态是否正常
2. `Interop`
   - `AndroidView` 显示和主题桥接
3. `Layout`
   - weight / wrap / nested surface 的可见性和布局边界
4. 局部主题 override 的视觉断言
5. 截图目录和 artifact 汇总
6. overlay/feedback 组件的宿主级回归

## 6.1 当前专项回归基线

当前已经进入稳定基线的延迟 session 容器回归有：

1. `Collections -> Stress`
   - 点击 `Insert X`
   - 验证 `Remove X`、`Rotate Order`、`Active ids: X -> A -> B -> C -> D`
   - 目的：覆盖 `LazyColumn` 在结构近似稳定时的 item session 刷新
2. `State -> Patch`
   - 点击 `Advance patch state 0`
   - 验证 `Stable summary 1`
   - 目的：覆盖 `TabPager` 在稳定 page token 下的当前页闭包刷新

这些用例的目标不是证明 demo 正常，而是证明框架层“延迟 session 容器”在真实 Activity 内能刷新可见内容。

## 6.2 后续新增 UI 测试的固定规则

只要新增的控件或容器满足下面任一条件，就必须补延迟 session 容器回归：

1. 内部基于 `RecyclerView` / `ViewPager2`
2. 内部存在 holder/session 复用
3. 内容闭包和结构 diff 可能解耦

新增用例至少要包含这 2 条：

1. `结构稳定 + 外层状态变化`
2. `当前可见内容立即刷新`

专项清单见 [SESSION_CONTAINER_CHECKLIST.md](../architecture/session-containers.md)。

## 7. 结论

当前最合理的路线不是直接上截图平台，而是：

1. 先用 instrumentation 补结构断言
2. 再补主题颜色断言
3. 同时产出截图辅助排查

这样最适合当前这套 View-based 声明式框架，也最容易和框架层改动互相印证。
