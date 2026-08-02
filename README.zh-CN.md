# ViewCompose

<p align="center">
  <strong>以 Android 原生 View 系统为引擎的声明式 UI 框架。</strong>
</p>

<p align="center">
  <a href="./README.md"><img alt="English" src="https://img.shields.io/badge/English-3C4043?style=for-the-badge"></a>
  <a href="./README.zh-CN.md"><img alt="简体中文" src="https://img.shields.io/badge/简体中文-6E56CF?style=for-the-badge"></a>
</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/com.viewcompose/viewcompose-host-android"><img alt="Maven Central" src="https://img.shields.io/maven-central/v/com.viewcompose/viewcompose-host-android?label=Maven%20Central"></a>
  <a href="https://github.com/ViewCompose/ViewCompose/actions/workflows/ci.yml"><img alt="Build" src="https://github.com/ViewCompose/ViewCompose/actions/workflows/ci.yml/badge.svg"></a>
  <a href="./LICENSE"><img alt="License" src="https://img.shields.io/badge/License-MIT-yellow.svg"></a>
  <img alt="Android API" src="https://img.shields.io/badge/Android-API%2024%2B-3DDC84">
  <img alt="Status" src="https://img.shields.io/badge/状态-Alpha-orange">
</p>

<p align="center">
  <a href="./CONTEXT.md">文档</a> ·
  <a href="https://central.sonatype.com/artifact/com.viewcompose/viewcompose-host-android">Maven Central</a> ·
  <a href="https://plugins.jetbrains.com/plugin/33290-viewcompose-preview">Android Studio 插件</a> ·
  <a href="./CONTRIBUTING.md">参与贡献</a> ·
  <a href="./ROADMAP.md">路线图</a>
</p>

ViewCompose 为使用 Android View 渲染引擎的应用提供受 Compose 启发、由状态驱动的 Kotlin
声明式 DSL。框架拥有自己的运行时、组合、差异更新和工具链模型，最终输出仍然是原生 Android
View 树。

它不是 Jetpack Compose，也不是 Compose 兼容层或编译器插件的重实现。项目有意聚焦于一套
实用的声明式框架，使其能够继续使用成熟的 Android View 生态和系统服务。

ViewCompose 现已作为完整开源项目持续维护，第一个公开 Alpha 版本已经发布到 Maven Central。
Alpha 阶段的 API 仍可能调整，在生产项目采用前，请根据自身需求验证当前版本。

## 为什么选择 ViewCompose

- **原生 View 引擎** —— 最终产物是 Android View 树，可以继续使用平台无障碍、输入法、输入、
  生命周期、主题系统以及 `AndroidView` 互操作能力。
- **声明式运行时** —— 支持可观察状态、增量重组、Key 复用、事务化渲染、结构化副作用、状态保存
  与环境传播。
- **系统级 UI 能力** —— 覆盖完整文本编辑、Lazy 容器、嵌套滚动、焦点与硬件键盘、浮层、动画、
  手势、图形，以及不要求以 Fragment 作为页面目的地的导航系统。
- **Android 主题打通** —— 支持原生主题解析、Material 色彩角色、动态色、配置变化、Shape Token
  和一致的预览输入。
- **模块独立演进** —— 平台无关 Core 与 Android Feature 可以独立依赖、独立发布，采用类似
  AndroidX 的版本管理方式。
- **配套工具链** —— 静态预览、源码联动、可视化诊断、截图回归和性能对比均与框架一同维护。

## 工作原理

```text
Kotlin DSL
   ↓
VNode / NodeSpec 树
   ↓
状态追踪 + 增量组合
   ↓
Diff / Patch 渲染器
   ↓
原生 Android View 树
```

运行时和策略 Core 会尽可能保持与 Android 平台无关，Android 模块负责将语义树连接到 View
和系统服务。高级阴影、Coil 图片加载、导航和 ConstraintLayout 等可选能力不会成为核心依赖。

## 快速开始

当前公开版本为 `0.1.0-alpha01`，可以直接从 Maven Central 获取。

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    val viewComposeVersion = "0.1.0-alpha01"

    implementation("com.viewcompose:viewcompose-runtime:$viewComposeVersion")
    implementation("com.viewcompose:viewcompose-ui-contract:$viewComposeVersion")
    implementation("com.viewcompose:viewcompose-widget-core:$viewComposeVersion")
    implementation("com.viewcompose:viewcompose-host-android:$viewComposeVersion")
}
```

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent { _ ->
            UiTheme {
                Column(
                    spacing = 12.dp,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(text = "Hello ViewCompose")
                    Button(
                        text = "Continue",
                        onClick = { /* 处理点击。 */ },
                    )
                }
            }
        }
    }
}
```

Feature 模块会在适用时带入对应的平台无关 Core，Core 也可以单独依赖：

```kotlin
implementation("com.viewcompose:viewcompose-navigation:0.1.0-alpha01")
implementation("com.viewcompose:viewcompose-animation:0.1.0-alpha01")

// 纯 Kotlin/JVM 的策略和状态模型可以独立使用。
implementation("com.viewcompose:viewcompose-navigation-core:0.1.0-alpha01")
implementation("com.viewcompose:viewcompose-animation-core:0.1.0-alpha01")
```

所有公开产物都包含源码包，可以在 IDE 中直接进入框架实现。完整的产物和版本约定见
[发布说明](./PUBLISHING.md)。

## 模块概览

| 领域 | 模块 | 作用 |
| --- | --- | --- |
| 运行时 | `viewcompose-runtime`、`viewcompose-text-core`、`viewcompose-ui-contract` | 状态、组合、编辑与语义契约 |
| Android UI | `viewcompose-widget-core`、`viewcompose-renderer`、`viewcompose-host-android` | DSL、原生 View 映射与宿主会话 |
| Android 集成 | `viewcompose-lifecycle`、`viewcompose-viewmodel`、`viewcompose-overlay-android`、`viewcompose-image-coil` | 平台生命周期与系统服务 |
| Feature/Core | `viewcompose-navigation*`、`viewcompose-animation*`、`viewcompose-gesture*`、`viewcompose-graphics*` | 独立演进的 Core 与 Android 能力 |
| 可选 UI | `viewcompose-shadow-android`、`viewcompose-widget-constraintlayout` | 高级渲染与布局集成 |
| 工具链 | `viewcompose-preview*`、`viewcompose-benchmark` | 预览、诊断、快照与性能测试 |

模块版本有意保持独立。依赖某一项 Feature 不要求同时引入无关模块，也不要求整个项目使用同一个
原子版本列车升级。

## ViewCompose Preview Android Studio 插件

[ViewCompose Preview](https://plugins.jetbrains.com/plugin/33290-viewcompose-preview) 是配套的
Android Studio 静态预览插件，提供：

- DSL 旁的预览入口，以及源码与渲染节点双向联动；
- Light/Dark、语言、布局方向、Density、字体比例和设备尺寸切换；
- 原生 View、布局、VNode、组合、Patch 与重组诊断；
- 增量刷新、全量更新、有界缓存、缩放/平移和全部预览画板；
- 隔离的 Layoutlib Worker，不把应用代码加载进 Android Studio 进程。

插件采用独立的版本和发布周期，源码与本地安装说明位于
[`tools/viewcompose-studio-plugin`](./tools/viewcompose-studio-plugin)。

## 文档

GitHub 托管的文档网站是项目的下一个阶段。在网站上线前，
[`CONTEXT.md`](./CONTEXT.md) 是稳定的文档入口，汇总架构、API、使用指南、性能、工具链和路线图。
README 将有意保持简洁，只负责说明项目性质和首次接入所需信息。

## 构建与贡献

```bash
git clone https://github.com/ViewCompose/ViewCompose.git
cd ViewCompose
./gradlew qaQuick
```

欢迎提交 Issue 和 Pull Request。进行较大改动前，请阅读[贡献指南](./CONTRIBUTING.md)、
[架构边界](./ARCHITECTURE.md)和[开发流程](./WORKFLOW.md)。

## 开源协议

ViewCompose 基于 [MIT License](./LICENSE) 开源。
