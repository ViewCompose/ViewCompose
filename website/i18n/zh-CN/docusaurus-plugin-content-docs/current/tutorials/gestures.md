---
title: 使用手势
sidebar_position: 12
translation_source: tutorials/gestures.md
translation_source_hash: e434dd1d76882886a4b151d67d0df47b4f2b3c704dbcf602591954cc08e3b671
translation_status: current
---

# 使用手势

## 必需依赖

本页可以独立使用。手势 Modifier 必须引入单独的 `viewcompose-gesture` 产物：

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha03")
    implementation("com.viewcompose:viewcompose-gesture:0.1.0-alpha03")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## 处理点击与长按

创建 `GesturesTutorialActivity.kt`：

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/GesturesTutorialActivity.kt" region="gestures" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.gesture.combinedClickable
import com.viewcompose.host.android.setUiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Surface
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.remember

class GesturesTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            val message = remember { mutableStateOf("Tap or long-press the card") }

            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { message.value = "Tapped" },
                            onLongClick = { message.value = "Long-pressed" },
                        ),
                ) {
                    Text(message.value, modifier = Modifier.padding(24.dp))
                }
            }
        }
    }
}
```
{/* tutorial-sample-end */}

`combinedClickable` 让一个原生目标区分点击与长按，不需要应用自行计时。renderer 负责触摸阈值、
时间、取消和回调顺序。普通按钮操作仍应优先使用具备按钮语义的 `Button`。

## 验证结果

先点击卡片，再长按卡片，确认标签分别报告两种手势。编译命令：

```bash
./gradlew :samples:tutorials:assembleDebug
```

拖动、锚点拖动、变换、原始指针与嵌套滚动请查看[手势模块手册](../modules/viewcompose-gesture/README.md)。
