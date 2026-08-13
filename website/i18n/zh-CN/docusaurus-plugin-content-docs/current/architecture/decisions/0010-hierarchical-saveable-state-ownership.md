---
translation_source: architecture/decisions/0010-hierarchical-saveable-state-ownership.md
translation_source_hash: 87f5953d66ee5c9cae411aa7fa7d03c99412c86f4fef4df74ef849fdf77a4336
translation_status: current
---

# ADR-0010：分层可保存状态所有权

- 状态：已接受
- 日期：2026-08-13

## 背景

Android Host 会为每个 SavedState Owner 安装一个 `SaveableStateRegistry`。延迟 ViewCompose
容器会为 Lazy Item、Pager Page、Tab 和 Overlay Surface 创建独立子 `RenderSession`，但这些
Session 之前继承同一个扁平 Host Registry。

每个子 Composer 的自动 Key 序列都从相同结构路径开始，因此两个可见子项可能同时注册
`auto:root:0:1`，相同显式 Key 也存在同样问题。Registry 正确拒绝第二个 Provider，但拒绝发生在
原生帧已经挂载之后，滚动期间会产生 Committed Frame Failure。给 Demo 增加显式 Key、覆盖
Provider 或在 Detach 时注销状态，都只会隐藏一个症状，同时保留跨子项别名、恢复污染或状态丢失。

稳定集合 Key 已经为 Diff 与回收标识逻辑子项。可保存状态所有权必须复用同一逻辑层级，而不是让
每个子 Composer 共享一个全局字符串 Key 空间。

## 决策

1. Host `SaveableStateRegistry` 继续作为根持久化边界。Navigation Entry 等 SavedState Owner
   仍各自持有独立根 Registry。
2. 每个创建独立子组合的框架容器，都在父组合中 Remember 一个内部可保存状态 Holder。Holder
   按稳定逻辑身份持有子 Registry：Lazy Item Key、解析后的 Pager Page Key、Tab Key 或 Overlay
   Surface 身份。
3. 子组合通过捕获的 Local Snapshot 接收子 Registry。自动与显式 `rememberSaveable` Key 只在
   该子 Registry 内有效。嵌套容器递归应用同一规则，形成层级，而不是编码成扁平 Key。
4. 子项 Detach 或回收时关闭 Registry Lease，并在父 Holder 中保留最后保存的 Map。相同逻辑 Key
   再次 Attach 时恢复该 Map；重排不会让状态在不同 Key 之间转移。
5. 父 Holder 通过自己的事务式 `rememberSaveable` 注册，把所有子 Map 保存为一个值。当 Scope
   内确实存在可保存状态时，逻辑 Key 必须被 Host Registry 接受。不支持的 Key 会产生带 Scope
   信息的明确诊断，而不是被 Hash 成可能冲突的字符串。
6. 同一逻辑子项只有一个活跃 Presentation 拥有持久化权。Renderer 并发创建的副本（例如分离的
   Pinned Header Presentation）从 Owner 当前 Snapshot 初始化，但不拥有持久化权，也不能覆盖
   逻辑子项的保存状态。
7. 同一子 Registry 内的重复 Provider 仍是错误。层级隔离不会放宽同一组合 Scope 内两个
   `rememberSaveable` 调用必须拥有不同显式或结构身份的不变量。
8. Container Holder 创建参与父组合事务。失败父帧不能发布候选 Holder、子 Scope、恢复值 Claim
   或子项更新。

## 公开 API 与兼容性影响

不新增面向应用的 API。这是 `viewcompose-ui-foundation` 中 `rememberSaveable` 所有权的 Q3 行为
修正：

- 显式 Key 只需在逻辑组合 Scope 内唯一，不再要求跨共享同一 Host Owner 的所有子 Session 唯一；
- Lazy Key 继续要求稳定且必填；无 Key Pager Page 使用解析后的位置，因此在调用方提供稳定 Page
  Key 之前，状态跟随位置；
- 根组合显式 Key 与 Android Bundle Bridge 格式保持不变；
- Container Holder 会占用一个父组合自动 Saveable Slot，因此同一结构 Scope 中后续自动 Key 可能
  位移，自动 Key 不属于持久兼容面；
- 不迁移由缺陷扁平命名空间写入的历史子项值。

子项状态重置与自动 Key 位移都是有意硬切。旧表示已经可能让无关子项互相别名，兼容读取器无法
安全判断所有权。需要跨框架升级维持根状态身份的应用应使用显式 `rememberSaveable` Key。

## 后果

- 兄弟与嵌套子 Session 可以安全使用相同自动或显式 Local Key。
- 子项状态可以跨 Holder 回收、Keyed Reorder 与 Host 重建恢复，同时平台无关模块无需依赖 Android
  Bundle 类型。
- Container 声明位置仍标识父 Holder。由于没有编译器转换，调用方必须保持条件 Container Call
  Site 的结构稳定，或使用 `key` 包裹。
- 适合 RecyclerView 身份但不能被已安装 Host 保存的子项 Key，在子项真正注册可保存状态前仍然
  有效；注册时会明确失败。
- 次级视觉副本不会成为第二个持久化业务状态来源。

## 被否决的替代方案

### 使用 Item Key Hash 为每个 Provider Key 添加前缀

任意 Kotlin Key 不存在无碰撞的平台无关字符串编码。Hash 冲突只会让正确性缺陷变得更罕见，
显式 Key 仍需要被拦截，因此否决。

### 只给 Demo 增加显式 Key

每个子 Composer 仍可能生成相同显式 Key。缺陷属于框架所有权边界，而不是示例页面，因此否决。

### 允许根 Registry 接受或覆盖重复 Provider

保存顺序会任意选中一个子项，恢复时还可能把状态交给另一个 Item。同一 Scope 内的重复注册仍应
暴露不变量错误，因此否决。

### Holder Detach 时直接释放 Item 状态

RecyclerView Detach 是 Presentation 事件，不代表逻辑删除。该方案会在普通滚动与预取波动期间
丢失状态，因此否决。

## 验证

架构要求以下确定性测试：

1. 兄弟项自动与显式 Key；
2. Keyed Reorder、Detach、Recycle 与 Reattach；
3. 嵌套 Lazy/Pager/Tab Scope 与 Overlay Surface；
4. 子项处于 Attach 或保留状态时的 Host Save 与重建；
5. 并发 Pinned Header 副本；
6. 失败的父组合与子组合事务；
7. 不支持的可保存 Scope Key，以及同一子 Scope 内的重复 Provider。
