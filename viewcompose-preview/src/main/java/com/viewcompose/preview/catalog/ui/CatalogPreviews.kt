package com.viewcompose.preview.catalog.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.viewcompose.preview.ViewComposePreviewTheme
import com.viewcompose.preview.catalog.PreviewCatalog
import com.viewcompose.preview.catalog.model.PreviewSpecRef
import com.viewcompose.preview.catalog.provider.CollectionPreviewSpecProvider
import com.viewcompose.preview.catalog.provider.ContainerPreviewSpecProvider
import com.viewcompose.preview.catalog.provider.ContentPreviewSpecProvider
import com.viewcompose.preview.catalog.provider.AnimationPreviewSpecProvider
import com.viewcompose.preview.catalog.provider.FeedbackPreviewSpecProvider
import com.viewcompose.preview.catalog.provider.GesturePreviewSpecProvider
import com.viewcompose.preview.catalog.provider.GraphicsPreviewSpecProvider
import com.viewcompose.preview.catalog.provider.InputPreviewSpecProvider
import com.viewcompose.preview.catalog.provider.ModifierPreviewSpecProvider
import com.viewcompose.preview.catalog.provider.NavigationPreviewSpecProvider
import com.viewcompose.preview.shell.ViewComposePreviewSurface
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.dp
import com.viewcompose.widget.core.sp

@Preview(
    name = "Content",
    group = "ViewCompose/Catalog",
    widthDp = 411,
    heightDp = 891,
)
@Composable
private fun ContentCatalogPreview(
    @PreviewParameter(ContentPreviewSpecProvider::class) specRef: PreviewSpecRef,
) {
    PreviewCatalogSpecScreen(specId = specRef.id)
}

@Preview(
    name = "Input",
    group = "ViewCompose/Catalog",
    widthDp = 411,
    heightDp = 891,
)
@Composable
private fun InputCatalogPreview(
    @PreviewParameter(InputPreviewSpecProvider::class) specRef: PreviewSpecRef,
) {
    PreviewCatalogSpecScreen(specId = specRef.id)
}

@Preview(
    name = "Container",
    group = "ViewCompose/Catalog",
    widthDp = 411,
    heightDp = 891,
)
@Composable
private fun ContainerCatalogPreview(
    @PreviewParameter(ContainerPreviewSpecProvider::class) specRef: PreviewSpecRef,
) {
    PreviewCatalogSpecScreen(specId = specRef.id)
}

@Preview(
    name = "Collection",
    group = "ViewCompose/Catalog",
    widthDp = 411,
    heightDp = 891,
)
@Composable
private fun CollectionCatalogPreview(
    @PreviewParameter(CollectionPreviewSpecProvider::class) specRef: PreviewSpecRef,
) {
    PreviewCatalogSpecScreen(specId = specRef.id)
}

@Preview(
    name = "Navigation",
    group = "ViewCompose/Catalog",
    widthDp = 411,
    heightDp = 891,
)
@Composable
private fun NavigationCatalogPreview(
    @PreviewParameter(NavigationPreviewSpecProvider::class) specRef: PreviewSpecRef,
) {
    PreviewCatalogSpecScreen(specId = specRef.id)
}

@Preview(
    name = "Feedback",
    group = "ViewCompose/Catalog",
    widthDp = 411,
    heightDp = 891,
)
@Composable
private fun FeedbackCatalogPreview(
    @PreviewParameter(FeedbackPreviewSpecProvider::class) specRef: PreviewSpecRef,
) {
    PreviewCatalogSpecScreen(specId = specRef.id)
}

@Preview(
    name = "Modifier",
    group = "ViewCompose/Catalog",
    widthDp = 411,
    heightDp = 891,
)
@Composable
private fun ModifierCatalogPreview(
    @PreviewParameter(ModifierPreviewSpecProvider::class) specRef: PreviewSpecRef,
) {
    PreviewCatalogSpecScreen(specId = specRef.id)
}

@Preview(
    name = "Animation",
    group = "ViewCompose/Catalog",
    widthDp = 411,
    heightDp = 891,
)
@Composable
private fun AnimationCatalogPreview(
    @PreviewParameter(AnimationPreviewSpecProvider::class) specRef: PreviewSpecRef,
) {
    PreviewCatalogSpecScreen(specId = specRef.id)
}

@Preview(
    name = "Gesture",
    group = "ViewCompose/Catalog",
    widthDp = 411,
    heightDp = 891,
)
@Composable
private fun GestureCatalogPreview(
    @PreviewParameter(GesturePreviewSpecProvider::class) specRef: PreviewSpecRef,
) {
    PreviewCatalogSpecScreen(specId = specRef.id)
}

@Preview(
    name = "Graphics",
    group = "ViewCompose/Catalog",
    widthDp = 411,
    heightDp = 891,
)
@Composable
private fun GraphicsCatalogPreview(
    @PreviewParameter(GraphicsPreviewSpecProvider::class) specRef: PreviewSpecRef,
) {
    PreviewCatalogSpecScreen(specId = specRef.id)
}

/**
 * 渲染单个目录 spec 的共享 Preview 屏幕。
 * Shared Preview screen that renders one catalog spec.
 */
@Composable
internal fun PreviewCatalogSpecScreen(
    specId: String,
    themeMode: ViewComposePreviewTheme = ViewComposePreviewTheme.Light,
) {
    val spec = PreviewCatalog.require(specId)
    ViewComposePreviewSurface(themeMode = themeMode) {
        Column(
            spacing = 10.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Text(
                text = spec.title,
                style = UiTextStyle(fontSizeSp = 18.sp),
            )
            spec.content(this)
        }
    }
}
