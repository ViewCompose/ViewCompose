---
title: 使用 AndroidView
sidebar_position: 10
translation_source: tutorials/android-view.md
translation_source_hash: d41d832345ed0019868501d4cfa93fd2f80a98136dfeae40387f0436a4e77b3b
translation_status: current
---

# 使用 AndroidView

## 必需依赖

本页可以独立使用。`AndroidView` 由 `viewcompose-host-android` 提供，不需要额外的 interop 产物：

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## 嵌入并更新 TextView

创建 `AndroidViewTutorialActivity.kt`：

{/* tutorial-sample sample_id="tutorial.android-view" source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/AndroidViewTutorialActivity.kt" region="android-view" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.AndroidView
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.remember

class AndroidViewTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
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
View，而且必须允许 rollback 或 rebind 时再次执行；不要在其中执行一次性的外部副作用。这种
Callback 形式是简洁的底层逃生路径。

## 提取可复用的类型安全 Adapter

集成会被复用或需要持有生命周期 Callback 时，应使用 `AndroidViewAdapter<V, S>`。View 类型与
完整状态快照会在全部 Callback 间保持编译期检查：

```kotlin
private data class NativeLabelState(val text: String)

private class NativeLabelAdapter(
    private val textAppearance: Int,
) : AndroidViewAdapter<TextView, NativeLabelState> {
    override fun create(scope: AndroidViewCreateScope): TextView =
        TextView(scope.context, null, 0, textAppearance)

    override fun update(scope: AndroidViewUpdateScope<TextView>, state: NativeLabelState) {
        scope.view.text = state.text
    }
}

AndroidView(
    adapter = NativeLabelAdapter(textAppearance),
    state = NativeLabelState("Native TextView count: ${count.value}"),
    key = "counter-label",
    constructionKey = textAppearance,
    modifier = Modifier.fillMaxWidth(),
)
```

`key` 标识逻辑条目；Adapter 实现类与 `constructionKey` 共同标识构造敏感的 View 状态。状态
变化会复用 View 且只调用 `update`；构造身份变化时会创建并绑定候选节点，只有完整事务成功后
才替换旧 View。`onReset` 只用于通过 `AndroidViewReusePolicy.Resettable` 主动允许跨 Key
Mounted Tree 复用的集成。

## 验证结果

点击 `Increment`，确认已经挂载的原生 `TextView` 会更新。编译命令：

```bash
./gradlew :samples:tutorials:assembleDebug
```

所有权和清理规则请查看[宿主、生命周期与 Android 互操作](../migration/compose-host-lifecycle-and-android-interop.md)。
