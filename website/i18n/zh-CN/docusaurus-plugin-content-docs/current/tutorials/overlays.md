---
title: 使用 Overlay
sidebar_position: 9
translation_source: tutorials/overlays.md
translation_source_hash: 5fe17a79768c619a45ab9e31f97e0b664543708c3fbf90809f92caa7aae234ea
translation_status: current
---

# 使用 Overlay

## 必需依赖

本页可以独立使用。显示对话框必须引入单独的 Android Overlay 宿主产物：

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-runtime:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-ui-contract:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-widget-core:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-overlay-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

缺少 `viewcompose-overlay-android` 或 `overlayHostFactory` 时，`Dialog` 声明没有对应的 Android
presenter，无法显示。

## 显示确认对话框

创建 `OverlaysTutorialActivity.kt`：

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/OverlaysTutorialActivity.kt" region="overlays" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.overlay.android.host.AndroidOverlayHost
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Dialog
import com.viewcompose.widget.core.Surface
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.remember

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
