---
title: 使用导航
sidebar_position: 8
translation_source: tutorials/navigation.md
translation_source_hash: 58c6049d35a8e5111b734d28b3b3638d59e89ecb85045302ca38b201999f4fc4
translation_status: current
---

# 使用导航

## 必需依赖

本页可以独立使用。添加 Android 导航功能模块即可；它会传递引入平台无关的路由模型：

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-navigation-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## 在两个目标页之间跳转

创建 `NavigationTutorialActivity.kt`：

{/* tutorial-sample sample_id="tutorial.navigation" source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/NavigationTutorialActivity.kt" region="navigation" required_artifacts="viewcompose-navigation-android" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.navigation.NavHost
import com.viewcompose.navigation.rememberNavHostController
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults

private const val HOME = "home"
private const val DETAILS = "details"

class NavigationTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
            val controller = rememberNavHostController(NavRoute(HOME))

            NavHost(controller = controller) { entry ->
                Column(
                    spacing = 12.dp,
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                ) {
                    when (entry.route.name) {
                        HOME -> {
                            Text("Home", style = TextDefaults.titleLargeStyle())
                            Button(
                                "Open details",
                                onClick = { controller.navigate(NavRoute(DETAILS)) },
                            )
                        }
                        DETAILS -> {
                            Text("Details", style = TextDefaults.titleLargeStyle())
                            Button("Back", onClick = controller::popBackStack)
                        }
                        else -> error("Unknown route ${entry.route.name}")
                    }
                }
            }
        }
    }
}
```
{/* tutorial-sample-end */}

被记住的 controller 拥有返回栈。`NavHost` 渲染当前 `NavRoute`，并把系统 Back 接到同一个栈。
宿主挂载后，可以从界面事件调用 `navigate` 或 `popBackStack`。

## 验证结果

点击 `Open details`，再通过 `Back` 按钮或 Android 系统 Back 返回。编译命令：

```bash
./gradlew :samples:tutorials:assembleDebug
```

类型化参数、多返回栈、SavedState 和预测性返回请查看[导航指南](../guides/navigation.md)。
