---
translation_source: modules/viewcompose-android/README.md
translation_source_hash: 46c35be9a2016969ec22f851e9b38b91979beec7e51b59c94eb62df3b3951054
translation_status: current
---

# Android 应用聚合模块

`viewcompose-android` 是标准 Android 应用推荐使用的单一基础依赖。它组合 UI Foundation、Android
Host Engine、Material 3 主题适配、Lifecycle 集成与 ViewModel 集成，并提供 Activity 和 Fragment
的 `setUiContent` 入口。

聚合模块不会重复实现这些能力，它只负责依赖编排和稳定的应用入口。高级使用方仍可直接选择更窄
的模块。

## 构件与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-android:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。
- 平台：Android library，`minSdk 24`、`compileSdk 36`，Java 11 字节码。
- 传递 API：Host Engine、UI Foundation、Material 3 适配、Lifecycle、ViewModel、AndroidX
  Activity 与 AndroidX Fragment。
- Material Components 由 Material 3 适配模块传递提供；只有业务代码直接编译 Material 类时才需
  显式声明。

## 标准入口

```kotlin
import com.viewcompose.android.setUiContent
```

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUiContent {
            Text("Hello from ViewCompose")
        }
    }
}
```

`ComponentActivity.setUiContent` 和 `Fragment.setUiContent` 会创建全尺寸根节点，并提供
Lifecycle、ViewModel、Android 状态保存、环境值、Material 3 token、动画协程上下文、帧时钟与
可替换的 overlay host。重复调用会释放旧 session；Fragment session 跟随当前 View lifecycle，
Activity session 在 Activity 销毁时结束。

## 依赖规则

把 `viewcompose-android` 作为应用基础依赖，再按需添加 Navigation、图片适配器、Overlay 或高级
阴影。除非业务确实直接使用独立底层 API 或主动约束版本，否则不要重复声明它已经传递的基础模块。

## 相关文档

- [五层架构](../../architecture/decisions/0002-five-layer-runtime-module-architecture.md)
- [快速开始](../../tutorials/getting-started.md)
- [Android 宿主引擎](../viewcompose-host-android/README.md)
- [Material 3 适配](../viewcompose-material3/README.md)

完整生成参考位于
[`viewcompose-android` API 树](https://docs.viewcompose.com/api/viewcompose-android/current/)。

## 兼容性说明

本模块从 `0.1.0-alpha01` 开始。原来的多基础依赖配置被一次性硬切为聚合依赖；入口独占
`com.viewcompose.android`，不存在旧聚合模块或 `com.viewcompose.host.android` 转发包。
