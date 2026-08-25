---
title: 使用主题
sidebar_position: 7
translation_source: tutorials/theming.md
translation_source_hash: 220462d5f5347df6b0a79f783b46e20955e93e2da38ed8985daed680160745b4
translation_status: current
---

# 使用主题

## 必需依赖

本页可以独立使用。主题 API 位于 `viewcompose-ui-foundation`，Android 主题解析由
`viewcompose-host-android` 安装：

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
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

{/* tutorial-sample sample_id="tutorial.theming" source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ThemingTutorialActivity.kt" region="theming" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme

class ThemingTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
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

`setMaterial3UiContent` 会读取 Android 主题并提供 ViewCompose 语义 token。构建界面时直接读取
`Theme.colors`，不要长期保存已经解析的颜色整数。明暗配置变化后，宿主会刷新这些 token。

## 验证结果

切换设备明暗模式，确认背景、主色文本和按钮始终清晰可读。编译命令：

```bash
./gradlew :samples:tutorials:assembleDebug
```

自定义 token、动态颜色策略和运行时刷新请查看[主题指南](../guides/theming.md)。
