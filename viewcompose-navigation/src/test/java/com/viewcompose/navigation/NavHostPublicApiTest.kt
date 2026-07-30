package com.viewcompose.navigation

/*
 * 测试职责：覆盖 Android navigation runtime 中的 Nav Host Public Api 行为，防止导航契约在后续重构中回退。
 * Test responsibility: covers Nav Host Public Api behavior in Android navigation runtime and guards navigation contracts against regressions.
 */

import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.viewcompose.host.android.renderInto
import com.viewcompose.lifecycle.LocalLifecycleOwner
import com.viewcompose.lifecycle.ProvideLifecycleOwner
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryIdFactory
import com.viewcompose.navigation.core.NavDeepLink
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavStackConfiguration
import com.viewcompose.navigation.core.NavStackId
import com.viewcompose.navigation.core.NavStackSelectionMode
import com.viewcompose.navigation.core.NavStackSpec
import com.viewcompose.navigation.core.NavValue
import com.viewcompose.navigation.core.navGraph
import com.viewcompose.viewmodel.savedStateHandle
import com.viewcompose.viewmodel.viewModel
import com.viewcompose.widget.core.OverlayHostDefaults
import com.viewcompose.widget.core.ProvideSaveableStateRegistry
import com.viewcompose.widget.core.SaveableStateRegistry
import com.viewcompose.widget.core.Saver
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.createSaveableStateRegistry
import com.viewcompose.widget.core.rememberSaveable
import java.io.Serializable
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NavHostPublicApiTest {
    @Test
    fun `public host enters nested graph through the existing transaction and lifecycle boundary`() {
        val graph = navGraph(
            route = "app",
            startDestination = NavRoute("home"),
        ) {
            destination("home")
            navigation(
                route = "account",
                startDestination = NavRoute("profile"),
            ) {
                destination("profile")
                destination("security")
            }
        }
        val entryIds = ArrayDeque(listOf("home", "profile"))
        val controller = createNavHostController(
            graph = graph,
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(entryIds.removeFirst())
            },
        )
        val owners = mutableMapOf<String, LifecycleOwner>()
        val fixture = renderPublicHost(controller = controller) { entry ->
            owners[entry.route.name] = checkNotNull(LocalLifecycleOwner.current)
            Text(entry.route.name)
        }

        val result = controller.navigate(
            NavRoute(
                name = "account",
                arguments = mapOf(
                    "userId" to NavValue.LongValue(42L),
                ),
            ),
        )

        assertTrue(result is NavResult.Committed)
        assertEquals(listOf("home", "profile"), controller.routeNames())
        assertEquals(listOf("app", "account"), controller.snapshot.top.graphHierarchy)
        assertEquals(NavValue.LongValue(42L), controller.snapshot.top.route["userId"])
        assertEquals(Lifecycle.State.CREATED, checkNotNull(owners["home"]).lifecycle.currentState)
        assertEquals(Lifecycle.State.RESUMED, checkNotNull(owners["profile"]).lifecycle.currentState)
        assertEquals(2, fixture.navHostView.childCount)

        fixture.session.dispose()
    }

    @Test
    fun `public host shares graph ViewModels and releases them when graph leaves the stack`() {
        val graph = navGraph(
            route = "app",
            startDestination = NavRoute("home"),
        ) {
            destination("home")
            navigation(
                route = "account",
                startDestination = NavRoute("profile"),
            ) {
                destination("profile")
                destination("security")
            }
        }
        val entryIds = ArrayDeque(listOf("home", "profile", "security", "home-reset"))
        val controller = createNavHostController(
            graph = graph,
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(entryIds.removeFirst())
            },
        )
        val accountOwners = mutableMapOf<String, NavGraphOwner>()
        val accountViewModels = mutableMapOf<String, ReleaseTrackingViewModel>()
        val accountHandles = mutableMapOf<String, SavedStateHandle>()
        val fixture = renderPublicHost(controller = controller) { entry ->
            val accountOwner = LocalNavGraphOwnerScope.current?.get("account")
            if (accountOwner != null) {
                accountOwners[entry.route.name] = accountOwner
                ProvideNavGraphOwner("account") {
                    accountViewModels[entry.route.name] = viewModel(
                        key = "account-vm",
                        factory = ReleaseTrackingViewModelFactory,
                    )
                    accountHandles[entry.route.name] = savedStateHandle("account-handle")
                    Text(entry.route.name)
                }
            } else {
                Text(entry.route.name)
            }
        }

        controller.navigate(
            NavRoute(
                name = "account",
                arguments = mapOf(
                    "userId" to NavValue.LongValue(42L),
                ),
            ),
        )
        val profileOwner = checkNotNull(accountOwners["profile"])
        val profileViewModel = checkNotNull(accountViewModels["profile"])
        checkNotNull(accountHandles["profile"])["selection"] = 7
        controller.navigate(NavRoute("security"))

        assertSame(profileOwner, accountOwners["security"])
        assertSame(profileViewModel, accountViewModels["security"])
        assertEquals(42L, checkNotNull(accountHandles["security"])["userId"])
        assertEquals(7, checkNotNull(accountHandles["security"])["selection"])
        assertEquals(Lifecycle.State.RESUMED, profileOwner.lifecycle.currentState)

        controller.reset(NavRoute("home"))

        assertEquals(Lifecycle.State.DESTROYED, profileOwner.lifecycle.currentState)
        assertTrue(profileViewModel.cleared)
        fixture.session.dispose()
    }

    @Test
    fun `graph SavedStateHandle survives complete host and controller recreation`() {
        val graph = navGraph(
            route = "app",
            startDestination = NavRoute("home"),
        ) {
            destination("home")
            navigation(
                route = "account",
                startDestination = NavRoute("profile"),
            ) {
                destination("profile")
            }
        }
        val entryIds = ArrayDeque(listOf("home", "profile"))
        val controller = createNavHostController(
            graph = graph,
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(entryIds.removeFirst())
            },
        )
        var firstHandle: SavedStateHandle? = null
        val first = renderPublicHost(controller = controller) { entry ->
            if (LocalNavGraphOwnerScope.current?.get("account") != null) {
                ProvideNavGraphOwner("account") {
                    firstHandle = savedStateHandle("account-handle")
                    Text(entry.route.name)
                }
            } else {
                Text(entry.route.name)
            }
        }
        controller.navigate(
            NavRoute(
                name = "account",
                arguments = mapOf(
                    "userId" to NavValue.LongValue(42L),
                ),
            ),
        )
        checkNotNull(firstHandle)["selection"] = 7
        val expectedGraphEntry = controller.snapshot.top.graphEntries.last()
        val encoded = encodeNavHostState(controller.stateForSave())
        first.session.dispose()

        val restoredController = checkNotNull(navHostControllerSaver(graph).restore(encoded))
        var restoredHandle: SavedStateHandle? = null
        val restored = renderPublicHost(controller = restoredController) { entry ->
            if (LocalNavGraphOwnerScope.current?.get("account") != null) {
                ProvideNavGraphOwner("account") {
                    restoredHandle = savedStateHandle("account-handle")
                    Text(entry.route.name)
                }
            } else {
                Text(entry.route.name)
            }
        }

        assertEquals(expectedGraphEntry, restoredController.snapshot.top.graphEntries.last())
        assertEquals(42L, checkNotNull(restoredHandle)["userId"])
        assertEquals(7, checkNotNull(restoredHandle)["selection"])
        restored.session.dispose()
    }

    @Test
    fun `public host mounts stack and controller executes transactional navigation`() {
        val fixture = renderPublicHost()

        assertTrue(fixture.controller.isAttached)
        assertEquals(listOf("home"), fixture.controller.routeNames())
        assertEquals(1, fixture.navHostView.childCount)

        val pushed = fixture.controller.navigate(NavRoute("details"))

        assertTrue(pushed is NavResult.Committed)
        assertEquals(listOf("home", "details"), fixture.controller.routeNames())
        assertEquals(2, fixture.navHostView.childCount)

        val popped = fixture.controller.popBackStack()

        assertTrue(popped is NavResult.Committed)
        assertEquals(listOf("home"), fixture.controller.routeNames())
        assertEquals(1, fixture.navHostView.childCount)

        fixture.session.dispose()

        assertFalse(fixture.controller.isAttached)
        assertEquals(0, fixture.navHostView.childCount)
    }

    @Test
    fun `public host retains independent tab stacks and owners`() {
        val homeStack = NavStackId("home-tab")
        val searchStack = NavStackId("search-tab")
        val entryIds = ArrayDeque(
            listOf(
                "home-root",
                "search-root",
                "home-details",
                "search-result",
            ),
        )
        val controller = createNavHostController(
            stackConfiguration = NavStackConfiguration(
                initialStackId = homeStack,
                stacks = listOf(
                    NavStackSpec(homeStack, NavRoute("home")),
                    NavStackSpec(searchStack, NavRoute("search")),
                ),
            ),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(entryIds.removeFirst())
            },
        )
        val owners = mutableMapOf<NavEntryId, LifecycleOwner>()
        val fixture = renderPublicHost(controller = controller) { entry ->
            owners[entry.id] = checkNotNull(LocalLifecycleOwner.current)
            Text(entry.route.name)
        }
        val homeRoot = controller.stackSnapshot(homeStack).top
        val searchRoot = controller.stackSnapshot(searchStack).top

        assertEquals(2, fixture.navHostView.childCount)
        assertEquals(Lifecycle.State.RESUMED, checkNotNull(owners[homeRoot.id]).lifecycle.currentState)
        assertEquals(Lifecycle.State.CREATED, checkNotNull(owners[searchRoot.id]).lifecycle.currentState)

        controller.navigate(NavRoute("home-details"))
        val homeDetails = controller.snapshot.top
        val homeDetailsOwner = checkNotNull(owners[homeDetails.id])
        val selectedSearch = controller.selectStack(searchStack)
        assertEquals(searchStack, selectedSearch.stackState.activeStackId)
        assertEquals(
            listOf("home", "home-details"),
            checkNotNull(selectedSearch.stackState[homeStack])
                .entries
                .map { entry -> entry.route.name },
        )
        controller.navigate(NavRoute("search-result"))
        val searchResult = controller.snapshot.top
        controller.selectStack(homeStack)

        assertEquals(homeStack, controller.activeStackId)
        assertSame(homeDetails, controller.snapshot.top)
        assertEquals(
            listOf("search", "search-result"),
            controller.stackSnapshot(searchStack).entries.map { entry -> entry.route.name },
        )
        assertEquals(Lifecycle.State.RESUMED, homeDetailsOwner.lifecycle.currentState)
        assertEquals(
            Lifecycle.State.CREATED,
            checkNotNull(owners[searchResult.id]).lifecycle.currentState,
        )

        controller.selectStack(
            stackId = homeStack,
            selectionMode = NavStackSelectionMode.PopToRoot,
        )

        assertEquals(listOf("home"), controller.snapshot.entries.map { entry -> entry.route.name })
        assertEquals(Lifecycle.State.DESTROYED, homeDetailsOwner.lifecycle.currentState)
        assertEquals(
            listOf("search", "search-result"),
            controller.stackSnapshot(searchStack).entries.map { entry -> entry.route.name },
        )
        fixture.session.dispose()
    }

    @Test
    fun `native deep link selects its stack and rolls back both changes on render failure`() {
        val homeStack = NavStackId("home-tab")
        val accountStack = NavStackId("account-tab")
        val entryIds = ArrayDeque(
            listOf(
                "home-root",
                "account-root",
                "broken-security",
            ),
        )
        val graph = navGraph(
            route = "app",
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
                            uriPattern = "viewcompose://account/profile",
                            targetStackId = accountStack,
                        ),
                    ),
                )
                destination(
                    route = "security",
                    deepLinks = listOf(
                        NavDeepLink(
                            uriPattern = "viewcompose://account/security",
                            targetStackId = accountStack,
                        ),
                    ),
                )
            }
        }
        val controller = createNavHostController(
            stackConfiguration = NavStackConfiguration(
                initialStackId = homeStack,
                stacks = listOf(
                    NavStackSpec(homeStack, NavRoute("home")),
                    NavStackSpec(accountStack, NavRoute("account")),
                ),
            ),
            graph = graph,
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(entryIds.removeFirst())
            },
        )
        val fixture = renderPublicHost(controller = controller) { entry ->
            if (entry.route.name == "security") {
                error("broken security destination")
            }
            Text(entry.route.name)
        }

        assertSame(
            NavDeepLinkResult.NoMatch,
            controller.navigateDeepLink(Intent(Intent.ACTION_SEND)),
        )

        val selected = controller.navigateDeepLink(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("viewcompose://account/profile"),
            ),
        ) as NavDeepLinkResult.Navigated

        assertTrue(selected.navigationResult is NavResult.Committed)
        assertEquals(accountStack, selected.navigationResult.stackState.activeStackId)
        assertEquals(accountStack, controller.activeStackId)
        controller.selectStack(homeStack)
        val beforeFailure = controller.stackState

        val failed = controller.navigateDeepLink(
            Uri.parse("viewcompose://account/security"),
        ) as NavDeepLinkResult.Navigated

        assertTrue(failed.navigationResult is NavResult.Failed)
        assertFalse((failed.navigationResult as NavResult.Failed).failure.stackCommitted)
        assertSame(beforeFailure, controller.stackState)
        assertEquals(homeStack, controller.activeStackId)
        assertEquals(
            listOf("profile"),
            controller.stackSnapshot(accountStack).entries.map { entry -> entry.route.name },
        )
        assertEquals(2, fixture.navHostView.childCount)
        fixture.session.dispose()
    }

    @Test
    fun `destination lifecycle follows public host lifecycle and destroys on owner death`() {
        var destinationOwner: LifecycleOwner? = null
        val fixture = renderPublicHost { entry ->
            destinationOwner = LocalLifecycleOwner.current
            Text(entry.route.name)
        }
        val owner = checkNotNull(destinationOwner)

        assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)

        fixture.lifecycleOwner.moveTo(Lifecycle.State.STARTED)
        assertEquals(Lifecycle.State.STARTED, owner.lifecycle.currentState)

        fixture.lifecycleOwner.moveTo(Lifecycle.State.CREATED)
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)

        fixture.lifecycleOwner.moveTo(Lifecycle.State.DESTROYED)

        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        assertFalse(fixture.controller.isAttached)
        assertEquals(0, fixture.navHostView.childCount)
        fixture.session.dispose()
    }

    @Test
    fun `pop and host disposal release page resources while retaining committed saveable state`() {
        val owners = mutableMapOf<String, NavEntryOwner>()
        val viewModels = mutableMapOf<String, ReleaseTrackingViewModel>()
        val counters = mutableMapOf<String, RestorableCounter>()
        val handles = mutableMapOf<String, SavedStateHandle>()
        val content: com.viewcompose.widget.core.UiTreeBuilder.(NavEntry) -> Unit = { entry ->
            val routeName = entry.route.name
            owners[routeName] = LocalLifecycleOwner.current as NavEntryOwner
            viewModels[routeName] = viewModel(
                key = "release-vm",
                factory = ReleaseTrackingViewModelFactory,
            )
            counters[routeName] = rememberSaveable(
                key = "release-counter",
                saver = RestorableCounterSaver,
            ) {
                RestorableCounter(-1)
            }
            handles[routeName] = savedStateHandle(key = "release-handle")
            Text(routeName)
        }
        val controller = deterministicController()
        val first = renderPublicHost(
            controller = controller,
            content = content,
        )
        val originalHomeOwner = checkNotNull(owners["home"])
        val originalHomeViewModel = checkNotNull(viewModels["home"])
        val originalHomeContainer = first.navHostView.getChildAt(0)
        checkNotNull(counters["home"]).value = 7
        checkNotNull(handles["home"])["token"] = "home-state"

        controller.navigate(NavRoute("details"))
        val removedDetailsOwner = checkNotNull(owners["details"])
        val removedDetailsViewModel = checkNotNull(viewModels["details"])
        val removedDetailsContainer = first.navHostView.getChildAt(1)
        checkNotNull(counters["details"]).value = 41
        checkNotNull(handles["details"])["token"] = "removed-state"

        controller.popBackStack()

        assertEquals(Lifecycle.State.DESTROYED, removedDetailsOwner.lifecycle.currentState)
        assertTrue(removedDetailsViewModel.cleared)
        assertNull(removedDetailsContainer.parent)
        assertEquals(Lifecycle.State.RESUMED, originalHomeOwner.lifecycle.currentState)
        assertFalse(originalHomeViewModel.cleared)

        controller.navigate(NavRoute("details"))
        val replacementDetailsOwner = checkNotNull(owners["details"])
        val replacementDetailsViewModel = checkNotNull(viewModels["details"])

        assertNotSame(removedDetailsOwner, replacementDetailsOwner)
        assertNotSame(removedDetailsViewModel, replacementDetailsViewModel)
        assertEquals(-1, checkNotNull(counters["details"]).value)
        assertNull(checkNotNull(handles["details"]).get<String>("token"))

        controller.popBackStack()
        first.session.dispose()

        assertEquals(Lifecycle.State.DESTROYED, originalHomeOwner.lifecycle.currentState)
        assertTrue(originalHomeViewModel.cleared)
        assertNull(originalHomeContainer.parent)
        assertFalse(controller.isAttached)
        assertEquals(0, first.navHostView.childCount)

        val second = renderPublicHost(
            controller = controller,
            content = content,
        )
        val restoredHomeOwner = checkNotNull(owners["home"])
        val restoredHomeViewModel = checkNotNull(viewModels["home"])

        assertNotSame(originalHomeOwner, restoredHomeOwner)
        assertNotSame(originalHomeViewModel, restoredHomeViewModel)
        assertEquals(7, checkNotNull(counters["home"]).value)
        assertEquals("home-state", checkNotNull(handles["home"])["token"])
        assertEquals(Lifecycle.State.RESUMED, restoredHomeOwner.lifecycle.currentState)
        assertFalse(restoredHomeViewModel.cleared)

        second.session.dispose()

        assertEquals(Lifecycle.State.DESTROYED, restoredHomeOwner.lifecycle.currentState)
        assertTrue(restoredHomeViewModel.cleared)
        assertFalse(controller.isAttached)
        assertEquals(0, second.navHostView.childCount)
    }

    @Test
    fun `public host can attach during activity onCreate lifecycle state`() {
        val application = RuntimeEnvironment.getApplication()
        val root = FrameLayout(application)
        val lifecycleOwner = TestLifecycleOwner()
        val controller = deterministicController()
        var destinationOwner: LifecycleOwner? = null

        val session = renderInto(root) {
            ProvideLifecycleOwner(lifecycleOwner) {
                NavHost(
                    controller = controller,
                    transitionSpec = NavTransitionSpec.None,
                    overlayHostFactory = { OverlayHostDefaults.noOp },
                ) { entry ->
                    destinationOwner = LocalLifecycleOwner.current
                    Text(entry.route.name)
                }
            }
        }
        val owner = checkNotNull(destinationOwner)

        assertTrue(controller.isAttached)
        assertEquals(Lifecycle.State.INITIALIZED, owner.lifecycle.currentState)

        lifecycleOwner.moveTo(Lifecycle.State.CREATED)

        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)
        session.dispose()
    }

    @Test
    fun `parent rerender does not synchronously refresh unchanged destination content`() {
        var renderCount = 0
        val fixture = renderPublicHost { entry ->
            renderCount += 1
            Text(entry.route.name)
        }
        val originalHost = fixture.navHostView
        val initialRenderCount = renderCount

        fixture.session.render()

        assertEquals(initialRenderCount, renderCount)
        assertTrue(fixture.root.requireNavHostView() === originalHost)
        assertTrue(fixture.controller.isAttached)
        fixture.session.dispose()
    }

    @Test
    fun `changed content key refreshes visible destination without replacing host`() {
        var contentVersion = 0
        var renderCount = 0
        val fixture = renderPublicHost(
            contentKey = { contentVersion },
        ) { entry ->
            renderCount += 1
            Text("${entry.route.name}:$contentVersion")
        }
        val originalHost = fixture.navHostView
        val initialRenderCount = renderCount
        contentVersion += 1

        fixture.session.render()

        assertTrue(renderCount > initialRenderCount)
        assertTrue(fixture.root.requireNavHostView() === originalHost)
        fixture.session.dispose()
    }

    @Test
    fun `failed destination returns public failure and preserves committed page`() {
        var reportedFailure: NavFailure? = null
        val fixture = renderPublicHost(
            onFailure = { failure ->
                reportedFailure = failure
            },
        ) { entry ->
            if (entry.route.name == "broken") {
                error("broken destination")
            }
            Text(entry.route.name)
        }

        val result = fixture.controller.navigate(NavRoute("broken"))

        assertTrue(result is NavResult.Failed)
        result as NavResult.Failed
        assertEquals(NavFailurePhase.DestinationPreparation, result.failure.phase)
        assertFalse(result.failure.stackCommitted)
        assertEquals(result.failure, reportedFailure)
        assertEquals(listOf("home"), fixture.controller.routeNames())
        assertEquals(1, fixture.navHostView.childCount)
        fixture.session.dispose()
    }

    @Test
    fun `controller rejects commands before a host is attached`() {
        val controller = createNavHostController(NavRoute("home"))

        val failure = runCatching {
            controller.navigate(NavRoute("details"))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("attached NavHost"))
        assertEquals(listOf("home"), controller.routeNames())
    }

    @Test
    fun `failed initial destination can retry on the next committed parent render`() {
        val application = RuntimeEnvironment.getApplication()
        val root = FrameLayout(application)
        val lifecycleOwner = TestLifecycleOwner().apply {
            moveTo(Lifecycle.State.RESUMED)
        }
        val controller = deterministicController()
        val failures = mutableListOf<NavFailure>()
        var failInitialDestination = true
        val session = renderInto(root) {
            ProvideLifecycleOwner(lifecycleOwner) {
                NavHost(
                    controller = controller,
                    transitionSpec = NavTransitionSpec.None,
                    overlayHostFactory = { OverlayHostDefaults.noOp },
                    onFailure = failures::add,
                ) { entry ->
                    if (failInitialDestination) {
                        error("initial destination failed")
                    }
                    Text(entry.route.name)
                }
            }
        }

        assertFalse(controller.isAttached)
        assertEquals(NavFailurePhase.DestinationPreparation, failures.single().phase)
        assertEquals(0, root.requireNavHostView().childCount)

        failInitialDestination = false
        session.render()

        assertTrue(controller.isAttached)
        assertEquals(1, root.requireNavHostView().childCount)
        session.dispose()
    }

    @Test
    fun `released controller can attach a new host with its existing stack`() {
        val controller = deterministicController()
        val first = renderPublicHost(controller = controller)
        controller.navigate(NavRoute("details"))
        first.session.dispose()

        val second = renderPublicHost(controller = controller)

        assertTrue(controller.isAttached)
        assertEquals(listOf("home", "details"), controller.routeNames())
        assertEquals(2, second.navHostView.childCount)
        second.session.dispose()
    }

    @Test
    fun `released controller restores destination saveable state when remounted`() {
        val controller = deterministicController()
        var destinationState: RestorableCounter? = null
        val first = renderPublicHost(controller = controller) { entry ->
            val state = rememberSaveable(
                key = "counter",
                saver = RestorableCounterSaver,
            ) {
                RestorableCounter(0)
            }
            if (entry.route.name == "details") {
                destinationState = state
            }
            Text(entry.route.name)
        }
        controller.navigate(NavRoute("details"))
        checkNotNull(destinationState).value = 41

        first.session.dispose()
        destinationState = null
        val second = renderPublicHost(controller = controller) { entry ->
            val state = rememberSaveable(
                key = "counter",
                saver = RestorableCounterSaver,
            ) {
                RestorableCounter(-1)
            }
            if (entry.route.name == "details") {
                destinationState = state
            }
            Text(entry.route.name)
        }

        assertEquals(41, checkNotNull(destinationState).value)
        second.session.dispose()
    }

    @Test
    fun `remembered controller restores stack IDs and every destination state after recreation`() {
        val firstRegistry = createSaveableStateRegistry(
            canBeSaved = ::canBeSavedToBundle,
        )
        val first = renderRememberedHost(firstRegistry)
        checkNotNull(first.homeState).value = 11
        first.controller.navigate(NavRoute("details"))
        checkNotNull(first.detailsState).value = 29
        val expectedSnapshot = first.controller.snapshot

        val saved = firstRegistry.performSave()
        first.session.dispose()
        val restoredRegistry = createSaveableStateRegistry(
            restoredValues = saved,
            canBeSaved = ::canBeSavedToBundle,
        )
        val restored = renderRememberedHost(restoredRegistry)

        assertEquals(expectedSnapshot, restored.controller.snapshot)
        assertEquals(11, checkNotNull(restored.homeState).value)
        assertEquals(29, checkNotNull(restored.detailsState).value)
        restored.session.dispose()
    }

    @Test
    fun `remembered multi stack host restores active tab histories and inactive state`() {
        val firstRegistry = createSaveableStateRegistry(
            canBeSaved = ::canBeSavedToBundle,
        )
        val first = renderRememberedMultiStackHost(firstRegistry)
        first.state("home").value = 11
        first.controller.selectStack(MultiSearchStack)
        first.state("search").value = 22
        first.controller.navigate(NavRoute("search-result"))
        first.state("search-result").value = 33
        first.controller.selectStack(MultiHomeStack)
        val expectedState = first.controller.stackState

        val saved = firstRegistry.performSave()
        first.session.dispose()
        val restoredRegistry = createSaveableStateRegistry(
            restoredValues = saved,
            canBeSaved = ::canBeSavedToBundle,
        )
        val restored = renderRememberedMultiStackHost(restoredRegistry)

        assertEquals(expectedState, restored.controller.stackState)
        assertEquals(MultiHomeStack, restored.controller.activeStackId)
        assertEquals(11, restored.state("home").value)
        assertEquals(22, restored.state("search").value)
        assertEquals(33, restored.state("search-result").value)

        restored.controller.selectStack(MultiSearchStack)

        assertEquals("search-result", restored.controller.snapshot.top.route.name)
        assertEquals(33, restored.state("search-result").value)
        restored.session.dispose()
    }

    private fun renderPublicHost(
        controller: NavHostController = deterministicController(),
        onFailure: ((NavFailure) -> Unit)? = null,
        contentKey: () -> Any? = { Unit },
        content: com.viewcompose.widget.core.UiTreeBuilder.(
            com.viewcompose.navigation.core.NavEntry,
        ) -> Unit = { entry -> Text(entry.route.name) },
    ): PublicHostFixture {
        val application = RuntimeEnvironment.getApplication()
        val root = FrameLayout(application)
        val lifecycleOwner = TestLifecycleOwner().apply {
            moveTo(Lifecycle.State.RESUMED)
        }
        val session = renderInto(root) {
            ProvideLifecycleOwner(lifecycleOwner) {
                NavHost(
                    controller = controller,
                    transitionSpec = NavTransitionSpec.None,
                    overlayHostFactory = { OverlayHostDefaults.noOp },
                    onFailure = onFailure,
                    contentKey = contentKey(),
                    content = content,
                )
            }
        }
        val navHostView = root.requireNavHostView()
        return PublicHostFixture(
            root = root,
            lifecycleOwner = lifecycleOwner,
            controller = controller,
            session = session,
            navHostView = navHostView,
        )
    }

    private fun deterministicController(): NavHostController {
        val entryIds = ArrayDeque(
            listOf(
                "root",
                "details",
                "broken",
                "next",
            ),
        )
        return createNavHostController(
            startDestination = NavRoute("home"),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(entryIds.removeFirst())
            },
        )
    }

    private fun renderRememberedHost(
        registry: SaveableStateRegistry,
    ): RememberedHostFixture {
        val application = RuntimeEnvironment.getApplication()
        val root = FrameLayout(application)
        val lifecycleOwner = TestLifecycleOwner().apply {
            moveTo(Lifecycle.State.RESUMED)
        }
        var controller: NavHostController? = null
        var homeState: RestorableCounter? = null
        var detailsState: RestorableCounter? = null
        val session = renderInto(root) {
            ProvideLifecycleOwner(lifecycleOwner) {
                ProvideSaveableStateRegistry(registry) {
                    val rememberedController = rememberNavHostController(
                        startDestination = NavRoute("home"),
                    )
                    controller = rememberedController
                    NavHost(
                        controller = rememberedController,
                        transitionSpec = NavTransitionSpec.None,
                        overlayHostFactory = { OverlayHostDefaults.noOp },
                    ) { entry ->
                        val state = rememberSaveable(
                            key = "counter",
                            saver = RestorableCounterSaver,
                        ) {
                            RestorableCounter(0)
                        }
                        when (entry.route.name) {
                            "home" -> homeState = state
                            "details" -> detailsState = state
                        }
                        Text(entry.route.name)
                    }
                }
            }
        }
        return RememberedHostFixture(
            controller = checkNotNull(controller),
            session = session,
            homeStateProvider = { homeState },
            detailsStateProvider = { detailsState },
        )
    }

    private fun renderRememberedMultiStackHost(
        registry: SaveableStateRegistry,
    ): RememberedMultiStackHostFixture {
        val application = RuntimeEnvironment.getApplication()
        val root = FrameLayout(application)
        val lifecycleOwner = TestLifecycleOwner().apply {
            moveTo(Lifecycle.State.RESUMED)
        }
        val states = linkedMapOf<String, RestorableCounter>()
        var controller: NavHostController? = null
        val session = renderInto(root) {
            ProvideLifecycleOwner(lifecycleOwner) {
                ProvideSaveableStateRegistry(registry) {
                    val rememberedController = rememberNavHostController(
                        NavStackConfiguration(
                            initialStackId = MultiHomeStack,
                            stacks = listOf(
                                NavStackSpec(MultiHomeStack, NavRoute("home")),
                                NavStackSpec(MultiSearchStack, NavRoute("search")),
                            ),
                        ),
                    )
                    controller = rememberedController
                    NavHost(
                        controller = rememberedController,
                        transitionSpec = NavTransitionSpec.None,
                        overlayHostFactory = { OverlayHostDefaults.noOp },
                    ) { entry ->
                        states[entry.route.name] = rememberSaveable(
                            key = "counter",
                            saver = RestorableCounterSaver,
                        ) {
                            RestorableCounter(0)
                        }
                        Text(entry.route.name)
                    }
                }
            }
        }
        return RememberedMultiStackHostFixture(
            controller = checkNotNull(controller),
            session = session,
            states = states,
        )
    }

    private fun NavHostController.routeNames(): List<String> {
        return snapshot.entries.map { entry -> entry.route.name }
    }

    private companion object {
        val MultiHomeStack = NavStackId("multi-home")
        val MultiSearchStack = NavStackId("multi-search")
    }
}

private class RememberedHostFixture(
    val controller: NavHostController,
    val session: com.viewcompose.host.android.RenderSession,
    private val homeStateProvider: () -> RestorableCounter?,
    private val detailsStateProvider: () -> RestorableCounter?,
) {
    val homeState: RestorableCounter?
        get() = homeStateProvider()

    val detailsState: RestorableCounter?
        get() = detailsStateProvider()
}

private class RestorableCounter(
    var value: Int,
)

private class RememberedMultiStackHostFixture(
    val controller: NavHostController,
    val session: com.viewcompose.host.android.RenderSession,
    private val states: Map<String, RestorableCounter>,
) {
    fun state(route: String): RestorableCounter = checkNotNull(states[route])
}

private class ReleaseTrackingViewModel : ViewModel() {
    var cleared: Boolean = false
        private set

    override fun onCleared() {
        cleared = true
    }
}

private object ReleaseTrackingViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(modelClass == ReleaseTrackingViewModel::class.java)
        return ReleaseTrackingViewModel() as T
    }
}

private val RestorableCounterSaver = Saver<RestorableCounter, Int>(
    save = RestorableCounter::value,
    restore = ::RestorableCounter,
)

private fun canBeSavedToBundle(value: Any?): Boolean {
    return when (value) {
        null -> true
        is Function<*> -> false
        is List<*> -> value.all(::canBeSavedToBundle)
        is Map<*, *> -> value.all { (key, item) ->
            key is String && canBeSavedToBundle(item)
        }
        is Array<*> -> value.all(::canBeSavedToBundle)
        is Parcelable,
        is Serializable,
        -> true
        else -> false
    }
}

private data class PublicHostFixture(
    val root: FrameLayout,
    val lifecycleOwner: TestLifecycleOwner,
    val controller: NavHostController,
    val session: com.viewcompose.host.android.RenderSession,
    val navHostView: NavHostView,
)

private class TestLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = registry

    fun moveTo(state: Lifecycle.State) {
        registry.currentState = state
    }
}
