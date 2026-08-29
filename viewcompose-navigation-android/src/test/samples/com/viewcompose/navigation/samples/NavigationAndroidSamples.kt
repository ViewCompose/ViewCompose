package com.viewcompose.navigation.samples

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import com.viewcompose.navigation.LocalNavDestinationContext
import com.viewcompose.navigation.NavDestinationMotionSpec
import com.viewcompose.navigation.NavDestinationTransform
import com.viewcompose.navigation.NavHost
import com.viewcompose.navigation.NavHostController
import com.viewcompose.navigation.NavMotionEasing
import com.viewcompose.navigation.NavMotionTiming
import com.viewcompose.navigation.NavPresentationRetentionPolicy
import com.viewcompose.navigation.NavResult
import com.viewcompose.navigation.NavTransitionSpec
import com.viewcompose.navigation.rememberNavHostController
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.runtime.MutableState
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.ui.foundation.UiThemeTokens
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.sharedBounds
import com.viewcompose.ui.shared.SharedContentKey
import com.viewcompose.viewmodel.ProvideViewModelStoreOwner
import com.viewcompose.viewmodel.viewModel

fun navHostControllerSample(controller: NavHostController) {
    when (val result = controller.navigate(NavRoute("details"))) {
        is NavResult.Committed -> check(result.snapshot.top.route.name == "details")
        is NavResult.NoChange -> Unit
        is NavResult.Queued -> Unit // Observe controller.navigationState for eventual completion.
        is NavResult.Failed -> throw result.failure.cause ?: IllegalStateException(result.failure.toString())
    }
}

fun UiTreeBuilder.rememberedNavHostSample() {
    val controller = rememberNavHostController(
        startDestination = NavRoute("home"),
    )
    NavHost(controller = controller) { entry ->
        when (entry.route.name) {
            "home" -> Text("Home")
            "details" -> Text("Details")
            else -> error("Unknown route ${entry.route.name}")
        }
    }
}

// DOCS_REGION_START(navigation-android-host)
fun UiTreeBuilder.AppNavigation() {
    val controller = rememberNavHostController(
        startDestination = NavRoute("home"),
    )
    NavHost(controller = controller) { entry ->
        when (entry.route.name) {
            "home" -> HomePage(controller)
            "details" -> DetailsPage(controller)
            else -> error("Unknown route ${entry.route.name}")
        }
    }
}
// DOCS_REGION_END(navigation-android-host)

// DOCS_REGION_START(navigation-android-presentation-retention)
fun UiTreeBuilder.BoundedPresentationNavigation(controller: NavHostController) {
    NavHost(
        controller = controller,
        presentationRetentionPolicy = NavPresentationRetentionPolicy.Bounded(
            maxHiddenPresentations = 2,
        ),
    ) { entry ->
        Text(entry.route.name)
    }
}
// DOCS_REGION_END(navigation-android-presentation-retention)

// DOCS_REGION_START(navigation-android-destination-context)
fun UiTreeBuilder.destinationContextSample(controller: NavHostController) {
    NavHost(controller = controller) { entry ->
        val presentation = checkNotNull(LocalNavDestinationContext.current).presentation.value
        Text("${entry.route.name}: ${presentation.visibility}, ${presentation.paneRole}")
    }
}
// DOCS_REGION_END(navigation-android-destination-context)

private fun UiTreeBuilder.HomePage(controller: NavHostController) {
    Text("Home: ${controller.snapshot.top.route.name}")
}

private fun UiTreeBuilder.DetailsPage(controller: NavHostController) {
    Text("Details: ${controller.snapshot.top.route.name}")
}

/** Pairs one typed visual endpoint across destination sessions owned by the same NavHost. */
fun UiTreeBuilder.sharedNavigationContentSample(controller: NavHostController) {
    val titleKey = SharedContentKey("article-title")
    NavHost(controller = controller) { entry ->
        Text(
            text = entry.route.name,
            modifier = Modifier.sharedBounds(titleKey),
        )
    }
}

fun UiTreeBuilder.inheritedNavViewModelFactorySample(
    controller: NavHostController,
    parentOwner: ViewModelStoreOwner,
) {
    ProvideViewModelStoreOwner(parentOwner) {
        NavHost(controller = controller) { entry ->
            // A HasDefaultViewModelProviderFactory parent can create this destination-scoped model.
            val model = viewModel<InheritedNavViewModel>()
            Text("${entry.route.name}: ${model.label}")
        }
    }
}

class InheritedNavViewModel(
    val label: String = "ready",
) : ViewModel()

fun UiTreeBuilder.retainedDestinationThemeSample(
    controller: NavHostController,
    applicationTheme: MutableState<UiThemeTokens>,
) {
    UiTheme(tokens = applicationTheme.value) {
        NavHost(controller = controller) { entry ->
            Text("${entry.route.name}: dark=${Theme.current.metadata.isDark}")
        }
    }
}

fun navigationMotionSample() {
    val fadeThrough = NavDestinationMotionSpec(
        durationMillis = 220L,
        incomingStart = NavDestinationTransform(alpha = 0f, scale = 0.96f),
        outgoingEnd = NavDestinationTransform(alpha = 0f, scale = 1.02f),
        easing = NavMotionEasing.Standard,
        incomingAlphaTiming = NavMotionTiming(
            durationMillis = 140L,
            startDelayMillis = 80L,
        ),
        outgoingAlphaTiming = NavMotionTiming(durationMillis = 80L),
    )
    val policy = NavTransitionSpec(
        replace = fadeThrough,
        reset = fadeThrough,
        stackSelection = fadeThrough,
    )
    check(policy.replace === fadeThrough)
}
