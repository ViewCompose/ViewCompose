---
title: 使用文本输入
sidebar_position: 5
translation_source: tutorials/text-input.md
translation_source_hash: 178c27db23937e02e56f8cd26e8ac9cf16ac4fc4d925d36ecb4ff1fed1a97510
translation_status: current
---

# 使用文本输入

## 必需依赖

本页可以独立使用。Android 宿主会传递引入文本编辑与基础应用 API，因此不需要单独依赖
`viewcompose-text-core`：

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## 将输入框绑定到可编辑状态

创建 `TextInputTutorialActivity.kt`：

{/* tutorial-sample sample_id="tutorial.text-input" source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TextInputTutorialActivity.kt" region="text-input" */}
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
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextField
import com.viewcompose.ui.foundation.rememberTextFieldState

class TextInputTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
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
