package generated.viewcompose

import com.viewcompose.preview.tooling.PreviewLayoutDirection
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.text.TextFieldState
import com.viewcompose.ui.foundation.UiTreeBuilder

@ViewComposePreview(
    name = "Generated XML · LoginView",
    group = "AI/XML",
    widthDp = 411,
    heightDp = -1,
    density = 2.625f,
    fontScale = 1.0f,
    localeTag = "en-US",
    layoutDirection = PreviewLayoutDirection.Ltr,
    theme = PreviewTheme.Light,
    apiLevel = -1,
)
fun UiTreeBuilder.GeneratedXmlPreview() {
    LoginView(
        loginTitle = "Sign in to ViewCompose",
        emailHint = "name@example.com",
        loginAction = "Sign in",
        emailState = TextFieldState(),
    )
}
