---
translation_source: tutorials/lazy-lists.md
translation_source_hash: daa3cc74a12a0e1b864fdd8a0ccf303dee843f80243b68aefcf20346d379e6db
translation_status: current
---

# 使用 Lazy 列表

## 必需依赖

本页可以独立使用。`LazyColumn` 位于 `viewcompose-ui-foundation`，不需要额外的集合产物：

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="lazy-collections-dependencies" sample_id="tutorial.lazy-collections-dependencies" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## 显示带稳定 key 的集合

创建 `LazyListsTutorialActivity.kt`：

{/* tutorial-sample sample_id="tutorial.lazy-lists" source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LazyListsTutorialActivity.kt" region="lazy-lists" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Text

class LazyListsTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
            val messages = List(100) { index -> "Message #${index + 1}" }

            LazyColumn(
                items = messages,
                key = { message -> message },
                contentType = { "message" },
                spacing = 8.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) { message ->
                Text(message, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
```
{/* tutorial-sample-end */}

`LazyColumn` 只在可见区域附近创建和复用行。插入或移动数据后，同一个逻辑条目的 `key` 仍要保持
稳定；`contentType` 用于归类可以安全复用同一原生结构的行。

## 验证结果

从 `Message #1` 滚动到 `Message #100` 附近，确认 Activity 始终可以响应。编译命令：

```bash
./gradlew :samples:tutorials:assembleDebug
```

只有测量结果表明默认预取和缓存不足时，才需要查看[调整 Lazy 列表性能](./lazy-list-performance.md)。
状态控制、自适应网格与 Pager 用法见[Lazy 集合指南](../guides/lazy-collections.md)。
