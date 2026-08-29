---
translation_source: guides/navigation.md
translation_source_hash: 5f5fef08510ded88ca7d36f463db9a56cad269eb5042b6d923caa0b834f8f512
translation_status: current
---

# 配置可上线的导航宿主

请先完成[导航教程](../tutorials/navigation.md)，再使用本指南。教程里的双目标页示例会在这里
扩展为一套明确约束恢复、返回键、所有权与失败策略的宿主。规则背后的运行时原因见
[导航运行时架构](../architecture/navigation.md)。Motion、深链、多栈、Graph Owner 和自适应
Pane 的完整签名与可选 API 仍由 [Navigation Android 模块手册](../modules/viewcompose-navigation-android/README.md)
维护。

## 选择唯一的 Controller Owner

在挂载 `NavHost` 的同一个 UI Owner 中通过 `rememberNavHostController` 创建 Controller。
不得缓存在进程单例或跨 Host 共享。它通过最近的 ViewCompose Registry 保存已提交栈与 Owner
Identity、参数和 Destination State。

请把 `NavHost` 挂在 `LocalLifecycleOwner` 和 `LocalViewModelStoreOwner` 两个边界之下。标准 Activity
与 Fragment `setUiContent` Host 会提供二者；`renderInto` 集成必须显式提供，因为没有私有兜底 Store。

当 Route 需要类型化参数、嵌套所有权或深链时，请使用稳定的 `NavGraph`。如果当前 Graph 已不
接受保存的 Route 层级，恢复会失败关闭到起始目标页。

为每个应用 Route 声明一个稳定 `NavRouteSpec<T>`，并在 `destination`、`navigation`、Controller
类型化命令与 `NavEntry.toRoute` 中复用。只编码持久 ID 和少量基础值，领域对象由 ViewModel
加载，并保持 Name 与 Schema 可恢复。Codec 错误发生在 Host 事务前。

可选 [Kotlinx Adapter](https://docs.viewcompose.com/zh-CN/modules/viewcompose-navigation-kotlinx-serialization)
为扁平序列化 Route 派生 Spec；不支持的 Shape 使用显式 Core Codec。

使用 `NavDeepLinkRequest` 表达外部导航。声明可以约束 URI、action、MIME type 或三者组合，且
每项约束都必须匹配。Android 只映射 Intent Data、Action 与 Type。检查 `NavDeepLinkResult`，并在
安全边界验证完整 URI。

## 恢复状态并接入平台返回

保持 `systemBackEnabled = true`。处于 `STARTED` 且可 Pop 时，`NavHost` 使用最近的 View-tree
NavigationEvent Owner，仅在不存在时回退 Activity Back。根节点向外委派；不要再包第二个 Owner
或 Callback。

界面内返回按钮调用 `popBackStack`。系统返回和 Predictive Back 就会使用同一事务边界。
Predictive Preview 不发布候选栈；取消恢复已提交 Scene，完成走程序化 Pop 路径。

声明稳定的 `NavResultKey`，带值 Pop，并在上一页用 `NavResultEffect` 观察。交付可保存、遵循
FIFO，并等待页面 `RESUMED`；显式确认或重试使用 Destination Context Inbox。

## 穷举处理 Route 渲染

在 `NavHost` 内容块中渲染每个可接受 Route，并立即拒绝未知 Route。内容块运行在 Destination
自有的 Lifecycle、Store、Saveable Namespace 与子 RenderSession 中；除非 Launch Mode 复用，
重复 Push 会创建不同 Owner。Scoped Store 随 Parent 跨配置重建存活，在永久移除时清理，并在进程
重建后从状态创建新 ViewModel。只为不可观察的父级 Capture 修改 `contentKey`；Host Owner、
Controller、Factory、调试身份或 Host `key` 改变会重建原生 Host。

## 观察 Destination Presentation，而不复制 Lifecycle

当内容需要区分 Hidden、Visible、Covered、Interactive、Transition、Pane 或 Overlay Role 时，
应在 Destination DSL 声明阶段读取 `LocalNavDestinationContext.current`，并为回调捕获最近 Holder。
其 Entry 跨隐藏展示释放存活；永久移除会停止更新并销毁 Lifecycle。活动资源仍遵循 AndroidX
Lifecycle，因为该 Context 只表达粗粒度 Role，不含逐帧 Progress。

## 明确选择展示保留策略

除非针对具体 Destination 的真机证据表明其原生 View Tree 重建成本不可接受，否则保持默认
`DisposeWhenHidden`。它释放隐藏原生展示但保留 Owner State。需要时使用经测量的 `Bounded(n)`；
`RetainAll` 无界。策略变化保留 Owner，首次或恢复连接只物化可见 Pane。

## 自定义集成只保留一个策略来源

普通应用使用 `NavHost`。自定义 Host 必须完整执行 `NavExecutionPlan`：Commit 前准备，一起发布
Scene/Lifecycle/Interaction/Back，再按计划清理；不得派生并行 Lifecycle、Retention 或 Back Policy。

## 处理命令结果

每个命令返回 `Committed`、`NoChange`、`Queued` 或 `Failed`。通过 `navigationState` 观察入队任务
完成，并上报结构化 Failure，不替换已提交栈。日志、降级或测试使用 `onFailure`，否则抛出
`NavHostException`。提交前失败保留旧页面，`NavFailure.stackCommitted` 标记提交后边界。

## 验证任务

运行编译教程和 Navigation Android 测试：

```bash
./gradlew :samples:tutorials:assembleDebug :viewcompose-navigation-android:testDebugUnitTest
```

在一条真实 Host 路径中验证：界面与系统 Back 一致；Activity 重建保留 Route、Entry、可保存状态
和 ViewModel Identity；Predictive Back 取消与提交分别改变栈零次与一次；提交前渲染失败保留前页；
深栈展示符合策略，且被淘汰页面恢复 Owner 状态。Detached 命令、重复 Pop、Owner 提前清理、无界
原生保留，或把 `Queued` 当成完成，都表示配置失败。
