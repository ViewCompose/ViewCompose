---
title: 使用文本输入
sidebar_position: 5
translation_source: tutorials/text-input.md
translation_source_hash: 9204fa8f0e3251dd09a3b0d724cbe74a3c9d0ab43ade9a372c40f7b4e2aacdec
translation_status: current
---

# 使用文本输入

## 必需依赖

本页可以独立使用。Android 宿主会传递引入文本编辑与基础应用 API，因此不需要单独依赖
`viewcompose-text-core`：

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha03")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## 将输入框绑定到可编辑状态

创建 `TextInputTutorialActivity.kt`：

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TextInputTutorialActivity.kt" region="text-input" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextField
import com.viewcompose.widget.core.rememberTextFieldState

class TextInputTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            val name = rememberTextFieldState()

            Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                TextField(
                    state = name,
                    hint = "Your name",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(if (name.text.isBlank()) "Hello" else "Hello, ${name.text}")
            }
        }
    }
}
```
{/* tutorial-sample-end */}

`rememberTextFieldState` 同时拥有文本、选择范围、IME composition 和编辑历史。原生编辑器会更新
该状态，界面读取 `name.text` 后即可同步更新问候语，不需要额外的字符串回调。

## 验证结果

输入姓名，并确认每次编辑都会更新问候语。编译命令：

```bash
./gradlew :samples:tutorials:assembleDebug
```

输入校验、富文本、撤销和 Receive Content 请查看[文本输入指南](../guides/text-input.md)。
