package com.viewcompose.host.android

import com.viewcompose.host.android.runtime.AndroidRenderSessionInspectionToolingRegistry
import com.viewcompose.ui.foundation.RenderSessionInspectionTooling

/**
 * Q3 installs one process-wide optional render-session inspection implementation from a
 * downstream tooling artifact.
 *
 * Tooling artifacts must call this during application-component initialization, before the first
 * ViewCompose render session reads the port. The call is synchronized, performs no file or service
 * discovery, and retains the implementation for the process lifetime. Reinstalling the same
 * instance is idempotent. Installing distinct instances before first use disables the port;
 * installing after first use is ignored. Either failure mode remains a rendering no-op.
 *
 * Applications and ordinary custom hosts do not call this integration hook. Concrete tooling must
 * still enforce its artifact-presence, debuggable-process, and explicit-request gates.
 *
 * @sample com.viewcompose.host.android.samples.installRenderSessionInspectionToolingSample
 */
fun installRenderSessionInspectionTooling(tooling: RenderSessionInspectionTooling) {
    AndroidRenderSessionInspectionToolingRegistry.install(tooling)
}
