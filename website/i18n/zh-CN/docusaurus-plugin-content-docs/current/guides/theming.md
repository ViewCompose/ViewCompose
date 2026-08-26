---
translation_source: guides/theming.md
translation_source_hash: 09166fab2705bf2ce33e9b60b38d4446c572ff6a8ae10f72e24f36184f6d210c
translation_status: current
---

# 切换应用主题模式

完成[主题教程](../tutorials/theming.md)后，如果应用要提供 System、Light 或 Dark 偏好，请使用本
指南。长期有效的快照与优先级规则位于[主题架构](../architecture/theming.md)；Android 动态资源和
局部子树定制分别有独立指南。

## 由应用持有偏好

把选中模式持久化到应用自有 Repository 或可观察状态容器。每个 Activity 根节点读取同一份
状态，但分别持有自己的 `RenderSession` 和主题 Provider。不要把框架主题变成进程单例，也
不要让一个 Activity 操作另一个 Activity 的 Session。

下面的可编译 Helper 会为 System 模式选择外层已经解析的 Material 快照，为显式模式选择确定性
静态快照：

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ThemingGuideSamples.kt" region="application-theme-mode" sample_id="guide.theming-mode-switch" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
enum class AppThemeMode { System, Light, Dark }

object AppThemePreference {
    val mode = mutableStateOf(AppThemeMode.System)
}

fun UiTreeBuilder.ApplicationTheme(content: UiTreeBuilder.() -> Unit) {
    val systemTokens = Theme.current
    val selectedTokens = when (AppThemePreference.mode.value) {
        AppThemeMode.System -> systemTokens
        AppThemeMode.Light -> Material3ThemeDefaults.light()
        AppThemeMode.Dark -> Material3ThemeDefaults.dark()
    }
    Material3Theme(tokens = selectedTokens, content = content)
}
```

在每个 `setMaterial3UiContent` 根节点内调用 `ApplicationTheme { ... }`。生产环境偏好 Repository
应持久化该 Enum 并暴露一个可观察值；样例中的顶层状态只是为了明确所有权边界。

## 区分 System 与显式模式

System 模式使用各根节点的 `Theme.current`，因为 Configuration、Locale、Window 和厂商资源都
属于该根 Context。这里的显式 Light 和 Dark 模式使用确定性 token 生产器。如果显式模式还要
把 Android XML 资源应用到原生 Widget，需在应用匹配的 Context Theme 后重建或显式刷新每个
Host；只替换 token 不会修改 Android 资源。

`Context.setTheme` 和 `applyStyle` 只影响一个 Context，既不会持久化用户选择，也不会通知其他
Activity Session。应把应用偏好保持为唯一事实来源，并使用[动态颜色与刷新指南](./theming-dynamic-color.md)
处理资源生命周期变化。

## 验证任务

通过 `./gradlew :samples:tutorials:compileDebugKotlin` 编译已登记样例，然后执行以下人工路径：

1. 打开两个都安装 `ApplicationTheme` 的 Activity。
2. 在第二个 Activity 选择 Light 并返回第一个；两个根节点都必须显示 Light 快照，不能依赖重建
   才清除旧值。
3. 选择 Dark 并重复检查。
4. 选择 System，切换设备模式，确认每个根节点从自己的当前 Context 解析。
5. 隐藏一个带主题的导航目标页，切换模式后通过 Back 或 Stack Selection 重新显示；它必须在
   可见前用新快照完成渲染。

如果只有修改偏好的 Activity 更新、System 模式复用旧 Context 快照，或目标页重新出现时短暂
显示旧主题，任务即失败。
