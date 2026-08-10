package com.viewcompose.ui.foundation.samples

import com.viewcompose.ui.foundation.DesignSystemAttributionProvider
import com.viewcompose.ui.foundation.DesignSystemDiagnostics
import com.viewcompose.ui.foundation.UiComponentAttribution
import com.viewcompose.ui.foundation.UiComponentBackend
import com.viewcompose.ui.foundation.UiDesignConformance
import com.viewcompose.ui.foundation.UiDesignSystemAttribution
import com.viewcompose.ui.foundation.UiIntegrationAttribution
import com.viewcompose.ui.foundation.buildVNodeTree

fun designSystemAttributionSample() {
    val attribution = UiDesignSystemAttribution(
        designSystemId = "product-design",
        recipeSetId = "product-pressure-v1",
        components = listOf(
            UiComponentAttribution(
                familyId = "button",
                recipeId = "product-filled-button-v1",
                backend = UiComponentBackend.DslComposite,
                conformance = UiDesignConformance.Exact,
                capabilityPath = "basic-button",
            ),
        ),
        integrations = listOf(
            UiIntegrationAttribution(
                capabilityId = "overlay.snackbar",
                transportId = "product-overlay-transport",
                presenterId = "product-snackbar-v1",
                conformance = UiDesignConformance.Exact,
            ),
        ),
    )
    var observed: UiDesignSystemAttribution? = null

    buildVNodeTree {
        DesignSystemAttributionProvider(attribution) {
            observed = DesignSystemDiagnostics.current
        }
    }

    check(observed?.component("button")?.recipeId == "product-filled-button-v1")
    check(observed?.integration("overlay.snackbar")?.presenterId == "product-snackbar-v1")
}
