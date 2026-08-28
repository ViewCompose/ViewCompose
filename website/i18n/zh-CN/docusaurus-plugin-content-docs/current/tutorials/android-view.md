---
title: 使用 AndroidView
sidebar_position: 10
translation_source: tutorials/android-view.md
translation_source_hash: c1baa6bbd2c6ac061701e3f53e30ad5ce605a4ca5ff7b2da8cae8c23356f6a45
translation_status: current
---

# 使用 AndroidView

## 必需依赖

本页可以独立使用。`AndroidView` 由 `viewcompose-host-android` 提供，不需要额外的 interop 产物：

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="android-view-dependencies" sample_id="tutorial.android-view-dependencies" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
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
import android.view.View
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
            val largeText = remember { mutableStateOf(false) }

            Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                AndroidView(
                    factory = { context ->
                        TextView(context).apply {
                            id = View.generateViewId()
                            textSize = if (largeText.value) 20f else 14f
                        }
                    },
                    update = { view ->
                        (view as TextView).text =
                            "Native TextView #${view.id} count: ${count.value}"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    constructionKey = largeText.value,
                )
                Button("Increment", onClick = { count.value += 1 })
                Button(
                    if (largeText.value) "Use compact native text" else "Use large native text",
                    onClick = { largeText.value = !largeText.value },
                )
            }
        }
    }
}
```
{/* tutorial-sample-end */}

只有 reconciliation 需要新的构造身份时，`factory` 才创建原生 View，因此生成的 View ID
可以直观呈现替换行为。`update` 把最新计数应用到保留的 View，而且必须允许 rollback 或 rebind
时再次执行。本示例有意把文字大小作为 `factory` 所有的构造配置，所以也将其作为
`constructionKey` 传入：修改计数会保留 View，修改该 Key 则会创建并原子替换 View。不要在
`update` 中执行一次性的外部副作用。这种 Callback 形式是简洁的底层逃生路径。

## 提取可复用的类型安全 Adapter

集成会被复用或需要持有生命周期 Callback 时，应使用 `AndroidViewAdapter<V, S>`。View 类型与
完整状态快照会在全部 Callback 间保持编译期检查：

{/* compiled-region source="viewcompose-host-android/src/test/samples/com/viewcompose/host/android/samples/HostAndroidSamples.kt" region="host-android-view-adapter" sample_id="module.host-android-view-adapter" build_target=":viewcompose-host-android:compileDebugUnitTestKotlin" */}
```kotlin
fun typedAndroidViewAdapterSample(builder: UiTreeBuilder) {
    builder.AndroidView(
        adapter = NativeLabelAdapter,
        state = NativeLabelState(
            text = "Typed native label",
            enabled = true,
        ),
        key = "label",
        constructionKey = "default-text-appearance",
    )
}

private data class NativeLabelState(
    val text: String,
    val enabled: Boolean,
)

private object NativeLabelAdapter : AndroidViewAdapter<TextView, NativeLabelState> {
    override val reusePolicy: AndroidViewReusePolicy = AndroidViewReusePolicy.Resettable

    override fun create(scope: AndroidViewCreateScope): TextView = TextView(scope.context)

    override fun update(scope: AndroidViewUpdateScope<TextView>, state: NativeLabelState) {
        scope.view.text = state.text
        scope.view.isEnabled = state.enabled
    }

    override fun onReset(
        scope: AndroidViewResetScope<TextView>,
        reason: AndroidViewResetReason,
    ) {
        scope.view.text = null
        scope.view.isEnabled = false
    }
}
```

`NativeLabelState` 把 Adapter 所有的文本与启用属性放进同一个不可变快照，使 `update` 无需
未类型化的旁路即可重放完整配置。
`key` 标识逻辑条目；Adapter 实现类与 `constructionKey` 共同标识构造敏感的 View 状态。状态
变化会复用 View 且只调用 `update`；构造身份变化时会创建并绑定候选节点，只有完整事务成功后
才替换旧 View。`onReset` 只用于通过 `AndroidViewReusePolicy.Resettable` 主动允许跨 Key
Mounted Tree 复用的集成。

## 验证结果

记下 `Native TextView #` 后面的数字。点击 `Increment`，确认计数发生变化而 ID 保持不变；
然后点击原生文字大小按钮，确认文字大小和 ID 都发生变化。此时构造身份已经改变，替换符合预期。
编译命令：

```bash
./gradlew :samples:tutorials:assembleDebug
```

所有权和清理规则请查看[宿主、生命周期与 Android 互操作](../migration/compose-host-lifecycle-and-android-interop.md)。
