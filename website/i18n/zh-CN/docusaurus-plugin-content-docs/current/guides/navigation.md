---
translation_source: guides/navigation.md
translation_source_hash: 6e7573952bec62a3098cd92e371b66833e200a5898d56778b72c12cd6f34b0f7
translation_status: current
---

# 系统导航

## 1. 范围

ViewCompose 导航只把 Android `Activity`/`Window` 当作根平台宿主。destination 是框架拥有的
页面 Session，不映射为 Activity 或 Fragment。

导航分为两层：

1. `viewcompose-navigation-core`：纯 Kotlin/JVM，拥有不可变 route/back stack、两阶段导航
   事务、页面生命周期规划和持久化契约；
2. `viewcompose-navigation-android`：拥有 destination `RenderSession`、AndroidX Lifecycle/ViewModel/
   SavedState adapter、系统返回、转场和 destination 容器 View。

Android 集成在孵化期间与现有应用入口隔离；公共 API 委托给内部事务测试使用的同一 coordinator。

当前 Stage 1–10 均已完成，包括导航与生命周期内核、Android 页面 owner、destination Session、
事务 `NavHost`、转场、恢复、系统返回与 Predictive Back、API 33/35 真机门禁、嵌套 graph、
graph scope、多保留 tab 栈、严格 deep link 和自适应原生 View pane。

## 2. P0 交付

### Stage 1：导航内核

- 稳定 `NavEntryId` 与可持久化 typed route value；
- 非空不可变 back stack；
- `push`、`pop`、`replaceTop`、`reset` 和 single-top；
- prepare/commit/rollback 事务；
- 确定性 stack snapshot 恢复。

### Stage 2：页面生命周期内核

- host lifecycle 限制 destination lifecycle；
- 稳定 pane scene 中所有可交互 destination 为 `RESUMED`；
- 转场可见参与者至少 `STARTED`；隐藏保留项为 `CREATED`；永久移除项为 `DESTROYED`；
- 先执行向下生命周期，再执行向上生命周期。

### Stage 3：Android 页面所有权

每个 `NavEntry` 拥有独立 child `RenderSession`、saveable-state namespace、`ViewModelStore`、
AndroidX `LifecycleRegistry` 和捕获的 CompositionLocal snapshot。隐藏时保留资源，永久移除后才
清理。

destination 先渲染到未挂载候选容器。成功候选可隐藏 staged、commit 并展示；失败或 rollback
会销毁组合与 owner，不发布页面。已提交 Session 可刷新最新 CompositionLocal snapshot 和内容
闭包，而不替换 entry owner 或容器。

### Stage 4：事务 `NavHost`

- 发布新 stack 前渲染候选 destination；
- 候选成功后才提交 stack 与 lifecycle；
- 失败时回滚候选 Session；
- 转场期间同时保留 outgoing/incoming Session；
- 主线程串行处理重入命令。

`TransactionalNavHostCoordinator` 拥有 settled-state 事务边界，处理初始 attach、所有 stack
命令、pop 前揭页刷新、host lifecycle cap 和渲染期间的新命令。失败候选回滚纯 back-stack
事务，并丢弃该候选发出的命令。

stack 提交后，coordinator 发布不可变前后 pane scene、保留 entry、可见 union 和层级顺序。
after-scene 的 destination 可交互且 `RESUMED`；仅转场可见项为 `STARTED`。永久移除 Session
只在转场终态销毁。

转场 driver 是可取消策略 adapter。完成或显式取消都收敛到已提交目标，不回滚 stack。新命令
会取消视觉工作、稳定已提交目标，再准备下一事务；旧 transition ID 的回调无效。host 销毁时
立即取消视觉工作并销毁所有保留页面。

公共 `NavHost` 通过事务 `AndroidView` 节点挂载。配置只在 parent render commit effect 中
应用，因此 parent rollback 不会 attach controller、发布 destination 或泄漏 owner。节点移除或
lifecycle owner 销毁会释放 child Session 并解绑 controller。

`NavHostController` 是唯一应用侧 mutation handle，只能绑定一个 host，detached 时拒绝命令，
并只暴露 `Committed`、`NoChange`、`Queued` 和 `Failed`。release 后同一 controller 可挂载新
host，同时保留 back stack 与 destination saveable state。

```kotlin
val navController = rememberNavHostController(
    startDestination = NavRoute("home"),
)

NavHost(
    controller = navController,
) { entry ->
    when (entry.route.name) {
        "home" -> HomePage()
        "details" -> DetailsPage(entry.route)
    }
}
```

Android driver 在 commit 后使用可取消属性动画。前进/返回遵循布局方向，支持 slide、fade-only
和 disabled，并在完成/取消时复位所有 View 属性。host 未布局或 detached 时立即 settle，避免
不可见动画无限持有资源。

### Stage 5：恢复与平台返回

`rememberNavHostController` 在当前 ViewCompose saveable-state registry 中登记一个版本化、
Bundle-safe envelope，保存完整 stack set、active stack、选择历史、稳定 entry/graph instance
ID、typed route argument，以及每个保留 leaf/graph owner 的 Android SavedState bundle。

恢复时在 `NavHost` attach 前直接从 snapshot 创建纯 back-stack controller；每个 owner 只消费
匹配自身 restored entry ID 的 bundle。仅因转场保留、但已不在 committed stack 的 outgoing
页面不保存。release 后重新挂载同一 controller 也保留 destination state。

format-4 codec 拒绝未知格式、空或重复 stack、配置不匹配、损坏 route 和无效 typed argument；
错误数据会原子回退到全部配置 stack root，不发布局部损坏状态。

真实进程死亡 runner 构建两个保留 tab stack，各含两个 entry，记录 stack/selection history、
leaf/graph ID、`rememberSaveable`、`SavedStateHandle` 和 graph route argument；应用进入后台后
只杀应用进程，再前置原 task。认证要求 PID 变化且完整状态精确恢复：

```bash
ANDROID_SERIAL=<device> tools/navigation/validate_android_process_death.sh
```

`NavHost` 向最近的 `OnBackPressedDispatcherOwner` 登记一个 lifecycle-aware callback。仅当
`systemBackEnabled`、host attached 且 controller 可生成系统 Back 命令时启用。active stack
不止 root 时执行 pop；位于 root 时，`PreviousStack` 回到最近选择 stack，否则交给外层 host、
其他回调或平台 fallback。

普通 Back 与 Predictive Back 共用事务边界。Predictive Back 开始时只预览当前 top 与前一个
destination/stack，不改变 committed stack；progress 只驱动原生 View；取消恢复 View、可见性
和 lifecycle；完成先刷新揭页，再提交 Back，并用正常转场协议完成剩余 motion。程序命令会先
redirect 活跃 preview；host detach、callback 禁用或销毁会取消 preview 并恢复 settled scene。

### Stage 6：设备验证与 P0 合并门禁

Android 13/API 33 套件验证真实系统 Back、AndroidX predictive progress/cancel/commit、程序导航
redirect、`systemBackEnabled`、Activity 重建、30 轮连续 push/Back，以及活跃转场期间 stop/
resume/recreate。也验证在 `Activity.onCreate` 的 `INITIALIZED` 状态直接挂载 `NavHost`。

Samsung SM-G991B 和 API 33 AVD 建立兼容基线，后者 7/7 通过并完成真实进程死亡 runner。
Android 15/API 35 Pixel 9a AVD 在手势导航模式验证真实 OS 边缘手势逐帧 View translation/alpha、
反向取消和完成提交。平台 `input` 命令无法稳定复现 Predictive Back，因此 runner 使用 emulator
认证硬件触摸通道：

```bash
tools/navigation/validate_android_predictive_back.sh
```

JVM/Robolectric 还运行 100 轮 lifecycle/transaction race，确认旧转场回调不能改变 settled
stack；public-host 集成测试持有真实 owner、ViewModel、provider 和 container，要求 pop/dispose
释放旧资源，remount 只通过新实例恢复已提交状态。

每次导航变更必须通过：

1. `viewcompose-navigation-android` 完整单测；
2. API 33 Back/lifecycle 与真实进程死亡；
3. API 35 平台 Predictive Back 与真实进程死亡；
4. 仓库 `qaFull`。

```bash
./gradlew :viewcompose-navigation-android:testDebugUnitTest --no-configuration-cache

ANDROID_SERIAL=<api33-device> ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.NavigationBackDeviceTest \
  --no-configuration-cache
ANDROID_SERIAL=<api33-device> tools/navigation/validate_android_process_death.sh

ANDROID_SERIAL=<api35-emulator> tools/navigation/validate_android_predictive_back.sh
ANDROID_SERIAL=<api35-emulator> tools/navigation/validate_android_process_death.sh
ANDROID_SERIAL=<api35-emulator> ./gradlew qaFull --no-configuration-cache
```

## 3. P1 交付

P1 复用 P0 的事务、页面所有权、恢复与平台 Back。graph、tab、deep link 或自适应 placement
可以选择 destination，但不得在 `NavBackStackController` 之外发布第二份导航状态。

### Stage 7A：嵌套 graph 内核

纯 core 提供不可变 `navGraph` DSL。graph/destination route 名全局唯一；graph start 必须是直接
child；进入 graph route 会在分配或发布 entry 前递归解析到 leaf start。graph route 参数覆盖
每层 nested start route 默认值。

每个 committed `NavEntry` 保存完整 `graphEntries`。`NavGraphEntry` 包含 graph route、typed
argument 与稳定 instance ID。leaf 间直接导航复用共同 graph instance 前缀；显式进入 graph
或 reset 分配新路径。SingleTop、replace、rollback 与转场保留比较 leaf 和完整 graph path。

format 4 保存 graph instance、route、argument 与完整 retained stack set；恢复时验证路径与共享
instance 一致性，失败则原子回退到配置 root。

```kotlin
val graph = navGraph(
    route = "app",
    startDestination = NavRoute("home"),
) {
    destination("home")
    navigation(
        route = "account",
        startDestination = NavRoute("profile"),
    ) {
        destination("profile")
        destination("security")
    }
}

val navController = rememberNavHostController(graph)
navController.navigate(NavRoute("account"))
```

### Stage 7B：graph scope Android 所有权

每个 live `NavGraphEntry` 拥有稳定 `NavGraphOwner`，实现 LifecycleOwner、ViewModelStoreOwner、
SavedStateRegistryOwner，并拥有 ViewCompose saveable-state namespace。同一 graph instance 的
sibling leaf 共享 graph state，但不共享 leaf owner；最后一个引用 leaf 被 pop、reset 或 host
销毁时，graph owner 只释放一次。

`LocalNavGraphOwnerScope` 可按 route 查 owner；`ProvideNavGraphOwner` 为 subtree 安装其 lifecycle、
ViewModel、SavedState 和 saveable-state Local。候选只创建 committed stack 缺少的 owner；失败
只销毁候选 owner。commit 后 parent graph 先启动，leaf/child 先销毁；转场可见 graph 至少
`STARTED`，隐藏保留 graph 为 `CREATED`。

### Stage 8：独立保留的 tab back stack

`NavStackConfiguration` 声明稳定 stack ID、各自 start route、初始选择和 root Back 策略。
controller 在一个不可变 `NavStackSetSnapshot` 中拥有全部 stack，且全局最多一个 prepared
transaction。route mutation 只操作 active stack；`selectStack` 原子切换而不重建 controller。

```kotlin
val homeStack = NavStackId("home")
val searchStack = NavStackId("search")
val tabs = NavStackConfiguration(
    initialStackId = homeStack,
    stacks = listOf(
        NavStackSpec(homeStack, NavRoute("home")),
        NavStackSpec(searchStack, NavRoute("search")),
    ),
    rootBackBehavior = NavRootBackBehavior.PreviousStack,
)
val navController = rememberNavHostController(
    stackConfiguration = tabs,
    graph = graph,
)
```

active stack pane scene 可见、可交互且 `RESUMED`；inactive stack 隐藏并限制为 `CREATED`。
`Preserve` 恢复离开位置，`PopToRoot` 只销毁当前 stack root 上方 entry。selection history 稳定、
排除 active stack 并随完整 stack set 保存。失败 render/switch 或取消 preview 都保留此前 committed
selection、history、Session 与 owner。

### Stage 9：严格 graph deep link

`NavDeepLink` 在 graph 或 destination 登记白名单 URI pattern。scheme、host、可选 port、path
segment 与 query key 必须严格匹配。placeholder 占完整 segment/value，并解析为声明的 typed
route argument。损坏编码、fragment、userinfo、重复 query、错误 typed value、歧义 pattern，
以及更具体 pattern 失败后退到更宽 pattern 都会拒绝。

`OpenDeepLink` 在一个事务中处理 route mutation、目标 stack 选择和 history。字符串、Android
`Uri` 与 `ACTION_VIEW Intent` 共用 resolver 与事务边界；render 失败恢复完整旧 stack set。
纯 core 随机模型覆盖 128,000 次确定性事务，Android 验证命令为：

```bash
ANDROID_SERIAL=<device> tools/navigation/validate_android_deep_links.sh
```

### Stage 10：自适应原生 View pane

`NavPaneStrategy` 在 active committed stack 上解析不可变 `NavPaneScene`。scene 使用连续
`Primary`/`Secondary`/`Tertiary` role，只能引用该 stack entry，且必须包含 top。
`Single` 保持单 pane；`BackStack` 暴露最新一至三个 entry。

Android `NavPanePolicy` 按宿主宽度、density、最小 pane 宽度、最大数量和间距计算 pane 数。
默认 `Single`，可选择 `Adaptive` 或自定义策略。宽度变化只更新同一 committed entry 的 placement
与 lifecycle，不创建第二 stack，不替换 container，也不重建 owner。settled pane 均可交互且
`RESUMED`，其余保留 entry 隐藏并为 `CREATED`。

转场携带前后 pane scene 的可见 union，只动画真正进入/离开的 entry，共享 pane 不作为 outgoing。
RTL 下原生布局对等分配像素余数、间距并镜像 start edge。

## 系统导航验收 Demo

Demo 目录中的 `系统导航验收` 使用生产 `NavHost`。Activity 只拥有 Android window 并转发
`ACTION_VIEW`；destination、stack、lifecycle、ViewModel、SavedState、转场和 pane 均由框架拥有。

页面显示 route/argument、entry/graph owner ID、lifecycle、三个 tab stack、selection history、
最后事务与 deep-link 结果，并覆盖所有 stack 操作、PreviousStack、Predictive Back、nested graph、
entry/graph state、strict deep link、转场、pane、Activity recreation 与 owner identity。

```bash
adb shell am start \
  -a android.intent.action.VIEW \
  -d 'viewcompose://demo/account/42' \
  com.gzq.uiframework
```

建议依次验证各 tab 独立栈与 owner ID、root system Back history、Predictive Back 取消/提交、有效与
无效 deep link、外部 account URI、旋转后的状态恢复，以及点击 `准备三窗格样例` 后横屏三 pane
不替换 entry/graph owner。

```bash
ANDROID_SERIAL=<device> ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.SystemNavigationDemoDeviceTest \
  --no-configuration-cache
```

## 4. 事务不变量

1. `prepare` 创建但不发布不可变候选 snapshot。
2. Android 层准备并渲染所需页面 Session。
3. `commit` 发布候选；`rollback` 放弃候选并保留旧 snapshot。

一个 controller 同时最多一个 prepared transaction；它必须先 commit/rollback。被放弃事务分配的
entry ID 永不复用，避免旧 SavedState、ViewModel、overlay 或 result 归属到后续页面。

stack commit 前失败保留旧 stack、可见页和 lifecycle owner。commit 后应用 effect 出现不可恢复
失败时，coordinator 进入 `Failed` 并拒绝新命令。视觉转场只在页面与 stack 事务 commit 后开始；
取消不回滚应用状态，所有终态都收敛到 committed target。移除资源在转场终态按 top-first 清理，
只有 active transition ID 可完成。

## 5. 生命周期不变量

| destination 角色 | 目标状态 |
| --- | --- |
| 可交互 pane 路径上的 graph | `RESUMED` |
| 可见转场路径 graph | `STARTED` |
| 隐藏保留 graph | `CREATED` |
| settled 可交互 destination | `RESUMED` |
| 可见但不可交互/转场 destination | `STARTED` |
| 隐藏保留 destination | `CREATED` |
| prepared 未 commit destination | host 为 `INITIALIZED` 时相同，否则 `CREATED` |
| 永久移除 destination | `DESTROYED` |

所有权变化先降级旧交互项，再升级新项。多个 leaf 只有位于同一有效 settled pane scene 时可同时
`RESUMED`；parent graph 先于 child 升级。`Activity.onCreate` 的 `INITIALIZED` 状态允许挂载；
host `DESTROYED` 会销毁全部 leaf/graph owner，已销毁 instance ID 不得重新引入。

## 6. 剩余范围

P1 能力集已经实现，合并前只剩稳定性工作：最终 rebase 后重复 API 33/35 设备矩阵，收集 release
模式转场与自适应 relayout benchmark，并在完整仓库和设备 soak 稳定通过前保持分支隔离。

编译器生成 route 序列化仍是明确非目标。后续能力必须建立在同一事务和所有权契约上，不增加
并行导航路径。
