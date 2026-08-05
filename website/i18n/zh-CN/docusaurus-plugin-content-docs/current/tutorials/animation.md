---
title: 使用 AnimatedVisibility
sidebar_position: 11
translation_source: tutorials/animation.md
translation_source_hash: f83fdb754300d4ce452647b14b15de1363c83e3ab8846bb211f4397ab7f13a26
translation_status: current
---

# 使用 AnimatedVisibility

## 必需依赖

本页可以独立使用。可见性动画必须引入单独的 `viewcompose-animation` 产物：

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha03")
    implementation("com.viewcompose:viewcompose-animation:0.1.0-alpha03")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## 为内容可见性添加动画

创建 `AnimationTutorialActivity.kt`：

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/AnimationTutorialActivity.kt" region="animation" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.animation.AnimatedVisibility
import com.viewcompose.host.android.setUiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.remember

class AnimationTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            val visible = remember { mutableStateOf(true) }

            Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                Button(
                    if (visible.value) "Hide message" else "Show message",
                    onClick = { visible.value = !visible.value },
                )
                AnimatedVisibility(visible = visible.value) {
                    Text("Animated content")
                }
            }
        }
    }
}
```
{/* tutorial-sample-end */}

修改 `visible` 会启动默认的淡入淡出与尺寸过渡。退出动画完成前内容仍会保留，之后才移除。首次
composition 直接处于稳定状态，因此不会播放进入动画。

## 验证结果

连续切换按钮，确认被打断的动画会从当前状态继续。编译命令：

```bash
./gradlew :samples:tutorials:assembleDebug
```

自定义过渡和保留的过渡状态请查看[动画模块手册](../modules/viewcompose-animation/README.md)。
