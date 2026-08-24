package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Dedicated host for the optional credentialed Google Maps fixture. */
class GoogleMapsActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_google_maps_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.GoogleMapsDemoPage(
            scenario = checkNotNull(currentScenario()) {
                "GoogleMapsActivity requires the registered Google Maps scenario"
            },
            credentialsConfigured = BuildConfig.VIEWCOMPOSE_MAPS_CONFIGURED,
        )
    }
}
