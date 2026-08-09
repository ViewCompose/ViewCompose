package com.viewcompose.publishing.smoke.oneui.overlay

import androidx.activity.ComponentActivity
import com.viewcompose.android.setUiContent
import com.viewcompose.oneui7.OneUi7Button
import com.viewcompose.oneui7.OneUi7Theme
import com.viewcompose.oneui7.OneUi7ThemeDefaults
import com.viewcompose.overlay.oneui7.android.host.AndroidOverlayHost
import com.viewcompose.ui.foundation.Snackbar
import com.viewcompose.ui.foundation.UiIntegrationAttribution

/** Compiles the explicit One UI design and overlay assembly from published coordinates only. */
fun ComponentActivity.installOneUiOverlayContent() {
    val tokens = OneUi7ThemeDefaults.light()
    var integrations = emptyList<UiIntegrationAttribution>()
    setUiContent(
        overlayHostFactory = { root ->
            AndroidOverlayHost(root, tokens).also { host ->
                integrations = host.integrationAttribution
            }
        },
    ) {
        OneUi7Theme(tokens, integrations = integrations) {
            OneUi7Button(text = "Continue", onClick = {})
            Snackbar(
                visible = true,
                message = "One UI overlay",
                requestKey = "one-ui-overlay-smoke",
            )
        }
    }
}
