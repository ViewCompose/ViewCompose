---
translation_source: architecture/decisions/0018-focus-visibility-and-pager-selection-ownership.md
translation_source_hash: c1e79650c215faeee5786f3de0257919391aaba21b6396867985ffa580febf65
translation_status: current
---

# ADR-0018：焦点可见性与 Pager 选择权归属

- 状态：已接受
- 日期：2026-08-22
- 替代：ADR-0012 中关于 ViewPager2 物理宿主和离屏默认值的部分

## 背景

原有 `focusFollowKeyboard` 参数把焦点编辑器可见性设计成每种垂直容器上的可选策略。
Android Renderer 随后为 RecyclerView、ScrollView、ViewPager2 和嵌套 PullToRefresh 路径，
分别使用焦点、布局、全局布局、Insets 和帧回调重新拼装这项行为。这些路径采用不同坐标系，
也无法统一由哪个容器负责移动。

Android 9 实机验证暴露了两类问题：ScrollableColumn 会因把后代内容坐标当成视口坐标而
过度滚动；VerticalPager 会在 IME 改变窗口视口时丢失编辑器焦点。后者并非框架回调的
时序缺陷。ViewPager2 会在 RecyclerView 空闲重布局期间报告一次页面选择并清除当前 Item
的焦点，即使并未发生页面切换。

## 决策

焦点编辑器可见性是真实 Android 滚动所有者必须满足的不变量，不是由调用方选择的容器模式。

1. 从公共 DSL、NodeSpec、Binder、Patch 和 Renderer 状态中删除
   `focusFollowKeyboard`，不保留弃用别名或被忽略的兼容字段。
2. LazyColumn、LazyVerticalGrid 和 ScrollableColumn 保留 Android 的
   `requestChildRectangleOnScreen` 链。最近的滚动所有者负责最小幅度露出，普通父级传播
   处理仍位于屏幕外的祖先关系。
3. 仅在 ViewCompose 编辑器持有焦点期间启用一个窗口级协调器。可见窗口视口变化时，它会
   重新发出编辑器的原生矩形请求，从而覆盖首次焦点请求早于 IME 完成 Resize 的 Android
   版本。它不计算或写入任何容器 Offset。
4. HorizontalPager 和 VerticalPager 使用框架自有的 RecyclerView、
   LinearLayoutManager 与 PagerSnapHelper 视口。空闲重布局不是选择事件，也绝不清除
   当前页焦点；只有真正停稳到另一页时才清除离开页的焦点。
5. Pager 只负责离散页面选择。内容可能被 IME 遮挡的页面，需要声明页内
   ScrollableColumn、LazyColumn 或其他真实垂直滚动所有者。页面边界会在矩形请求到达
   离散 Pager 前终止页内请求。
6. Pager 索引在 RTL 下仍使用逻辑顺序。`userScrollEnabled = false` 禁止指针和无障碍
   翻页，但保留状态命令和程序化焦点可见性。`offscreenPageLimit = -1` 使用 RecyclerView
   默认缓存策略；正数值会在两侧各增加对应数量的整页布局空间。

被删除的 API 家族和变更后的 Pager 契约均为 Q3。同一改动必须包含规范英文 KDoc、可编译
Sample、模块手册、迁移说明和实机验收。

## 影响

- 应用删除 `focusFollowKeyboard` 参数。VerticalPager 表单在页面可能超出 IME 可见视口时，
  增加页内滚动所有者。
- Renderer 删除四个容器专用 Monitor/Resolver 类型及 ViewPager2 运行时依赖。未激活路径
  不持有焦点监听或周期性工作。
- Pager 选择、目标页上报、RTL 几何、同轴嵌套手势归属、离屏驻留、焦点清除和无障碍输入，
  都成为框架测试契约。
- 唯一的窗口级焦点监听仅在一个已挂载编辑器持有焦点时存在，并把所有移动委托给 Android
  原生矩形协议。

## 被否决的方案

### 修补每个容器专用 Monitor

否决，因为修正坐标或再增加延迟回调仍会保留多个所有者与时序竞争，无法为 RecyclerView、
ScrollView、Pager 和 Wrapper 建立统一不变量。

### 把 `focusFollowKeyboard` 保留为弃用参数或无效参数

否决，因为兼容字段会保留错误心智模型，让调用方误以为焦点可见性可以脱离焦点归属单独关闭。

### 保留 ViewPager2 并抑制其焦点清除

否决，因为焦点清除与 ViewPager2 内部页面选择回调、空闲重布局解释相耦合。通过子类或时序
抑制只能在所有权模型与框架契约冲突的后端上继续叠加脆弱补丁。

### 让 Pager 滚动任意页内矩形

否决，因为离散页面选择器不存在有效的页内坐标策略。只有页面中的真实可滚动内容拥有足够
信息，能够以最小幅度露出编辑器。

## 验证

验收要求 JVM 测试覆盖空闲重布局、真实页面切换、正向/反向及命令式目标页、整页几何、离屏
驻留和禁用无障碍输入。实机测试必须覆盖横向/纵向手势、同轴嵌套、稳定页面选择，以及
LazyColumn、LazyVerticalGrid、ScrollableColumn、带页内所有者的 VerticalPager 和
PullToRefresh 在既定 LTR/RTL 矩阵中的完整编辑器露出。
