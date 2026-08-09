---
translation_source: modules/viewcompose-material3-android/README.md
translation_source_hash: 75a3aa6484a03f007d5bf0f64c45a678210d081a4ae142b53b5db5dbcc1f91b4
translation_status: current
---

# Material 3 Android 应用集成

`viewcompose-material3-android` 是 Android Material 3 应用推荐使用的单一依赖。它组合中立 Android
应用聚合模块与 Material 3 适配器，并提供具名的 Activity 和 Fragment
`setMaterial3UiContent` 入口。

该构件是平台集成与发布边界，不是第二套 Renderer。它私有持有 Material 根 Context 解析，并通过
有意设置的 `api` 依赖公开中立 Host 与 Material Token API。

## 构件与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。
- 平台：Android library，`minSdk 24`、`compileSdk 36`，Java 11 字节码。
- API 依赖：`viewcompose-android` 与 `viewcompose-material3`。
- Material Components、AppCompat、Host、Lifecycle 与 ViewModel 运行时依赖均会传递解析；只有业务
  源码直接使用对应 API 时才需要显式声明。

## 具名 Material Host

```kotlin
import com.viewcompose.material3.android.setMaterial3UiContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setMaterial3UiContent {
            Text("Hello from Material 3 ViewCompose")
        }
    }
}
```

`setMaterial3UiContent` 会在构造根节点前解析唯一的 Material Context。同一个稳定 Context 用于
创建根节点、原生子 View 和默认 Overlay；`Material3Theme` 读取并提供与之匹配的不可变 Token
快照。默认的 `Material3DynamicColorPolicy.UseIfAvailable` 会在 Android 支持时跟随动态色；需要
确定性 XML 主题输出时传入 `Disabled`。

主动调用 `Context.setTheme` 且没有重建 Activity 或触发配置变化后，使用
`Material3ThemeRefreshController` 刷新。Provider 挂载期间会观察配置变化。重复调用
`setMaterial3UiContent` 会释放旧 Render Session 并重建根节点；切换到根 Context 不同的设计系统
时必须走该路径，不能只在旧 View 上替换 Token。

## 边界规则

本模块可以依赖 Material 与中立聚合模块；`viewcompose-android`、`viewcompose-host-android`、UI
Foundation 和 Android Renderer 不得反向依赖或导入它。Material Recipe 在渲染前仍解析为中立
NodeSpec 与 Foundation 契约；具名集成不会在 Renderer 中注册 Material 分支。

## 相关文档

- [Material 3 适配器](../viewcompose-material3/README.md)
- [中立 Android 聚合模块](../viewcompose-android/README.md)
- [主题指南](../../guides/theming.md)
- [多设计系统架构](../../architecture/design-systems.md)

完整生成参考位于
[`viewcompose-material3-android` API 树](https://docs.viewcompose.com/api/viewcompose-material3-android/current/)。

## 兼容性说明

本模块从 `0.1.0-alpha01` 开始。原先使用 Material 感知型
`com.viewcompose.android.setUiContent` 的 Alpha 应用，应把坐标与导入分别替换为
`viewcompose-material3-android` 和 `setMaterial3UiContent`。Material 策略参数保留原有默认值与
行为；源码级改名是为了防止中立 Host 静默选择 Material。
