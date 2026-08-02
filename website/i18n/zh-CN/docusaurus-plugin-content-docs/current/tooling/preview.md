---
translation_source: tooling/preview.md
translation_source_hash: f69c9f01d1cee6db6ba62348fbae6085022eee8803eb7e4d8bcc8cf71c0c643a
translation_status: current
---

# ViewCompose 预览

`viewcompose-preview` 模块提供两类开发期预览能力：

- Android Studio Compose Preview 通过 `ViewComposePreviewHost` 桥接渲染 ViewCompose DSL。
- Paparazzi 快照回归消费同一份 `PreviewCatalog`，避免分别维护 Preview 和截图定义。

## 业务侧接入（推荐）

面向实际业务的预览应写在使用方模块中，直接调用 `:viewcompose-preview` 的公开 API：

- `com.viewcompose.preview.ViewComposePreview`
- `com.viewcompose.preview.ViewComposePreviewWithRoot`（页面构建需要根 `ViewGroup` 时使用）
- `com.viewcompose.preview.ViewComposePreviewOptions`
- `com.viewcompose.preview.ViewComposePreviewTheme`

使用方模块（不是 `:viewcompose-preview`）需要自行启用 Compose：

```kotlin
plugins {
    alias(libs.plugins.android.library) // or android.application
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":viewcompose-preview"))
}
```

业务预览示例：

```kotlin
@Preview(name = "Biz Light", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun BizLightPreview() {
    ViewComposePreview(
        options = ViewComposePreviewOptions(theme = ViewComposePreviewTheme.Light),
    ) {
        // 在这里构建业务 DSL。
    }
}

@Preview(name = "Biz Root-Aware", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun BizRootAwarePreview() {
    ViewComposePreviewWithRoot(
        options = ViewComposePreviewOptions(theme = ViewComposePreviewTheme.Dark),
    ) { root ->
        // 页面 DSL 需要宿主 ViewGroup 时使用 root。
    }
}
```

## Studio Preview

1. 在 Android Studio 中打开 `viewcompose-preview` 模块中的任一入口：
   - `com.viewcompose.preview.shell.PreviewShellsKt`
   - `com.viewcompose.preview.catalog.ui.CatalogPreviewsKt`
2. 使用 IDE Preview 面板检查浅色/深色、手机/平板和不同组件领域的变体。

## 快照回归

运行模块级快照验证：

```bash
./gradlew :viewcompose-preview:verifyPaparazziDebug
```

快照基线和差异报告输出到：

`viewcompose-preview/build/reports/paparazzi/`

## 浮层预览策略

预览场景使用静态内容模拟浮层，不创建真正的窗口层。Dialog、Popup 和 BottomSheet 的
真实行为由 instrumentation 测试覆盖。
