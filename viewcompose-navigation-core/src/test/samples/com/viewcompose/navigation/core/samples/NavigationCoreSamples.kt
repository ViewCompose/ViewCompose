package com.viewcompose.navigation.core.samples

import com.viewcompose.navigation.core.NavBackStackController
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavDeepLink
import com.viewcompose.navigation.core.NavDeepLinkArgumentType
import com.viewcompose.navigation.core.NavDeepLinkResolution
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryLifecycleState
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.navigation.core.NavLifecyclePlanner
import com.viewcompose.navigation.core.NavPreparation
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavStackConfiguration
import com.viewcompose.navigation.core.NavStackId
import com.viewcompose.navigation.core.NavStackSpec
import com.viewcompose.navigation.core.NavValue
import com.viewcompose.navigation.core.navGraph

fun navigationGraphSample() {
    val graph = navGraph(
        route = "root",
        startDestination = NavRoute("home"),
    ) {
        destination("home")
        navigation(
            route = "account",
            startDestination = NavRoute("profile"),
        ) {
            destination(
                route = "profile",
                deepLinks = listOf(
                    NavDeepLink(
                        uriPattern = "https://viewcompose.com/users/{userId}",
                        argumentTypes = mapOf("userId" to NavDeepLinkArgumentType.Long),
                    ),
                ),
            )
            destination("settings")
        }
    }

    val resolution = graph.resolve(
        NavRoute("account", mapOf("source" to NavValue.Text("notification"))),
    )
    check(resolution.destination.name == "profile")
    check(resolution.hierarchy == listOf("root", "account"))
}

fun deepLinkResolutionSample() {
    val graph = navGraph(
        route = "root",
        startDestination = NavRoute("home"),
    ) {
        destination("home")
        destination(
            route = "profile",
            deepLinks = listOf(
                NavDeepLink(
                    uriPattern = "https://viewcompose.com/users/{userId}",
                    argumentTypes = mapOf("userId" to NavDeepLinkArgumentType.Long),
                    targetStackId = NavStackId("account"),
                ),
            ),
        )
    }

    val matched = graph.resolveDeepLink("https://viewcompose.com/users/42")
        as NavDeepLinkResolution.Matched
    check(matched.match.route["userId"] == NavValue.LongValue(42L))
    check(matched.match.deepLink.targetStackId == NavStackId("account"))
}

fun transactionalNavigationSample() {
    val controller = NavBackStackController.create(
        configuration = NavStackConfiguration(
            initialStackId = NavStackId("home"),
            stacks = listOf(
                NavStackSpec(NavStackId("home"), NavRoute("home")),
                NavStackSpec(NavStackId("account"), NavRoute("profile")),
            ),
        ),
    )

    val preparation = controller.prepare(NavCommand.Push(NavRoute("details")))
    val transaction = (preparation as NavPreparation.Ready).transaction
    try {
        // Mount the prospective `transaction.after` snapshot in the platform host first.
        transaction.commit()
    } catch (failure: Throwable) {
        transaction.rollback()
        throw failure
    }

    check(controller.snapshot().top.route.name == "details")
}

fun lifecyclePlanningSample() {
    val list = NavEntryId("list")
    val detail = NavEntryId("detail")
    val plan = NavLifecyclePlanner.plan(
        currentStates = mapOf(
            list to NavEntryLifecycleState.Resumed,
            detail to NavEntryLifecycleState.Created,
        ),
        retainedEntryIds = listOf(list, detail),
        visibleEntryIds = setOf(list, detail),
        interactiveEntryId = detail,
        hostState = NavHostLifecycleState.Resumed,
    )

    check(plan.targetStates[list] == NavEntryLifecycleState.Started)
    check(plan.targetStates[detail] == NavEntryLifecycleState.Resumed)
    check(plan.transitions.first().entryId == list)
}
