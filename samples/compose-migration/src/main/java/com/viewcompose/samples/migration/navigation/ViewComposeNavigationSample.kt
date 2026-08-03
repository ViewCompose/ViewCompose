package com.viewcompose.samples.migration.navigation

import com.viewcompose.navigation.NavHost
import com.viewcompose.navigation.rememberNavHostController
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.UiTreeBuilder

// DOCS_REGION_START(viewcompose-navigation)
fun UiTreeBuilder.ViewComposeNavigationSample() {
    val controller = rememberNavHostController(
        startDestination = NavRoute("home"),
    )

    NavHost(controller = controller) { entry ->
        when (entry.route.name) {
            "home" -> Button(
                text = "Open details",
                onClick = {
                    controller.navigate(NavRoute("details"))
                },
            )
            "details" -> Text("Details")
            else -> error("Unknown route ${entry.route.name}")
        }
    }
}
// DOCS_REGION_END(viewcompose-navigation)
