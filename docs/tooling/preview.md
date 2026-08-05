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

Committed snapshot baselines live in:

`viewcompose-preview/src/test/snapshots/images/`

When an intentional visual change has been reviewed, record its new baseline with:

```bash
./gradlew :viewcompose-preview:recordPaparazziDebug
```

Review every changed image before committing it. An unexplained mismatch must be fixed, not
recorded. Verification reports and difference images are written under
`viewcompose-preview/build/reports/paparazzi/`, and the repository CI runs `qaPreview` as an
independent required gate. A failed CI run retains its Paparazzi difference images and test reports
in the `qa-preview-failure-<attempt>` artifact for seven days.

The catalog harness permits at most `0.15%` total image difference solely to absorb the known
Layoutlib native editable-text glyph rasterization difference between supported macOS and Linux
hosts. Do not raise this threshold to accept unexplained layout, color, or content changes; fix the
regression or review and record an intentional baseline instead.

## Overlay preview policy

Preview scenes use static content to model overlays instead of creating real window layers. The
actual behavior of dialogs, popups, and bottom sheets is covered by instrumentation tests.
