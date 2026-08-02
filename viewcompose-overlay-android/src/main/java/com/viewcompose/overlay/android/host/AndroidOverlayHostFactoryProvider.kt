package com.viewcompose.overlay.android.host

import android.view.View
import com.viewcompose.widget.core.OverlayHost
import com.viewcompose.widget.core.OverlayHostFactoryProvider

/**
 * Service-provider entry that lets `viewcompose-widget-core` discover this Android backend.
 *
 * Applications normally do not instantiate this class. Adding the artifact to the runtime
 * classpath exposes it through `ServiceLoader`; the renderer then creates one [AndroidOverlayHost]
 * for each render root. Direct construction remains useful for custom hosts and tests.
 *
 * @sample com.viewcompose.overlay.android.samples.discoveredAndroidOverlayHostSample
 */
class AndroidOverlayHostFactoryProvider : OverlayHostFactoryProvider {
    /** Creates a host attached to the Android window that owns [rootView]. */
    override fun create(rootView: View): OverlayHost {
        return AndroidOverlayHost(rootView)
    }
}
