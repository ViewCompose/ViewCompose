---
title: 使用状态与事件
sidebar_position: 3
translation_source: tutorials/state-and-events.md
translation_source_hash: c03513f45a73dd17c375699ae57895cad9bb38886211b52813b114e3d4f3efe6
translation_status: current
---

# 使用状态与事件

## 必需依赖

本页可以独立使用。复制示例前，先添加 Maven Central 和 Android 宿主。宿主会传递引入 runtime
状态与 widget API：

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha03")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## 构建计数器

创建 `StateTutorialActivity.kt`。这个文件自行拥有状态、界面与 Android 宿主，不依赖其他教程文件。

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/StateTutorialActivity.kt" region="state" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.remember

class StateTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
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
