---
translation_source: tutorials/navigation.md
translation_source_hash: 5e713d61576fa681be7b2acdd2933b7521ecad071810f9aa9cd4b6c905500628
translation_status: current
---

# 使用导航

## 必需依赖

本页可以独立使用。请添加 Maven Central、`viewcompose-material3-android:0.1.0-alpha01`、
`viewcompose-navigation-android:0.1.0-alpha01`、`androidx.activity:activity:1.12.4` 和
`com.google.android.material:material:1.13.0`。Android 导航产物会传递引入平台无关的 Route
模型。

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

remember 的 Controller 持有已提交返回栈。`NavHost` 渲染当前 `NavRoute`，并把 Android 系统
返回接到同一个栈。Host 挂载后，从 UI 事件调用 `navigate` 或 `popBackStack`。

## 验证结果

点击 `Open details`，再通过界面上的 `Back` 返回。重新打开 Details，然后使用 Android 系统
返回。两条路径都必须只返回 Home 一次。使用
`./gradlew :samples:tutorials:assembleDebug` 完成编译。

需要验证恢复、显式失败处理和 Predictive Back 时，请继续阅读
[配置可上线的导航宿主](../guides/navigation.md)。
