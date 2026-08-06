package com.viewcompose.overlay.material3.android.samples

import android.view.View
import com.viewcompose.overlay.material3.android.host.AndroidOverlayHost
import com.viewcompose.ui.foundation.OverlayHost
import com.viewcompose.host.android.overlay.AndroidOverlayHostDefaults

/** Creates the Android backend explicitly for a custom render host. */
fun androidOverlayHostSample(rootView: View): OverlayHost {
    return AndroidOverlayHost(rootView)
}

/** Lets widget core discover this artifact's service-provider entry. */
fun discoveredAndroidOverlayHostSample(rootView: View): OverlayHost {
    return AndroidOverlayHostDefaults.androidOrNoOp(rootView)
}
