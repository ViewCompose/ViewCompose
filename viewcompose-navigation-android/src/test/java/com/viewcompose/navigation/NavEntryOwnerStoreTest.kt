package com.viewcompose.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryLifecycleState
import com.viewcompose.navigation.core.NavEntryPresence
import com.viewcompose.navigation.core.NavGraphEntry
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.navigation.core.NavLifecyclePlan
import com.viewcompose.navigation.core.NavPaneRole
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavScene
import com.viewcompose.navigation.core.NavSceneEntry
import com.viewcompose.navigation.core.NavSceneInteraction
import com.viewcompose.navigation.core.NavSceneLayerRole
import com.viewcompose.navigation.core.NavSceneTransitionPhase
import com.viewcompose.navigation.core.NavSceneVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NavEntryOwnerStoreTest {
    @Test
    fun `owner contexts publish the exact multi-pane and overlay scene projections`() {
        val primary = entry("primary", "list")
        val secondary = entry("secondary", "details")
        val overlay = entry("overlay", "dialog")
        val sceneEntries = listOf(
            NavSceneEntry(
                entryId = primary.id,
                presence = NavEntryPresence.Retained,
                visibility = NavSceneVisibility.Covered,
                interaction = NavSceneInteraction.NonInteractive,
                transitionPhase = NavSceneTransitionPhase.Settled,
                paneRole = NavPaneRole.Primary,
            ),
            NavSceneEntry(
                entryId = secondary.id,
                presence = NavEntryPresence.Retained,
                visibility = NavSceneVisibility.Covered,
                interaction = NavSceneInteraction.NonInteractive,
                transitionPhase = NavSceneTransitionPhase.Settled,
                paneRole = NavPaneRole.Secondary,
            ),
            NavSceneEntry(
                entryId = overlay.id,
                presence = NavEntryPresence.Retained,
                visibility = NavSceneVisibility.Visible,
                interaction = NavSceneInteraction.Interactive,
                transitionPhase = NavSceneTransitionPhase.Settled,
                paneRole = null,
                layerRole = NavSceneLayerRole.Overlay,
            ),
        )
        val store = store()

        store.reconcile(
            retainedEntries = listOf(primary, secondary, overlay),
            scene = NavScene(sceneEntries),
            hostState = NavHostLifecycleState.Resumed,
        )

        sceneEntries.forEach { expected ->
            assertSame(
                expected,
                checkNotNull(store.ownerOrNull(expected.entryId))
                    .destinationContext.presentation.value,
            )
        }
    }

    @Test
    fun `permanent removal destroys owner and freezes captured destination context`() {
        val root = entry("root", "home")
        val details = entry("details", "details")
        val store = store()
        store.reconcile(
            retainedEntries = listOf(root, details),
            visibleEntryIds = setOf(details.id),
            interactiveEntryIds = setOf(details.id),
            hostState = NavHostLifecycleState.Resumed,
        )
        val detailsOwner = checkNotNull(store.ownerOrNull(details.id))
        val capturedContext = detailsOwner.destinationContext
        val lastPresentation = capturedContext.presentation.value

        store.reconcile(
            retainedEntries = listOf(root),
            visibleEntryIds = setOf(root.id),
            interactiveEntryIds = setOf(root.id),
            hostState = NavHostLifecycleState.Resumed,
        )

        assertNull(store.ownerOrNull(details.id))
        assertEquals(NavEntryLifecycleState.Destroyed, detailsOwner.entryLifecycleState)
        assertSame(lastPresentation, capturedContext.presentation.value)
    }

    @Test
    fun `hidden entry keeps owner and ViewModel until permanently removed`() {
        val root = entry("root", "home")
        val details = entry("details", "details")
        val store = store()

        store.reconcile(
            retainedEntries = listOf(root),
            visibleEntryIds = setOf(root.id),
            interactiveEntryIds = setOf(root.id),
            hostState = NavHostLifecycleState.Resumed,
        )
        val rootOwner = checkNotNull(store.ownerOrNull(root.id))
        val rootViewModel = rootOwner.viewModel<TrackingViewModel>("root-vm")

        store.reconcile(
            retainedEntries = listOf(root, details),
            visibleEntryIds = setOf(details.id),
            interactiveEntryIds = setOf(details.id),
            hostState = NavHostLifecycleState.Resumed,
        )

        assertSame(rootOwner, store.ownerOrNull(root.id))
        assertEquals(NavEntryLifecycleState.Created, rootOwner.entryLifecycleState)
        assertSame(rootViewModel, rootOwner.viewModel<TrackingViewModel>("root-vm"))
        assertTrue(!rootViewModel.cleared)

        val detailsOwner = checkNotNull(store.ownerOrNull(details.id))
        val detailsViewModel = detailsOwner.viewModel<TrackingViewModel>("details-vm")
        store.reconcile(
            retainedEntries = listOf(root),
            visibleEntryIds = setOf(root.id),
            interactiveEntryIds = setOf(root.id),
            hostState = NavHostLifecycleState.Resumed,
        )

        assertNull(store.ownerOrNull(details.id))
        assertTrue(detailsViewModel.cleared)
        assertEquals(NavEntryLifecycleState.Resumed, rootOwner.entryLifecycleState)
    }

    @Test
    fun `interactive ownership downgrades old page before new page resumes`() {
        val root = entry("root", "home")
        val details = entry("details", "details")
        val store = store()
        store.reconcile(
            retainedEntries = listOf(root),
            visibleEntryIds = setOf(root.id),
            interactiveEntryIds = setOf(root.id),
            hostState = NavHostLifecycleState.Resumed,
        )
        val rootOwner = checkNotNull(store.ownerOrNull(root.id))
        val detailsOwner = store.ownerFor(details)
        val events = mutableListOf<String>()
        rootOwner.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                events += "root:$event"
            },
        )
        detailsOwner.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                events += "details:$event"
            },
        )

        store.reconcile(
            retainedEntries = listOf(root, details),
            visibleEntryIds = setOf(root.id, details.id),
            interactiveEntryIds = setOf(details.id),
            hostState = NavHostLifecycleState.Resumed,
        )

        assertTrue(events.indexOf("root:ON_PAUSE") < events.indexOf("details:ON_CREATE"))
        assertEquals(Lifecycle.State.STARTED, rootOwner.lifecycle.currentState)
        assertEquals(Lifecycle.State.RESUMED, detailsOwner.lifecycle.currentState)
    }

    @Test
    fun `destinations share graph owners until the last graph reference is removed`() {
        val appGraph = graphEntry("app-scope", "app")
        val accountGraph = graphEntry("account-scope", "account")
        val profile = entry(
            id = "profile",
            route = "profile",
            graphEntries = listOf(appGraph, accountGraph),
        )
        val security = entry(
            id = "security",
            route = "security",
            graphEntries = listOf(appGraph, accountGraph),
        )
        val home = entry(
            id = "home",
            route = "home",
            graphEntries = listOf(appGraph),
        )
        val store = store()

        store.reconcile(
            retainedEntries = listOf(profile),
            visibleEntryIds = setOf(profile.id),
            interactiveEntryIds = setOf(profile.id),
            hostState = NavHostLifecycleState.Resumed,
        )
        val appOwner = checkNotNull(store.graphOwnerOrNull(appGraph.id))
        val accountOwner = checkNotNull(store.graphOwnerOrNull(accountGraph.id))
        val accountViewModel = accountOwner.delegate
            .viewModel<TrackingViewModel>("account-vm")

        store.reconcile(
            retainedEntries = listOf(profile, security),
            visibleEntryIds = setOf(security.id),
            interactiveEntryIds = setOf(security.id),
            hostState = NavHostLifecycleState.Resumed,
        )

        assertSame(appOwner, store.graphOwnerOrNull(appGraph.id))
        assertSame(accountOwner, store.graphOwnerOrNull(accountGraph.id))
        assertSame(
            accountViewModel,
            accountOwner.delegate.viewModel<TrackingViewModel>("account-vm"),
        )
        assertEquals(Lifecycle.State.RESUMED, accountOwner.lifecycle.currentState)

        store.reconcile(
            retainedEntries = listOf(home),
            visibleEntryIds = setOf(home.id),
            interactiveEntryIds = setOf(home.id),
            hostState = NavHostLifecycleState.Resumed,
        )

        assertSame(appOwner, store.graphOwnerOrNull(appGraph.id))
        assertNull(store.graphOwnerOrNull(accountGraph.id))
        assertTrue(accountViewModel.cleared)
    }

    @Test
    fun `graph lifecycle starts parent first and destroys child first`() {
        val appGraph = graphEntry("app-scope", "app")
        val accountGraph = graphEntry("account-scope", "account")
        val profile = entry(
            id = "profile",
            route = "profile",
            graphEntries = listOf(appGraph, accountGraph),
        )
        val store = store()
        val appOwner = store.graphOwnerFor(appGraph, depth = 0)
        val accountOwner = store.graphOwnerFor(accountGraph, depth = 1)
        val profileOwner = store.ownerFor(profile)
        val events = mutableListOf<String>()
        appOwner.lifecycle.addObserver(
            LifecycleEventObserver { _, event -> events += "app:$event" },
        )
        accountOwner.lifecycle.addObserver(
            LifecycleEventObserver { _, event -> events += "account:$event" },
        )
        profileOwner.lifecycle.addObserver(
            LifecycleEventObserver { _, event -> events += "profile:$event" },
        )

        store.reconcile(
            retainedEntries = listOf(profile),
            visibleEntryIds = setOf(profile.id),
            interactiveEntryIds = setOf(profile.id),
            hostState = NavHostLifecycleState.Resumed,
        )

        assertTrue(events.indexOf("app:ON_CREATE") < events.indexOf("account:ON_CREATE"))
        assertTrue(events.indexOf("account:ON_CREATE") < events.indexOf("profile:ON_CREATE"))
        events.clear()

        store.reconcile(
            retainedEntries = listOf(profile),
            visibleEntryIds = setOf(profile.id),
            interactiveEntryIds = setOf(profile.id),
            hostState = NavHostLifecycleState.Destroyed,
        )

        assertTrue(events.indexOf("profile:ON_DESTROY") < events.indexOf("account:ON_DESTROY"))
        assertTrue(events.indexOf("account:ON_DESTROY") < events.indexOf("app:ON_DESTROY"))
    }

    @Test
    fun `graph SavedStateHandle survives owner store recreation`() {
        val accountGraph = NavGraphEntry(
            id = NavEntryId("account-scope"),
            route = NavRoute(
                name = "account",
                arguments = mapOf(
                    "userId" to com.viewcompose.navigation.core.NavValue.LongValue(42L),
                ),
            ),
        )
        val profile = entry(
            id = "profile",
            route = "profile",
            graphEntries = listOf(accountGraph),
        )
        val firstStore = store()
        firstStore.reconcile(
            retainedEntries = listOf(profile),
            visibleEntryIds = setOf(profile.id),
            interactiveEntryIds = setOf(profile.id),
            hostState = NavHostLifecycleState.Created,
        )
        val firstOwner = checkNotNull(firstStore.graphOwnerOrNull(accountGraph.id))
        val firstViewModel = firstOwner.delegate
            .viewModel<SavedStateViewModel>("account-vm")
        assertEquals(42L, firstViewModel.handle["userId"])
        firstViewModel.handle["selection"] = 7

        val saved = firstStore.performSave(
            setOf(accountGraph.id, profile.id),
        )
        firstStore.destroy()
        val restoredStore = store(saved)
        restoredStore.reconcile(
            retainedEntries = listOf(profile),
            visibleEntryIds = setOf(profile.id),
            interactiveEntryIds = setOf(profile.id),
            hostState = NavHostLifecycleState.Created,
        )
        val restoredViewModel = checkNotNull(restoredStore.graphOwnerOrNull(accountGraph.id))
            .delegate
            .viewModel<SavedStateViewModel>("account-vm")

        assertEquals(42L, restoredViewModel.handle["userId"])
        assertEquals(7, restoredViewModel.handle["selection"])
        restoredStore.destroy()
    }

    @Test
    fun `store save restores composition and ViewModel state by entry ID`() {
        val root = entry("root", "home")
        val firstStore = store()
        firstStore.reconcile(
            retainedEntries = listOf(root),
            visibleEntryIds = setOf(root.id),
            interactiveEntryIds = setOf(root.id),
            hostState = NavHostLifecycleState.Created,
        )
        val firstOwner = checkNotNull(firstStore.ownerOrNull(root.id))
        firstOwner.compositionSaveableStateRegistry.registerProvider("selectedTab") { 3 }
        firstOwner.viewModel<SavedStateViewModel>("root-vm").handle["query"] = "ViewCompose"

        val saved = firstStore.performSave(setOf(root.id))
        firstStore.destroy()
        val restoredStore = store(saved)
        restoredStore.reconcile(
            retainedEntries = listOf(root),
            visibleEntryIds = setOf(root.id),
            interactiveEntryIds = setOf(root.id),
            hostState = NavHostLifecycleState.Created,
        )
        val restoredOwner = checkNotNull(restoredStore.ownerOrNull(root.id))

        assertEquals(
            3,
            restoredOwner.compositionSaveableStateRegistry
                .consumeRestored("selectedTab")
                ?.value,
        )
        assertEquals(
            "ViewCompose",
            restoredOwner.viewModel<SavedStateViewModel>("root-vm").handle["query"],
        )
    }

    @Test
    fun `store save excludes transition-only owners outside the committed stack`() {
        val root = entry("root", "home")
        val outgoing = entry("outgoing", "details")
        val firstStore = store()
        firstStore.reconcile(
            retainedEntries = listOf(root, outgoing),
            visibleEntryIds = setOf(root.id, outgoing.id),
            interactiveEntryIds = setOf(root.id),
            hostState = NavHostLifecycleState.Started,
        )
        checkNotNull(firstStore.ownerOrNull(root.id))
            .compositionSaveableStateRegistry
            .registerProvider("value") { "root-state" }
        checkNotNull(firstStore.ownerOrNull(outgoing.id))
            .compositionSaveableStateRegistry
            .registerProvider("value") { "outgoing-state" }

        val saved = firstStore.performSave(setOf(root.id))
        firstStore.destroy()
        val restoredStore = store(saved)
        restoredStore.reconcile(
            retainedEntries = listOf(root, outgoing),
            visibleEntryIds = setOf(root.id),
            interactiveEntryIds = setOf(root.id),
            hostState = NavHostLifecycleState.Started,
        )

        assertEquals(
            "root-state",
            checkNotNull(restoredStore.ownerOrNull(root.id))
                .compositionSaveableStateRegistry
                .consumeRestored("value")
                ?.value,
        )
        assertNull(
            checkNotNull(restoredStore.ownerOrNull(outgoing.id))
                .compositionSaveableStateRegistry
                .consumeRestored("value"),
        )
        restoredStore.destroy()
    }

    @Test
    fun `candidate owner can be removed during navigation rollback`() {
        val candidate = entry("candidate", "details")
        val store = store()
        val owner = store.ownerFor(candidate)
        owner.moveTo(NavEntryLifecycleState.Created)
        val viewModel = owner.viewModel<TrackingViewModel>("candidate-vm")

        store.remove(candidate.id)

        assertNull(store.ownerOrNull(candidate.id))
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        assertTrue(viewModel.cleared)
    }

    @Test
    fun `host destruction clears every owner and closes the store`() {
        val root = entry("root", "home")
        val details = entry("details", "details")
        val store = store()
        store.reconcile(
            retainedEntries = listOf(root, details),
            visibleEntryIds = setOf(details.id),
            interactiveEntryIds = setOf(details.id),
            hostState = NavHostLifecycleState.Resumed,
        )
        val rootViewModel = checkNotNull(store.ownerOrNull(root.id))
            .viewModel<TrackingViewModel>("root-vm")
        val detailsViewModel = checkNotNull(store.ownerOrNull(details.id))
            .viewModel<TrackingViewModel>("details-vm")

        store.reconcile(
            retainedEntries = listOf(root, details),
            visibleEntryIds = setOf(details.id),
            interactiveEntryIds = setOf(details.id),
            hostState = NavHostLifecycleState.Destroyed,
        )

        assertTrue(rootViewModel.cleared)
        assertTrue(detailsViewModel.cleared)
        assertNull(store.ownerOrNull(root.id))
        assertNull(store.ownerOrNull(details.id))
        assertThrows<IllegalStateException> {
            store.ownerFor(root)
        }
        assertThrows<IllegalStateException> {
            store.performSave(emptySet())
        }
    }

    @Test
    fun `host recreation retains entry ViewModels below the same parent owner`() {
        val root = entry("root", "home")
        val parentOwner = NavigationTestParentViewModelStoreOwner()
        val providerKey = NavHostOwnerScopeId("retained-host")
        val firstStore = navigationTestOwnerStore(
            application = RuntimeEnvironment.getApplication(),
            parentOwner = parentOwner,
            providerKey = providerKey,
        )
        firstStore.reconcile(
            retainedEntries = listOf(root),
            visibleEntryIds = setOf(root.id),
            interactiveEntryIds = setOf(root.id),
            hostState = NavHostLifecycleState.Resumed,
        )
        val firstOwner = checkNotNull(firstStore.ownerOrNull(root.id))
        val retainedViewModel = firstOwner.viewModel<TrackingViewModel>("root-vm")

        firstStore.destroy(retainViewModelScopes = true)

        assertEquals(Lifecycle.State.DESTROYED, firstOwner.lifecycle.currentState)
        assertTrue(!retainedViewModel.cleared)

        val recreatedStore = navigationTestOwnerStore(
            application = RuntimeEnvironment.getApplication(),
            parentOwner = parentOwner,
            providerKey = providerKey,
        )
        recreatedStore.reconcile(
            retainedEntries = listOf(root),
            visibleEntryIds = setOf(root.id),
            interactiveEntryIds = setOf(root.id),
            hostState = NavHostLifecycleState.Resumed,
        )

        assertSame(
            retainedViewModel,
            checkNotNull(recreatedStore.ownerOrNull(root.id))
                .viewModel<TrackingViewModel>("root-vm"),
        )

        recreatedStore.destroy()

        assertTrue(retainedViewModel.cleared)
        parentOwner.viewModelStore.clear()
    }

    @Test
    fun `entry ID cannot be rebound to a different route`() {
        val store = store()
        store.ownerFor(entry("same", "first"))

        assertThrows<IllegalStateException> {
            store.ownerFor(entry("same", "second"))
        }
    }

    private fun store(restoredState: android.os.Bundle? = null): NavEntryOwnerStore {
        return navigationTestOwnerStore(
            application = RuntimeEnvironment.getApplication(),
            restoredState = restoredState,
        )
    }

    private fun entry(
        id: String,
        route: String,
        graphEntries: List<NavGraphEntry> = emptyList(),
    ): NavEntry {
        return NavEntry(
            id = NavEntryId(id),
            route = NavRoute(route),
            graphEntries = graphEntries,
        )
    }

    private fun graphEntry(
        id: String,
        route: String,
    ): NavGraphEntry {
        return NavGraphEntry(
            id = NavEntryId(id),
            route = NavRoute(route),
        )
    }

    private fun NavEntryOwnerStore.reconcile(
        retainedEntries: List<NavEntry>,
        visibleEntryIds: Set<NavEntryId>,
        interactiveEntryIds: Set<NavEntryId>,
        hostState: NavHostLifecycleState,
    ): NavLifecyclePlan {
        return reconcile(
            retainedEntries = retainedEntries,
            scene = NavScene(
                retainedEntries.map { entry ->
                    val isVisible = entry.id in visibleEntryIds
                    NavSceneEntry(
                        entryId = entry.id,
                        presence = NavEntryPresence.Retained,
                        visibility = if (isVisible) {
                            NavSceneVisibility.Visible
                        } else {
                            NavSceneVisibility.Hidden
                        },
                        interaction = if (entry.id in interactiveEntryIds) {
                            NavSceneInteraction.Interactive
                        } else {
                            NavSceneInteraction.NonInteractive
                        },
                        transitionPhase = NavSceneTransitionPhase.Settled,
                        paneRole = if (isVisible) NavPaneRole.Primary else null,
                    )
                },
            ),
            hostState = hostState,
        )
    }

    private inline fun <reified VM : ViewModel> NavEntryOwner.viewModel(key: String): VM {
        return ViewModelProvider(
            viewModelStore,
            defaultViewModelProviderFactory,
            defaultViewModelCreationExtras,
        )[key, VM::class.java]
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        try {
            block()
        } catch (throwable: Throwable) {
            if (throwable is T) {
                return throwable
            }
            throw throwable
        }
        fail("Expected ${T::class.simpleName} to be thrown.")
        error("Unreachable")
    }

    class TrackingViewModel : ViewModel() {
        var cleared: Boolean = false

        override fun onCleared() {
            cleared = true
        }
    }

    class SavedStateViewModel(
        val handle: SavedStateHandle,
    ) : ViewModel()
}
