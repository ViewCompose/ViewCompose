package com.viewcompose.overlay.material3.android.host

import android.view.View
import com.viewcompose.ui.foundation.OverlayHost
import com.viewcompose.host.android.overlay.AndroidOverlayHostFactoryProvider as AndroidOverlayHostFactoryContract

/**
 * Service-provider entry that lets `viewcompose-ui-foundation` discover this Android backend.
 *
 * Applications normally do not instantiate this class. Adding the artifact to the runtime
 * classpath exposes it through `ServiceLoader`; the renderer then creates one [AndroidOverlayHost]
 * for each render root. Direct construction remains useful for custom hosts and tests.
 *
 * @sample com.viewcompose.overlay.material3.android.samples.discoveredAndroidOverlayHostSample
 */
class AndroidOverlayHostFactoryProvider : AndroidOverlayHostFactoryContract {
    /** Creates a host attached to the Android window that owns [rootView]. */
    override fun create(rootView: View): OverlayHost {
        return AndroidOverlayHost(rootView)
    }
}
