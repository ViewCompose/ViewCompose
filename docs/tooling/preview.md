# ViewCompose Preview

`viewcompose-preview` 模块提供两类开发态预览能力：

- Android Studio Compose Preview：通过 `ViewComposePreviewHost` 桥接渲染 ViewCompose DSL。
- Paparazzi 快照回归：消费同一份 `PreviewCatalog`，避免 Preview 与截图测试双维护。

## 业务侧接入（推荐）

真实预览应放在业务模块中编写，直接调用 `:viewcompose-preview` 提供的公开 API：

- `com.viewcompose.preview.ViewComposePreview`
- `com.viewcompose.preview.ViewComposePreviewWithRoot`（当页面构建依赖 root `ViewGroup` 时使用）
- `com.viewcompose.preview.ViewComposePreviewOptions`
- `com.viewcompose.preview.ViewComposePreviewTheme`

业务模块（不是 `:viewcompose-preview`）需要自行启用 Compose：

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
        // 这里写你的业务 DSL
    }
}

@Preview(name = "Biz Root-Aware", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun BizRootAwarePreview() {
    ViewComposePreviewWithRoot(
        options = ViewComposePreviewOptions(theme = ViewComposePreviewTheme.Dark),
    ) { root ->
        // 页面 DSL 依赖 root 场景
    }
}
```

## Studio Preview

1. 在 Android Studio 打开 `viewcompose-preview` 模块中的以下入口：
   - `com.viewcompose.preview.shell.PreviewShellsKt`
   - `com.viewcompose.preview.catalog.ui.CatalogPreviewsKt`
2. 直接使用 IDE 的 Preview 面板查看 Light/Dark、Phone/Tablet、分域组件预览。

## Snapshot Regression

执行模块级快照验证：

```bash
./gradlew :viewcompose-preview:verifyPaparazziDebug
```

快照基线与差异报告输出在：

`viewcompose-preview/build/reports/paparazzi/`

## Overlay 预览策略

Preview 场景下 Overlay 采用静态内容模拟，不渲染真实窗口层（Dialog/Popup/BottomSheet 的真实行为由 instrumentation 覆盖）。
