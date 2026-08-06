package com.viewcompose.publishing.smoke.engine

import android.view.ViewGroup
import com.viewcompose.host.android.RenderSession
import com.viewcompose.host.android.renderInto
import com.viewcompose.ui.foundation.Text

/** Compiles the low-level Android engine path without the Material 3 design system. */
fun mountEngineOnlyContent(container: ViewGroup): RenderSession {
    return renderInto(container) {
        Text("Material-free engine")
    }
}
