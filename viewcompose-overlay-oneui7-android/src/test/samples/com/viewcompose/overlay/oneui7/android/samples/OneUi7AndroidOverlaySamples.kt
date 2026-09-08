package com.viewcompose.overlay.oneui7.android.samples

import android.view.View
import com.viewcompose.oneui7.OneUi7ThemeDefaults
import com.viewcompose.overlay.oneui7.android.host.AndroidOverlayHost
import com.viewcompose.ui.foundation.OverlayHost

/** Creates one root-scoped One UI overlay adapter without selecting a different content host API. */
fun oneUi7AndroidOverlayHostSample(rootView: View): OverlayHost =
    AndroidOverlayHost(
        rootView = rootView,
        tokens = OneUi7ThemeDefaults.light(),
    )

/** Disposes an owned session while preserving every cleanup failure for the caller. */
fun clearOverlaySessionSample(
    host: com.viewcompose.ui.foundation.OverlayHost,
    session: com.viewcompose.ui.foundation.OverlaySessionId,
) {
    // The host attempts all owned delegates before this exception reaches the caller.
    host.clear(session)
    host.clear(session)
}
