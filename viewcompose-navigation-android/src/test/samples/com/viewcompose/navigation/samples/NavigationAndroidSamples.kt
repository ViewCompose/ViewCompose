package com.viewcompose.navigation.samples

import com.viewcompose.navigation.NavDestinationMotionSpec
import com.viewcompose.navigation.NavDestinationTransform
import com.viewcompose.navigation.NavHost
import com.viewcompose.navigation.NavHostController
import com.viewcompose.navigation.NavMotionEasing
import com.viewcompose.navigation.NavMotionTiming
import com.viewcompose.navigation.NavResult
import com.viewcompose.navigation.NavTransitionSpec
import com.viewcompose.navigation.rememberNavHostController
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.ui.foundation.UiThemeTokens
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.runtime.MutableState

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
