---
translation_source: guides/navigation.md
translation_source_hash: 53e34ca11856653865bf0885d6d830bf90dfcd8a9dec2fda9010e642c5b16aba
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
不要把它缓存在进程单例中，也不要同时挂到两个 Host。remember 的 Controller 会通过最近的
ViewCompose saveable-state registry 保存已提交栈、Entry 与 Graph 身份、Route 参数和
Destination 自有 SavedState。

当 Route 需要类型化参数、嵌套所有权或深链时，请使用稳定的 `NavGraph`。如果当前 Graph 已不
接受保存的 Route 层级，恢复会失败关闭。应把结果视为安全回到配置的起始目标页，不得拼接出
部分恢复的栈。

## 恢复状态并接入平台返回

普通 Activity 或 Fragment Host 保持 `systemBackEnabled = true`。只有 Controller 能消费返回
时，`NavHost` 才会向最近的 AndroidX Back Dispatcher 注册；到达活动根节点后，它会按配置的
保留栈历史处理，或向外层继续分发。

界面内返回按钮调用 `popBackStack`。系统返回和 Predictive Back 就会使用同一事务边界。
Predictive Back 在不发布候选栈的前提下预览上一目标页；取消时恢复已提交 Scene，完成时则走
与程序化 Pop 相同的路径提交。

## 穷举处理 Route 渲染

在 `NavHost` 内容块中渲染每个可接受 Route，并立即拒绝未知 Route。内容块运行在 Destination
由框架持有的 Lifecycle、ViewModelStore、SavedStateRegistry namespace、saveable-state
namespace 和子 RenderSession 中。除非明确选择复用的 Launch Mode，否则连续两次 Push 同一
Route 仍会创建两个不同 Entry Owner。

只有当 Destination 内容闭包读取不可观察的父级值时才修改 `contentKey`。可观察的 ViewCompose
状态会直接使所属 Destination Session 失效。Controller、Lifecycle Owner、父级
ViewModelStore Owner、Overlay Factory、调试身份或 Host `key` 的变化属于所有权变化，因此会
重建原生 Host。

## 处理命令结果

每个 Controller 命令都返回 `NavResult`：

| 结果 | 应用行为 |
| --- | --- |
| `Committed` | 读取其中的栈快照，或让可观察的 `navigationState` 更新界面。 |
| `NoChange` | 保持当前界面；命令有效，但目标状态已经生效。 |
| `Queued` | 等待 `navigationState`；这表示任务已入队，而不是已经完成。 |
| `Failed` | 上报结构化 `NavFailure`，并保留结果中携带的已提交栈。 |

如果应用有日志、降级或测试策略，请向 `NavHost` 传入 `onFailure`。未提供 Handler 时，Host 会
抛出 `NavHostException`。不要捕获任意 Destination 失败后再修改第二套应用自有返回栈：栈提交
前的失败已经会保留旧栈和可见页面；提交后的失败会通过 `NavFailure.stackCommitted` 明确标出
边界。

## 验证任务

运行编译教程和 Navigation Android 测试：

```bash
./gradlew :samples:tutorials:assembleDebug :viewcompose-navigation-android:testDebugUnitTest
```

然后验证一条真实 Host 路径：

1. 连续 Push 两个 Destination，并在每一页修改可保存状态。
2. 分别点击界面返回按钮和系统返回；两者必须展示同一个上一个 Entry。
3. 重建 Activity，确认当前 Route、Entry 身份和 saveable 值保持不变。
4. 在 Android 13 或更高版本上，先取消再完成一次边缘返回手势。取消不得改变栈；完成必须只
   Pop 一次。
5. 通过应用测试接缝注入 Destination 渲染失败。当 `stackCommitted` 为 false 时，前一页必须
   保持可见，且 `onFailure` 必须收到准确阶段。

只有五项检查全部通过，任务才算完成。Detached 命令异常、普通 Activity 重建后 Entry 身份
变化、重复 Pop、失败渲染后候选页可见，或把 `Queued` 当成完成，都属于配置失败。

## 选择下一项聚焦任务

- 使用严格 Deep Link 声明，并在接受外部 URI 前检查 `NavDeepLinkResult`。
- 使用 `NavStackConfiguration` 配置相互独立的保留 Tab 栈，并从
  `navigationState.activeStackId` 派生 Tab 选中态。
- 只有确实希望多个可见 Destination 共享已验证 Pane Scene 时，才使用
  `NavPanePolicy.Adaptive`。
- 只有在活动 Destination 内容内部，而且状态应属于 Graph 而不是某个叶子 Destination 时，
  才使用 `ProvideNavGraphOwner`。

这些能力扩展的是同一套 Controller 和事务模型，不是替代导航路径。
