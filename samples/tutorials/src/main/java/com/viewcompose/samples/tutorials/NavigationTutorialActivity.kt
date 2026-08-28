// DOCS_REGION_START(navigation)
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.navigation.NavHost
import com.viewcompose.navigation.rememberNavHostController
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.viewmodel.viewModel

private const val HOME = "home"
private const val DETAILS = "details"

class NavigationTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
            val controller = rememberNavHostController(NavRoute(HOME))

            NavHost(controller = controller) { entry ->
                Column(
                    spacing = 12.dp,
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                ) {
                    when (entry.route.name) {
                        HOME -> {
                            Text("Home", style = TextDefaults.titleLargeStyle())
                            Button(
                                "Open details",
                                onClick = { controller.navigate(NavRoute(DETAILS)) },
                            )
                        }
                        DETAILS -> {
                            val model = viewModel<DetailsViewModel>()
                            Text(model.title, style = TextDefaults.titleLargeStyle())
                            Button("Back", onClick = controller::popBackStack)
                        }
                        else -> error("Unknown route ${entry.route.name}")
                    }
                }
            }
        }
    }
}

class DetailsViewModel : ViewModel() {
    val title: String = "Details"
}
// DOCS_REGION_END(navigation)
