# ViewCompose Preview

The `viewcompose-preview` module provides two development-time preview paths:

- Android Studio Compose Preview renders ViewCompose DSL through `ViewComposePreviewHost`.
- Paparazzi snapshot regression consumes the same `PreviewCatalog`, avoiding separate Preview and
  screenshot definitions.

## Application integration (recommended)

Production-facing previews belong in the consuming application module and call the public
`:viewcompose-preview` APIs directly:

- `com.viewcompose.preview.ViewComposePreview`
- `com.viewcompose.preview.ViewComposePreviewWithRoot` (for pages that require the root
  `ViewGroup` while building)
- `com.viewcompose.preview.ViewComposePreviewOptions`
- `com.viewcompose.preview.ViewComposePreviewTheme`

The consuming module—not `:viewcompose-preview`—enables Compose:

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

Application preview example:

```kotlin
@Preview(name = "Biz Light", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun BizLightPreview() {
    ViewComposePreview(
        options = ViewComposePreviewOptions(theme = ViewComposePreviewTheme.Light),
    ) {
        // Build the application DSL here.
    }
}

@Preview(name = "Biz Root-Aware", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun BizRootAwarePreview() {
    ViewComposePreviewWithRoot(
        options = ViewComposePreviewOptions(theme = ViewComposePreviewTheme.Dark),
    ) { root ->
        // Use root when the page DSL requires the host ViewGroup.
    }
}
```

## Studio Preview

1. Open either entry point from the `viewcompose-preview` module in Android Studio:
   - `com.viewcompose.preview.shell.PreviewShellsKt`
   - `com.viewcompose.preview.catalog.ui.CatalogPreviewsKt`
2. Use the IDE Preview panel to inspect light/dark, phone/tablet, and component-domain variants.

## Snapshot Regression

Run the module snapshot verification:

```bash
./gradlew :viewcompose-preview:verifyPaparazziDebug
```

Snapshot baselines and difference reports are written to:

`viewcompose-preview/build/reports/paparazzi/`

## Overlay preview policy

Preview scenes use static content to model overlays instead of creating real window layers. The
actual behavior of dialogs, popups, and bottom sheets is covered by instrumentation tests.
