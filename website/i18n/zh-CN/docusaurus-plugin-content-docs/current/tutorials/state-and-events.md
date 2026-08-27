---
title: 使用状态与事件
sidebar_position: 3
translation_source: tutorials/state-and-events.md
translation_source_hash: 69e7175655192d9f6eda795d966f549c1999eec64b2f7b8f25a515361674d29a
translation_status: current
---

# 使用状态与事件

## 必需依赖

本页可以独立使用。复制示例前，先添加 Maven Central 和 Android 宿主。宿主会传递引入 runtime
状态与 widget API：

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="state-and-events-dependencies" sample_id="tutorial.state-and-events-dependencies" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## 构建计数器

创建 `StateTutorialActivity.kt`。这个文件自行拥有状态、界面与 Android 宿主，不依赖其他教程文件。

{/* tutorial-sample sample_id="tutorial.state-and-events" source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/StateTutorialActivity.kt" region="state" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.remember

class StateTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
            val count = remember { mutableStateOf(0) }

            Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                Text("Count: ${count.value}", style = TextDefaults.titleLargeStyle())
                Button("Increment", onClick = { count.value += 1 })
            }
        }
    }
}
```
{/* tutorial-sample-end */}

在 `AndroidManifest.xml` 中注册并运行该 Activity。`remember` 会在 composition 存活期间保留
状态对象；读取 `count.value` 会订阅变化；`onClick` 修改它以后，框架会更新已有的原生文本 View。

## 验证结果

界面从 `Count: 0` 开始，每次点击 `Increment` 都会增加数字。编译仓库中的同一示例：

```bash
./gradlew :samples:tutorials:assembleDebug
```

如果状态还要跨 Activity 重建保留，请继续查看[生命周期与 SavedState](../architecture/lifecycle-and-saved-state.md)。
