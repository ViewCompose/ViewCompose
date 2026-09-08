package com.viewcompose.overlay.material3.android.samples

import android.view.View
import com.viewcompose.overlay.material3.android.host.AndroidOverlayHost
import com.viewcompose.ui.foundation.OverlayHost

/** Creates the Android backend explicitly for a custom render host. */
fun androidOverlayHostSample(rootView: View): OverlayHost {
    return AndroidOverlayHost(rootView)
}

/** Disposes an owned session while preserving every cleanup failure for the caller. */
fun clearOverlaySessionSample(
    host: com.viewcompose.ui.foundation.OverlayHost,
    session: com.viewcompose.ui.foundation.OverlaySessionId,
) {
    // The host attempts all owned delegates before this exception reaches the caller.
    host.clear(session)
    host.clear(session)
}
