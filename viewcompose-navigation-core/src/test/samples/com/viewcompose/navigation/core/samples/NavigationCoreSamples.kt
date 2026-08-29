package com.viewcompose.navigation.core.samples

import com.viewcompose.navigation.core.NavBackStackController
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavDeepLink
import com.viewcompose.navigation.core.NavDeepLinkArgumentType
import com.viewcompose.navigation.core.NavDeepLinkRequest
import com.viewcompose.navigation.core.NavDeepLinkResolution
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryLifecycleState
import com.viewcompose.navigation.core.NavEntryPresence
import com.viewcompose.navigation.core.NavExecutionReducer
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.navigation.core.NavLifecyclePlanner
import com.viewcompose.navigation.core.NavPane
import com.viewcompose.navigation.core.NavPaneRole
import com.viewcompose.navigation.core.NavPaneScene
import com.viewcompose.navigation.core.NavPreparation
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavRouteSpec
import com.viewcompose.navigation.core.NavRootBackBehavior
import com.viewcompose.navigation.core.NavResultKey
import com.viewcompose.navigation.core.NavScene
import com.viewcompose.navigation.core.NavSceneEntry
import com.viewcompose.navigation.core.NavSceneInteraction
import com.viewcompose.navigation.core.NavSceneTransitionPhase
import com.viewcompose.navigation.core.NavSceneVisibility
import com.viewcompose.navigation.core.NavStackConfiguration
import com.viewcompose.navigation.core.NavStackId
import com.viewcompose.navigation.core.NavStackSpec
import com.viewcompose.navigation.core.NavValue
import com.viewcompose.navigation.core.navGraph
import com.viewcompose.navigation.core.toRoute

// DOCS_REGION_START(navigation-core-typed-route)
data class ProfileRoute(val userId: Long)

val ProfileDestination = NavRouteSpec(
    name = "profile",
    encodeArguments = { profile: ProfileRoute ->
        mapOf("userId" to NavValue.LongValue(profile.userId))
    },
    decodeArguments = { arguments ->
        ProfileRoute((arguments.getValue("userId") as NavValue.LongValue).value)
    },
)

fun typedRouteSample() {
    val graph = navGraph(
        route = "root",
        startDestination = NavRoute("home"),
    ) {
        destination("home")
        destination(ProfileDestination)
    }
    val route = ProfileDestination.encode(ProfileRoute(userId = 42L))
    val entry = NavEntry(NavEntryId("profile-42"), graph.resolve(route).destination)

    check(entry.toRoute(ProfileDestination).userId == 42L)
}
// DOCS_REGION_END(navigation-core-typed-route)

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
        destination(
            route = "shared-image",
            deepLinks = listOf(
                NavDeepLink(
                    action = "android.intent.action.SEND",
                    mimeType = "image/*",
                ),
            ),
        )
    }

    val matched = graph.resolveDeepLink("https://viewcompose.com/users/42")
        as NavDeepLinkResolution.Matched
    check(matched.match.route["userId"] == NavValue.LongValue(42L))
    check(matched.match.deepLink.targetStackId == NavStackId("account"))
    val shared = graph.resolveDeepLink(
        NavDeepLinkRequest(
            action = "android.intent.action.SEND",
            mimeType = "image/png",
        ),
    ) as NavDeepLinkResolution.Matched
    check(shared.match.route.name == "shared-image")
}

fun deepLinkDeclarationSample() {
    // DOCS_REGION_START(navigation-core-deep-link)
val graph = navGraph(
    route = "root",
    startDestination = NavRoute("home"),
) {
    destination("home")
    destination(
        route = "shared-image",
        deepLinks = listOf(
            NavDeepLink(
                action = "android.intent.action.SEND",
                mimeType = "image/*",
            ),
        ),
    )
}
val result = graph.resolveDeepLink(
    NavDeepLinkRequest(
        action = "android.intent.action.SEND",
        mimeType = "image/png",
    ),
)
check((result as NavDeepLinkResolution.Matched).match.route.name == "shared-image")
    // DOCS_REGION_END(navigation-core-deep-link)
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

fun navigationResultTransactionSample() {
    val controller = NavBackStackController.create(NavRoute("home"))
    val push = controller.prepare(NavCommand.Push(NavRoute("details"))) as NavPreparation.Ready
    push.transaction.commit()

    // DOCS_REGION_START(navigation-core-results)
val selectedItem = NavResultKey.text("catalog.selection")
val preparedResultPop = controller.prepare(
    NavCommand.PopWithResult(selectedItem.encode("item-42")),
)
check(preparedResultPop is NavPreparation.Ready)
    // DOCS_REGION_END(navigation-core-results)
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
    // DOCS_REGION_START(navigation-core-scene-projection)
    val list = NavEntry(NavEntryId("list"), NavRoute("list"))
    val detail = NavEntry(NavEntryId("detail"), NavRoute("detail"))
    val scene = NavScene(
        listOf(
            NavSceneEntry(
                entryId = list.id,
                presence = NavEntryPresence.Retained,
                visibility = NavSceneVisibility.Hidden,
                interaction = NavSceneInteraction.NonInteractive,
                transitionPhase = NavSceneTransitionPhase.Settled,
                paneRole = null,
            ),
            NavSceneEntry(
                entryId = detail.id,
                presence = NavEntryPresence.Retained,
                visibility = NavSceneVisibility.Visible,
                interaction = NavSceneInteraction.Interactive,
                transitionPhase = NavSceneTransitionPhase.Settled,
                paneRole = NavPaneRole.Primary,
            ),
        ),
    )
    val plan = NavLifecyclePlanner.plan(
        currentStates = mapOf(
            list.id to NavEntryLifecycleState.Resumed,
            detail.id to NavEntryLifecycleState.Created,
        ),
        entries = listOf(list, detail),
        scene = scene,
        hostState = NavHostLifecycleState.Resumed,
    )

    check(plan.targetStates[list.id] == NavEntryLifecycleState.Created)
    check(plan.targetStates[detail.id] == NavEntryLifecycleState.Resumed)
    check(plan.transitions.first().entryId == list.id)
    // DOCS_REGION_END(navigation-core-scene-projection)
}

fun navigationExecutionPlanSample() {
    val controller = NavBackStackController.create(NavRoute("home"))
    val before = controller.snapshot()
    val transaction = (
        controller.prepare(NavCommand.Push(NavRoute("details"))) as NavPreparation.Ready
    ).transaction
    // DOCS_REGION_START(navigation-core-execution-plan)
    val plan = NavExecutionReducer.transition(
        currentLifecycleStates = mapOf(
            before.top.id to NavEntryLifecycleState.Resumed,
        ),
        transaction = transaction,
        beforePaneScene = NavPaneScene(
            listOf(NavPane(NavPaneRole.Primary, before.top.id)),
        ),
        afterPaneScene = NavPaneScene(
            listOf(NavPane(NavPaneRole.Primary, transaction.after.top.id)),
        ),
        hostState = NavHostLifecycleState.Resumed,
        presentedEntryIds = listOf(before.top.id),
        maxRetainedHiddenPresentations = 0,
    )

    // A platform adapter prepares these identities before committing the stack.
    check(plan.preparePresentationEntryIds == listOf(transaction.after.top.id))
    check(plan.inputEntryIds.isEmpty())
    check(plan.rollbackOwnerEntryIds == listOf(transaction.after.top.id))
    check(plan.lifecycle.targetStates.values.none(NavEntryLifecycleState.Resumed::equals))
    // DOCS_REGION_END(navigation-core-execution-plan)
    transaction.rollback()
}
