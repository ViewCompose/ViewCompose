package com.viewcompose.preview.shell

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.viewcompose.preview.ViewComposePreview
import com.viewcompose.preview.ViewComposePreviewOptions
import com.viewcompose.preview.ViewComposePreviewTheme
import com.viewcompose.ui.modifier.Modifier as UiModifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

/**
 * Preview 目录统一使用的外层 surface。
 * Shared outer surface used by Preview catalog entries.
 */
@Composable
internal fun ViewComposePreviewSurface(
    themeMode: ViewComposePreviewTheme,
    content: UiTreeBuilder.() -> Unit,
) {
    ViewComposePreview(
        modifier = androidx.compose.ui.Modifier,
        options = ViewComposePreviewOptions(theme = themeMode),
        content = content,
    )
}

/**
 * 验证 Preview bridge 是否能启动的最小内容。
 * Minimal content used to verify that the Preview bridge can boot.
 */
private fun UiTreeBuilder.previewBootstrapContent() {
    Column(
        spacing = 8.dp,
        modifier = UiModifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "ViewCompose Preview",
            style = UiTextStyle(fontSizeSp = 20.sp),
        )
        Text(
            text = "Compose Preview bridge is active.",
            style = UiTextStyle(fontSizeSp = 14.sp),
        )
    }
}

@Preview(
    name = "ViewCompose Phone Light",
    group = "ViewCompose/Bridge",
    widthDp = 411,
    heightDp = 891,
    showBackground = true,
    backgroundColor = 0xFFF4F1EA,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Composable
private fun ViewComposePhoneLightPreview() {
    ViewComposePreviewSurface(themeMode = ViewComposePreviewTheme.Light) {
        previewBootstrapContent()
    }
}

@Preview(
    name = "ViewCompose Phone Dark",
    group = "ViewCompose/Bridge",
    widthDp = 411,
    heightDp = 891,
    showBackground = true,
    backgroundColor = 0xFF1F1B18,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ViewComposePhoneDarkPreview() {
    ViewComposePreviewSurface(themeMode = ViewComposePreviewTheme.Dark) {
        previewBootstrapContent()
    }
}

@Preview(
    name = "ViewCompose Tablet Light",
    group = "ViewCompose/Bridge",
    widthDp = 840,
    heightDp = 1280,
    showBackground = true,
    backgroundColor = 0xFFF4F1EA,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Composable
private fun ViewComposeTabletLightPreview() {
    ViewComposePreviewSurface(themeMode = ViewComposePreviewTheme.Light) {
        previewBootstrapContent()
    }
}

@Preview(
    name = "ViewCompose Tablet Dark",
    group = "ViewCompose/Bridge",
    widthDp = 840,
    heightDp = 1280,
    showBackground = true,
    backgroundColor = 0xFF1F1B18,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ViewComposeTabletDarkPreview() {
    ViewComposePreviewSurface(themeMode = ViewComposePreviewTheme.Dark) {
        previewBootstrapContent()
    }
}
