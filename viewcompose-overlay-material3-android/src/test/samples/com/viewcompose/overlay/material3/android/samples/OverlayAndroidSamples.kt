package com.viewcompose.overlay.material3.android.samples

import android.view.View
import com.viewcompose.overlay.material3.android.host.AndroidOverlayHost
import com.viewcompose.ui.foundation.OverlayHost

/** Creates the Android backend explicitly for a custom render host. */
fun androidOverlayHostSample(rootView: View): OverlayHost {
    return AndroidOverlayHost(rootView)
}
