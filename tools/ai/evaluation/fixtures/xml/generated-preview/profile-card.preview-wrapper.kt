package generated.viewcompose

import com.viewcompose.ai.preview.harness.R
import com.viewcompose.preview.tooling.PreviewLayoutDirection
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.node.ImageSource

@ViewComposePreview(
    name = "Generated XML · ProfileCardView",
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
    ProfileCardView(
        profileAvatar = ImageSource.Resource(R.drawable.vc_ai_4ff6ab670a58c14270e034e2090d9a432caa263a14e0a25785386b0c12f880b5),
        profilePhoto = "Profile photo",
        statusLabel = "Available",
    )
}
