---
title: 使用 Overlay
sidebar_position: 9
translation_source: tutorials/overlays.md
translation_source_hash: f80b6af772840d5d57cdfb8f9b65efb6544f5fc894ebfe08a630e31516b5e72e
translation_status: current
---

# 使用 Overlay

## 必需依赖

本页可以独立使用。显示对话框必须引入单独的 Android Overlay 宿主产物：

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-android:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-overlay-material3-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

缺少 `viewcompose-overlay-material3-android` 或 `overlayHostFactory` 时，`Dialog` 声明没有对应的 Android
presenter，无法显示。

## 显示确认对话框

创建 `OverlaysTutorialActivity.kt`：

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/OverlaysTutorialActivity.kt" region="overlays" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.android.setUiContent
import com.viewcompose.overlay.material3.android.host.AndroidOverlayHost
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Dialog
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.remember

class OverlaysTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent(overlayHostFactory = ::AndroidOverlayHost) {
            val dialogVisible = remember { mutableStateOf(false) }

            Button("Delete item", onClick = { dialogVisible.value = true })
            Dialog(
                visible = dialogVisible.value,
                requestKey = "delete-item",
                onDismissRequest = { dialogVisible.value = false },
            ) {
                Surface(modifier = Modifier.fillMaxWidth()) {
                    Column(spacing = 12.dp, modifier = Modifier.padding(20.dp)) {
                        Text("Delete this item?")
                        Button("Cancel", onClick = { dialogVisible.value = false })
                    }
                }
            }
        }
    }
}
```
{/* tutorial-sample-end */}

Boolean 是应用状态，`Dialog` 只声明如何显示这个状态。`requestKey` 为请求提供跨重组的稳定身份。
取消按钮和平台关闭动作都更新同一个状态所有者。

## 验证结果

点击 `Delete item`，再通过 `Cancel`、Back 或点击外部关闭对话框。编译命令：

```bash
./gradlew :samples:tutorials:assembleDebug
```

Popup、BottomSheet、Snackbar、Toast 和队列行为请查看 [Overlay 指南](../guides/overlays.md)。
