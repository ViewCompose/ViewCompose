---
title: 使用 AndroidView
sidebar_position: 10
translation_source: tutorials/android-view.md
translation_source_hash: fa2e7407f3c54d1d9d22b339cbe9bd1807ff275d1cd9e2981536c68092505533
translation_status: current
---

# 使用 AndroidView

## 必需依赖

本页可以独立使用。`AndroidView` 由 `viewcompose-host-android` 提供，不需要额外的 interop 产物：

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha03")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## 嵌入并更新 TextView

创建 `AndroidViewTutorialActivity.kt`：

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/AndroidViewTutorialActivity.kt" region="android-view" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.AndroidView
import com.viewcompose.host.android.setUiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.remember

class AndroidViewTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            val count = remember { mutableStateOf(0) }

            Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                AndroidView(
                    factory = { context -> TextView(context) },
                    update = { view ->
                        (view as TextView).text = "Native TextView count: ${count.value}"
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button("Increment", onClick = { count.value += 1 })
            }
        }
    }
}
```
{/* tutorial-sample-end */}

只有 reconciliation 需要新节点时，`factory` 才创建原生 View。`update` 把最新状态应用到保留的
View，而且必须允许 rollback 或 rebind 时再次执行；不要在其中执行一次性的外部副作用。

## 验证结果

点击 `Increment`，确认已经挂载的原生 `TextView` 会更新。编译命令：

```bash
./gradlew :samples:tutorials:assembleDebug
```

所有权和清理规则请查看[宿主、生命周期与 Android 互操作](../migration/compose-host-lifecycle-and-android-interop.md)。
