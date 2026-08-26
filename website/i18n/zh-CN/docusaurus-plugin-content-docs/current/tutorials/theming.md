---
translation_source: tutorials/theming.md
translation_source_hash: 3dad3cf688c1894df48f09091c26c6e47ad01c1b38ee3b7202d9f35e1050f702
translation_status: current
---

# 使用主题

## 所需依赖

本页可以独立使用。Material Android 聚合产物会传递提供 UI Foundation 和 Material token
适配器。

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="theming-dependencies" sample_id="tutorial.theming-dependencies" build_target=":samples:tutorials:compileDebugKotlin" */}
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

`setMaterial3UiContent` 为原生树解析一个 Android Material Context，并提供其中的 ViewCompose
语义 token。构建界面时读取 `Theme.colors`，不要长期保存已经解析的颜色整数。明暗配置变化后，
Host 会刷新 token。

## 验证结果

切换设备明暗模式。背景、主色文本和按钮必须始终可读，并且作为一个完整快照同步变化。通过
`./gradlew :samples:tutorials:assembleDebug` 编译。

接下来可选择[应用模式切换](../guides/theming.md)、[动态颜色与资源刷新](../guides/theming-dynamic-color.md)
或[局部子树 Override](../guides/theming-local-overrides.md)。长期有效的 token 与优先级模型位于
[主题架构](../architecture/theming.md)。
