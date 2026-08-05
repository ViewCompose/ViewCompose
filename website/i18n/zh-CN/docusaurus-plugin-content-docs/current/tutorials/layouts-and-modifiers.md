---
title: 使用布局与 Modifier
sidebar_position: 4
translation_source: tutorials/layouts-and-modifiers.md
translation_source_hash: dab049246442cc39915c38684a102969bb88c2444d0fc95e34c5da404105c5cf
translation_status: current
---

# 使用布局与 Modifier

## 必需依赖

本页可以独立使用。请添加 Android 宿主；它会传递引入 UI contract 和 widget API：

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha03")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## 构建双轴布局

创建 `LayoutsTutorialActivity.kt`：

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LayoutsTutorialActivity.kt" region="layouts" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults

class LayoutsTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            Column(
                spacing = 16.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                Text("Account", style = TextDefaults.titleLargeStyle())
                Row(
                    spacing = 12.dp,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ada", modifier = Modifier.weight(1f))
                    Button("Edit", onClick = {})
                }
            }
        }
    }
}
```
{/* tutorial-sample-end */}

`Column` 纵向排列子项，内部的 `Row` 横向排列姓名和按钮。`weight(1f)` 让姓名占用行内剩余宽度。
Modifier 顺序会影响结果：示例先填满屏幕，再添加内部边距。

## 验证结果

标题应显示在一行内容上方，`Ada` 会向 `Edit` 按钮方向扩展。编译命令：

```bash
./gradlew :samples:tutorials:assembleDebug
```

需要了解顺序和 renderer 细节时，再查看 [Modifier 架构](../architecture/modifier.md)。
