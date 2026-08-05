---
title: 使用主题
sidebar_position: 7
translation_source: tutorials/theming.md
translation_source_hash: d3ea9bf506cb10da8ccb092bd7154e73de98e11a160d78e21923c1330e1bfb99
translation_status: current
---

# 使用主题

## 必需依赖

本页可以独立使用。主题 API 位于 `viewcompose-widget-core`，Android 主题解析由
`viewcompose-host-android` 安装：

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha03")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

为 Application 或 Activity 使用 Material DayNight 主题：

```xml title="res/values/themes.xml"
<style name="Theme.Example" parent="Theme.Material3.DayNight.NoActionBar" />
```

## 读取语义颜色

创建 `ThemingTutorialActivity.kt`：

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ThemingTutorialActivity.kt" region="theming" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.Theme

class ThemingTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            Column(
                spacing = 12.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .backgroundColor(Theme.colors.background)
                    .padding(24.dp),
            ) {
                Text(
                    "Theme-aware screen",
                    color = Theme.colors.primary,
                    style = TextDefaults.titleLargeStyle(),
                )
                Text("Change the device theme to see the semantic colors update.")
                Button("Theme-aware button", onClick = {})
            }
        }
    }
}
```
{/* tutorial-sample-end */}

`setUiContent` 会读取 Android 主题并提供 ViewCompose 语义 token。构建界面时直接读取
`Theme.colors`，不要长期保存已经解析的颜色整数。明暗配置变化后，宿主会刷新这些 token。

## 验证结果

切换设备明暗模式，确认背景、主色文本和按钮始终清晰可读。编译命令：

```bash
./gradlew :samples:tutorials:assembleDebug
```

自定义 token、动态颜色策略和运行时刷新请查看[主题指南](../guides/theming.md)。
