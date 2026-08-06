---
translation_source: modules/viewcompose-navigation-android/README.md
translation_source_hash: 8978062c046f7e7ce35313a55e27dba516e6f52b31fea1596476fed374b1f2a5
translation_status: current
---

# Android Navigation 模块

`viewcompose-navigation-android` 把 `viewcompose-navigation-core` 状态挂载为原生 Android View 页面。
它负责目的地和图的生命周期边界、ViewModelStore、SavedStateRegistry 命名空间、子渲染会话、
事务失败恢复、Android 系统返回与预测性返回、自适应 pane 布局，以及感知命令类型的 View motion。

应用仍使用 Activity 或 Window 作为最外层 Android 宿主，但单个页面不需要 Activity 或 Fragment。
平台无关返回栈仍位于 `viewcompose-navigation-core`；本模块是它的 Android 执行边界。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-navigation-android:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。宿主、转场和预测性返回契约在 Alpha 版本之间仍可能演进。
- 平台：Android 库，最低 SDK 跟随仓库 Android 策略。
- 直接 ViewCompose 依赖包括 Navigation Core、Android Host、Renderer、UI Foundation、Lifecycle
  和 ViewModel 集成。
- 该产物会传递引入 `viewcompose-navigation-core`；只需要平台无关模型时可单独依赖 core。

## Controller 与宿主

```kotlin
fun UiTreeBuilder.AppNavigation() {
    val controller = rememberNavHostController(
        startDestination = NavRoute("home"),
    )
    NavHost(controller = controller) { entry ->
        when (entry.route.name) {
            "home" -> HomePage(controller)
            "details" -> DetailsPage(controller)
            else -> error("Unknown route ${entry.route.name}")
        }
    }
}
```

一个 `NavHostController` 同时只能连接一个活跃 `NavHost`。导航命令必须在主线程调用且要求宿主
已连接，确保 core 事务、目的地渲染、owner 生命周期和原生 View 层级共用同一个提交边界。

`NavHost` 为每个目的地创建一个保留的子渲染会话。隐藏 entry 会保留会话和 owner，但暂停帧驱动
渲染；重新可见时使用最新捕获环境渲染，不会在每次命令后同步重组所有保留页面。

目的地闭包依赖不可观察值时应修改 `contentKey`。可观察状态会直接使所属目的地会话失效。
`key`、controller identity、lifecycle owner、调试 identity 或 overlay factory 的变化会重建
原生宿主，因为这些输入改变的是 ownership，而非普通内容。

## 命令结果与重入

Controller 命令返回 `NavResult`：

- `Committed` 报告宿主已应用的状态和 entry owner 差异；
- `NoChange` 报告合法但已经生效的命令；
- `Queued` 表示转场或回调正在执行，命令稍后串行运行；
- `Failed` 报告结构化渲染或提交上下文。

目的地回调可能在另一轮渲染、生命周期更新或 motion 完成期间同步导航。宿主会把重入命令排队，
只在当前操作达到终态后继续执行。因此 queued 结果不代表完成；应观察
`controller.navigationState` 获取最终提交的多栈状态。

Controller 提供即时不可变 `snapshot` 和 `stackState`，以及可观察 `navigationState`。Tab 选中
状态应从 `activeStackId` 派生，不要维护第二份状态源。

## 目的地与图 Ownership

每个目的地 entry 都拥有独立 Android owner，其中包括：

- 受宿主和 pane 可见性限制的 Lifecycle；
- 仅在 entry 离开所有保留状态后清理的 ViewModelStore；
- 从 `NavRoute` 派生默认 SavedStateHandle 参数的 SavedStateRegistry；
- 页面独享的 ViewCompose saveable-state registry 命名空间。

同一个 route 连续 push 两次会创建两个 owner，不会共享页面状态。

嵌套图实例拥有 `NavGraphOwner`。同一个图实例内的目的地共享 Lifecycle、ViewModelStore 和
SavedStateRegistry，直到最后一个后代离栈。之后再次进入同名图 route 会创建新 owner。

`LocalNavGraphOwnerScope.current` 暴露从根到叶的活跃 owner 链。需要以图而不是叶子目的地解析
生命周期、ViewModel 和 Saved State 时，使用 `ProvideNavGraphOwner(route)` 包裹子树。在目的地
内容之外调用或指定非活跃图会失败。

## 失败与回滚

Android 宿主保持 navigation core 的两阶段保证：先准备新目的地会话和 owner，再 stage 到 View
层级，然后提交纯栈状态，最后运行提交副作用。失败由 `NavFailurePhase` 分类。

`NavFailure.stackCommitted` 区分不可逆栈边界前后的失败。提交前失败会移除候选会话和 owner，
并回滚 core 事务；提交后失败保留已提交状态并报告副作用问题，不会假装旧栈仍是事实来源。

可向 `NavHost` 传入 `onFailure` 处理日志、降级或测试。未处理失败会抛出 `NavHostException`，
其中保留原始 cause、失败 entry 和 renderer frame report。

## 保存、恢复与进程死亡

`rememberNavHostController` 使用当前 ViewCompose saveable-state registry，保存：

- 所有保留 stack、活跃 stack 和选择历史；
- 目的地及图实例 ID 与 route 参数；
- 目的地和图的 SavedStateRegistry Bundle；
- 每个页面或图拥有的 ViewCompose saveable 值。

待处理事务、运行动画、View、会话、LifecycleRegistry 实例和 ViewModelStore 内容不会序列化。

恢复采用防御式策略。未知版本、错误集合类型、过多 entry、配置不匹配或图层级变化都会丢弃不兼容
状态并重建初始状态，避免应用升级后把旧 Saved State 命名空间挂到另一个页面 owner。

## Android 系统返回与预测性返回

`systemBackEnabled = true` 时，`NavHost` 仅在 controller 能消费返回时注册到最近的 AndroidX
Back dispatcher。活跃栈到根后遵循保留栈历史配置，否则继续向外层宿主或 Activity 分发。

支持预测性返回的平台上，手势进度会展示上一目的地，但不提交 core 栈。取消时通过弹簧回到已提交
状态；完成手势时使用与程序化 `popBackStack` 相同的事务和 owner 边界。程序化命令可以重定向
活跃 preview，并保留当前视觉 transform，从而连续衔接。

View detach、关闭系统返回或销毁宿主时会主动取消未完成 preview，因为 dispatcher 可能不再发送
终止回调。

## Motion 动效

`NavTransitionSpec` 只是视觉策略，不会改变导航状态或 ownership。它分别配置 push、pop、replace、
reset、stack selection、deep link 和 predictive Back motion。

`NavDestinationTransform` 组合 pane 比例位移、dp 位移、alpha 和 scale。几何与进入/离开 alpha
可拥有独立 duration、delay 和 `NavMotionEasing`。默认 push/pop 几何和 emphasized easing 对齐
当前 Android Activity motion；预测性返回对齐当前 WM Shell 跨 Activity 几何。测试或应用需要
禁用 motion 时使用 `NavTransitionSpec.None`。

View driver 会先绘制完整起始布局，再开始 motion；只变化 transform/alpha 时会临时把昂贵页面
层级提升到硬件 layer。被重定向的 motion 会保留当前视觉属性，后续命令不会跳回 identity 帧。

## 自适应 pane

`NavPanePolicy.Single` 在所有宽度下保持单页面全屏宿主。`Adaptive` 会在每个 pane 都满足最小宽度
时展示最多三个最新 entry。决定 pane 数之前会扣除 `paneSpacingDp`。

宽度变化会复用已提交返回栈、目的地会话和 owner，只重新计算 pane scene 和原生 child bounds。
布局方向会把 primary 到 tertiary 映射为 LTR 或 RTL 下正确的物理顺序。

## 深链与保留栈

String、Android `Uri` 和 `ACTION_VIEW Intent` 入口都使用同一个严格图解析器。匹配结果会转换为
原子命令，同时更新和选中声明的目标 stack。`NavDeepLinkResult.Navigated` 仍包含 `NavResult`，
因此 URI 匹配成功不会与渲染或提交成功混淆。

多个 Tab 应声明一份 `NavStackConfiguration`，并与共享 graph 一起 remember。不要为每个 Tab 创建
一个 controller，也不要在应用字段中镜像活跃 stack；controller 已负责保留每个 stack 和选择历史。

## 相关文档

- [Navigation Core 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-navigation-core)
- [完整导航指南](https://docs.viewcompose.com/zh-CN/guides/navigation)
- [生命周期与 Saved State 架构](https://docs.viewcompose.com/zh-CN/architecture/lifecycle-and-saved-state)
- [Session 容器架构](https://docs.viewcompose.com/zh-CN/architecture/session-containers)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-navigation-android` API 树](https://docs.viewcompose.com/api/viewcompose-navigation-android/current/)。

## 兼容性说明

`0.1.0-alpha01` 确立了一个 controller 对一个 host 的连接、主线程串行命令、目的地和图 ownership、
防御式进程死亡恢复、预测性返回 preview、对齐 Android 的原生 View motion，以及最多三个自适应
pane。请通过 `rememberNavHostController` 持久化 controller 状态，不要在宿主之外保留 Android
owner 或 session 对象。
