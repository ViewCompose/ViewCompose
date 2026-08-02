package com.viewcompose.overlay.android.samples

import android.view.View
import com.viewcompose.overlay.android.host.AndroidOverlayHost
import com.viewcompose.widget.core.OverlayHost
import com.viewcompose.widget.core.OverlayHostDefaults

/** Creates the Android backend explicitly for a custom render host. */
fun androidOverlayHostSample(rootView: View): OverlayHost {
    return AndroidOverlayHost(rootView)
}

/** Lets widget core discover this artifact's service-provider entry. */
fun discoveredAndroidOverlayHostSample(rootView: View): OverlayHost {
    return OverlayHostDefaults.androidOrNoOp(rootView)
}
