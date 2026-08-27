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
import com.viewcompose.navigation.core.NavRootBackBehavior
import com.viewcompose.navigation.core.NavStackConfiguration
import com.viewcompose.navigation.core.NavStackId
import com.viewcompose.navigation.core.NavStackSpec
import com.viewcompose.navigation.core.NavValue
import com.viewcompose.navigation.core.navGraph

fun navigationGraphSample() {
    // DOCS_REGION_START(navigation-core-graph)
val graph = navGraph(
    route = "root",
    startDestination = NavRoute("home"),
) {
    destination("home")
    navigation(
        route = "account",
        startDestination = NavRoute("profile"),
    ) {
        destination("profile")
        destination("settings")
    }
}
    // DOCS_REGION_END(navigation-core-graph)

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

fun deepLinkDeclarationSample() {
    // DOCS_REGION_START(navigation-core-deep-link)
val profileLink = NavDeepLink(
    uriPattern = "https://viewcompose.com/users/{userId}",
    argumentTypes = mapOf("userId" to NavDeepLinkArgumentType.Long),
    targetStackId = NavStackId("account"),
)
    // DOCS_REGION_END(navigation-core-deep-link)
    check(profileLink.targetStackId == NavStackId("account"))
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

fun documentedTransactionSample() {
    val controller = NavBackStackController.create(NavRoute("home"))
    // DOCS_REGION_START(navigation-core-transaction)
when (val preparation = controller.prepare(NavCommand.Push(NavRoute("details")))) {
    is NavPreparation.NoChange -> Unit
    is NavPreparation.Ready -> preparation.transaction.use { transaction ->
        // First mount transaction.after and apply owner lifecycle changes.
        transaction.commit()
    }
}
    // DOCS_REGION_END(navigation-core-transaction)
}

fun retainedStacksSample() {
    val graph = navGraph(
        route = "root",
        startDestination = NavRoute("home"),
    ) {
        destination("home")
        destination("profile")
    }
    // DOCS_REGION_START(navigation-core-stacks)
val configuration = NavStackConfiguration(
    initialStackId = NavStackId("home"),
    stacks = listOf(
        NavStackSpec(NavStackId("home"), NavRoute("home")),
        NavStackSpec(NavStackId("account"), NavRoute("profile")),
    ),
    rootBackBehavior = NavRootBackBehavior.PreviousStack,
)
val controller = NavBackStackController.create(configuration, graph)
    // DOCS_REGION_END(navigation-core-stacks)
    check(controller.stackStateSnapshot().activeStackId == NavStackId("home"))
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
