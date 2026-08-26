---
translation_source: tutorials/lazy-list-performance.md
translation_source_hash: eccfcba6f37c86629b0306c709739f2e924014660938e6cfbc3b5bdc612a1c54
translation_status: current
---

# 调整 Lazy 列表性能

## 必需依赖

本页可以独立使用。集合策略位于基础 UI 契约，不需要额外的性能产物：

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="lazy-collections-dependencies" sample_id="tutorial.lazy-collections-dependencies" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## 添加经过测量的集合提示

创建 `LazyListPerformanceTutorialActivity.kt`：

{/* tutorial-sample sample_id="tutorial.lazy-list-performance" source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LazyListPerformanceTutorialActivity.kt" region="lazy-list-performance" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Text

class LazyListPerformanceTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
            val rows = List(500) { index -> "Row #${index + 1}" }

            LazyColumn(
                items = rows,
                key = { row -> row },
                contentType = { "text-row" },
                contentRevision = { row -> row },
                prefetchPolicy = LazyLayoutPrefetchPolicy(
                    nestedInitialPrefetchItemCount = 4,
                    itemViewCacheSize = 4,
                ),
                reusePolicy = CollectionReusePolicy(
                    sharePool = true,
                    mountedTreeCacheSize = 2,
                ),
                modifier = Modifier.fillMaxSize().padding(16.dp),
            ) { row ->
                Text(row, modifier = Modifier.fillMaxWidth().padding(8.dp))
            }
        }
    }
}
```
{/* tutorial-sample-end */}

预取与缓存大小是有界 Renderer Policy，不定义业务状态。共享池只保留空 Holder 外壳；Mounted
Tree 缓存按 `contentType` 保留已经 Reset 的物理树，并在淘汰时确定性释放。`contentRevision` 会
定义条目语义：捕获的非 State 值发生变化时，其 Revision 也必须变化。更大的缓存会消耗更多内存，
因此应先测量再调整。

如果稳定父级频繁重组，并且 Profile 中出现 Selector 扫描，可在应用的不可变数据边界引入已
Remember 的 `LazyItemsSnapshot`。它不是普通 List 的通用替代：每次组合都创建新快照仍会执行扫描，
而普通数据或 Capture 改变后继续保留旧快照则不正确。

## 验证结果

在同一设备和 release 构建上，分别测量默认值与显式策略的滚动表现。编译示例：

```bash
./gradlew :samples:tutorials:assembleDebug
```

基准条件和回归预算请查看[性能指南](../tooling/performance.md)。
