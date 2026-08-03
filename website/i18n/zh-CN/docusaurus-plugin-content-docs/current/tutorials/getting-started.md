---
title: 构建第一个应用
sidebar_position: 1
translation_source: tutorials/getting-started.md
translation_source_hash: 235461cc3aa60552ccc43d49c26129e1c557d364e623da728f01c2a1d723c99d
translation_status: current
---

# 构建第一个 ViewCompose 应用

本教程会构建一个由 Android View 渲染的可运行计数器。点击按钮会更新快照状态、使读取该状态的
界面失效，并 patch 已存在的原生 View 树。

完整且参与编译的应用位于
[`samples/counter`](https://github.com/ViewCompose/ViewCompose/tree/main/samples/counter)。下面的代码复制自该模块，
`qaQuick` 会编译应用及其设备测试，因此教程不会在公开 API 变化后静默失效。

## 将要构建的内容

应用只包含一个 Activity 和一棵声明式 UI 树：

- 居中显示 `Count: 0` 的文本；
- 一个 `Increment` 按钮；
- 驱动文本更新的保留快照状态；
- 负责生命周期、SavedState、主题与渲染服务的 Android 宿主。

预期结果：每次点击都会增加可见计数，且不需要替换 Activity。

## 前置条件与验证基线

你需要一个使用 Kotlin 的 Android 应用、Android SDK，以及供 Android Gradle Plugin 使用的
JDK 17。仓库示例使用 `compileSdk = 36`、`minSdk = 24` 和 JVM target 11。

本教程最后验证于 2026-08-03，使用以下 ViewCompose 产物：

| 产物 | 版本 | 显式依赖原因 |
| --- | --- | --- |
| `viewcompose-runtime` | `0.1.0-alpha01` | 快照状态与观察机制 |
| `viewcompose-ui-contract` | `0.1.0-alpha01` | `Modifier`、布局单位与对齐契约 |
| `viewcompose-widget-core` | `0.1.0-alpha01` | `Column`、`Text`、`Button`、主题默认值与 `remember` |
| `viewcompose-host-android` | `0.1.0-alpha01` | Activity 宿主与 Android renderer 安装 |

ViewCompose 产物独立演进。混用比本教程更新的版本前，请检查
[已发布模块目录](../modules/README.md)。

## 1. 添加依赖

确认应用可以解析 Maven Central，然后在应用模块中显式添加四层 ViewCompose 依赖：

```kotlin title="build.gradle.kts"
dependencies {
    implementation("com.viewcompose:viewcompose-runtime:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-ui-contract:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-widget-core:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha01")

    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

仓库示例使用职责相同的 project dependency，以便始终验证当前源码；外部使用者则使用上面的
Maven 坐标。

## 2. 使用 Material 应用主题

宿主会从 Android 主题解析 ViewCompose token。Android Studio 新建的 View 应用通常已经提供
合适的 Material 主题。计数器示例使用：

```xml title="res/values/themes.xml"
<resources>
    <style name="Theme.ViewCompose.Counter" parent="Theme.Material3.DayNight.NoActionBar" />
</resources>
```

在 `AndroidManifest.xml` 中把该主题应用到 Application 或 Activity。ViewCompose 会跟随宿主的
明暗配置和 Android 主题桥接；这里不涉及 Compose Theme。

## 3. 安装声明式内容

用参与编译的
[`MainActivity.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/samples/counter/src/main/java/com/viewcompose/samples/counter/MainActivity.kt)
替换生成的 Activity 内容：

```kotlin
package com.example.counter

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            val count = remember { mutableStateOf(0) }

            Column(
                spacing = 16.dp,
                arrangement = MainAxisArrangement.Center,
                horizontalAlignment = HorizontalAlignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                Text(
                    text = "Count: ${count.value}",
                    style = TextDefaults.titleLargeStyle(),
                )
                Button(
                    text = "Increment",
                    onClick = { count.value += 1 },
                )
            }
        }
    }
}
```

四部分共同形成完整更新路径：

1. `setUiContent` 安装生命周期感知的 Android 宿主并执行首帧渲染。
2. `remember` 在组合位置保留状态对象。
3. 读取 `count.value` 会让当前组合作用域订阅状态失效。
4. 按钮写入新值；ViewCompose 重组受影响作用域并 patch 原生 `TextView`，而不是重建 Activity。

`remember` 会在当前组合存续期间保留值。如果值还需要跨 Activity 重建或进程恢复，请使用
`rememberSaveable`，详见[生命周期与 SavedState](https://docs.viewcompose.com/architecture/lifecycle-and-saved-state)。

## 4. 运行与验证

可以从 Android Studio 运行应用，或在仓库根目录构建示例：

```bash
./gradlew :samples:counter:assembleDebug
```

连接模拟器或设备后，安装示例并运行点击回归：

```bash
./gradlew :samples:counter:installDebug
./gradlew :samples:counter:connectedDebugAndroidTest
```

测试会在真实 Android View 层级上断言 `Count: 0`，点击 `Increment`，然后断言 `Count: 1`。

## 下一步

- 阅读[状态快照](https://docs.viewcompose.com/architecture/state-snapshots)，理解事务与观察规则。
- 阅读[主题](https://docs.viewcompose.com/guides/theming)，再定义应用 token 或动态色策略。
- 阅读[预览工具](https://docs.viewcompose.com/tooling/preview)，在 Android Studio 中渲染标注的 DSL 函数。
- 通过[模块目录](../modules/README.md)按需添加导航、文本编辑、图形或其他可选能力，避免把它们
  全部拉入最小应用。
