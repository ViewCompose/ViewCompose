package com.viewcompose.overlay.android

import android.view.View
import com.viewcompose.host.android.overlay.AndroidOverlayHostFactoryProvider as AndroidOverlayHostFactoryContract
import com.viewcompose.ui.foundation.OverlayHost

/**
 * Creates the neutral Android overlay transport for low-level hosts using service discovery.
 *
 * Application Activity and Fragment integrations construct their overlay host explicitly. This
 * provider is retained for custom hosts that use `AndroidOverlayHostDefaults`; exactly one neutral
 * provider may be present on the runtime classpath.
 *
 * @sample com.viewcompose.overlay.android.samples.discoveredAndroidOverlayHostSample
 */
class AndroidOverlayHostFactoryProvider : AndroidOverlayHostFactoryContract {
    /**
     * Creates a root-scoped Material-free overlay host.
     *
     * @param rootView attached Android root that owns the overlay window lifetime
     * @return a new host with neutral presenters and explicit unsupported design-owned slots
     */
    override fun create(rootView: View): OverlayHost = AndroidOverlayHost(rootView)
}
