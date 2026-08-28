---
translation_source: tutorials/text-input.md
translation_source_hash: 169d5f472e89ecfcb42a26887d39c2c5a1758cb8775a2cf19ad7d79a6c95c190
translation_status: current
---

# 使用文本输入

## 必需依赖

本页可以独立使用。Android 宿主会传递引入文本编辑与基础应用 API，因此不需要单独依赖
`viewcompose-text-core`：

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="text-input-dependencies" sample_id="tutorial.text-input-dependencies" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
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

输入校验、撤销、键盘用途与 IME Action 见[编辑文本](../guides/text-input.md)。注解文档、剪贴板、
拖放或 IME Payload 见[富文本与外部内容](../guides/text-input-rich-text.md)。
