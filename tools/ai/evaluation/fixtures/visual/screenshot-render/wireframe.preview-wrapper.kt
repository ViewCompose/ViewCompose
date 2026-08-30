package generated.viewcompose

import com.viewcompose.preview.tooling.PreviewLayoutDirection
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.text.TextFieldState
import com.viewcompose.ui.foundation.UiTreeBuilder

@ViewComposePreview(
    name = "Generated Screenshot · ScreenshotWireframeView",
    group = "AI/Screenshot",
    widthDp = 411,
    heightDp = -1,
    density = 2.625f,
    fontScale = 1.0f,
    localeTag = "en-US",
    layoutDirection = PreviewLayoutDirection.Ltr,
    theme = PreviewTheme.Light,
    apiLevel = -1,
)
fun UiTreeBuilder.GeneratedScreenshotPreview() {
    ScreenshotWireframeView(
        emailState = TextFieldState(),
        onEmailSubmit = { _ -> false },
        onContinue = { },
    )
}
