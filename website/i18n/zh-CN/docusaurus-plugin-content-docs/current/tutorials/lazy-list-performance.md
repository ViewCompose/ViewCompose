---
title: 调整 Lazy 列表性能
sidebar_position: 13
translation_source: tutorials/lazy-list-performance.md
translation_source_hash: d8bf63c358b0aee5578ba693a093332e8db5327738fa0251deb0f80c65e7d85a
translation_status: current
---

# 调整 Lazy 列表性能

## 必需依赖

本页可以独立使用。集合策略位于基础 UI 契约，不需要额外的性能产物：

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

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LazyListPerformanceTutorialActivity.kt" region="lazy-list-performance" */}
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
                prefetchPolicy = LazyLayoutPrefetchPolicy(
                    initialPrefetchItemCount = 4,
                    itemViewCacheSize = 4,
                ),
                reusePolicy = CollectionReusePolicy(sharePool = true),
                modifier = Modifier.fillMaxSize().padding(16.dp),
            ) { row ->
                Text(row, modifier = Modifier.fillMaxWidth().padding(8.dp))
            }
        }
    }
}
```
{/* tutorial-sample-end */}

预取数量与缓存大小只是交给原生集合 renderer 的提示，不改变条目语义。共享池只适用于结构兼容的
列表，而且复用行仍必须完整 rebind。更大的缓存会消耗更多内存，因此应先测量再调整。

## 验证结果

在同一设备和 release 构建上，分别测量默认值与显式策略的滚动表现。编译示例：

```bash
./gradlew :samples:tutorials:assembleDebug
```

基准条件和回归预算请查看[性能指南](../tooling/performance.md)。
