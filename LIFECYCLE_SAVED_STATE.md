# Lifecycle 与 SavedState

## 1. 目标

本文定义宿主生命周期、生命周期感知 Flow 和 `rememberSaveable` 的提交/恢复边界。

核心原则：

1. 未提交的组合不能永久消费恢复值。
2. 宿主保存发生在组合准备期间，也不能丢失已 claim 的值。
3. 生命周期快速切换时，同一个 Flow 最多只有一个活跃 collector。
4. 已销毁宿主不能创建新的渲染会话或 SavedState 绑定。

## 2. 宿主生命周期

`ComponentActivity.setUiContent` 的会话绑定到 Activity 生命周期；
`Fragment.setUiContent` 的会话绑定到 Fragment view lifecycle，并在 view 销毁时释放。

边界规则：

1. 重复调用 `setUiContent` 会先释放旧会话。
2. `ON_DESTROY` 释放会话、组合副作用、协程和平台资源。
3. 对 `DESTROYED` 宿主调用 `setUiContent` 立即失败，不创建半绑定会话。
4. `LifecycleBoundDisposer` 绑定到已经销毁的 owner 时立即执行释放。

## 3. 生命周期感知 Flow

`collectAsStateWithLifecycle` 只接受：

- `CREATED`
- `STARTED`
- `RESUMED`

`INITIALIZED` 与 `DESTROYED` 不能作为活跃阈值。

实现使用 `repeatOnLifecycle` 的串行取消/重启语义：前一个 collector 的取消和
`finally` 清理完成后，下一次 collector 才能进入，避免快速 `STOP -> START` 产生并发收集。
组合释放会取消整个结构化收集作用域。

## 4. rememberSaveable 恢复事务

恢复分为四步：

1. 组合 prepare 时按稳定 key `claimRestored`。
2. claim 中的值参与恢复，但仍包含在 `performSave()` 快照中。
3. 组合 commit 后注册 provider，再提交 claim。
4. 组合 abort 或新值被 abandoned 时释放 claim，后续重试仍可恢复同一值。

因此，组合异常、renderer apply 回滚、保存与渲染交错都不会提前丢失恢复值。

`rememberSaveable(inputs...)` 的 input 变化仍表示有意重置：旧 holder 只在提交阶段
退出，新 holder 同步接管 provider，最终保存替换后的值。

## 5. Android Bundle 边界

Android host 保存：

- `null`
- Bundle 可保存的平台值
- 递归 `List`
- String-keyed `Map`

恢复时会安装宿主 class loader。未知格式版本整体忽略；单个损坏 entry 被隔离，
不会阻止其余有效 entry 恢复。

瞬时系统会话不属于 SavedState：IME composition、撤销历史、进行中的手势和动画不会恢复。

## 6. 验证

核心回归覆盖：

1. nullable、嵌套集合和自定义 Saver
2. composition abort 后重试恢复
3. in-flight claim 期间宿主保存
4. 快速生命周期停止/重启的 collector 串行性
5. destroyed owner
6. 未知 Bundle 版本和单 entry 损坏隔离
