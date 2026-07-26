package com.viewcompose.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryLifecycleState
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.navigation.core.NavRoute
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
    fun `hidden entry keeps owner and ViewModel until permanently removed`() {
        val root = entry("root", "home")
        val details = entry("details", "details")
        val store = store()

        store.reconcile(
            retainedEntries = listOf(root),
            visibleEntryIds = setOf(root.id),
            interactiveEntryId = root.id,
            hostState = NavHostLifecycleState.Resumed,
        )
        val rootOwner = checkNotNull(store.ownerOrNull(root.id))
        val rootViewModel = rootOwner.viewModel<TrackingViewModel>("root-vm")

        store.reconcile(
            retainedEntries = listOf(root, details),
            visibleEntryIds = setOf(details.id),
            interactiveEntryId = details.id,
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
            interactiveEntryId = root.id,
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
            interactiveEntryId = root.id,
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
            interactiveEntryId = details.id,
            hostState = NavHostLifecycleState.Resumed,
        )

        assertTrue(events.indexOf("root:ON_PAUSE") < events.indexOf("details:ON_CREATE"))
        assertEquals(Lifecycle.State.STARTED, rootOwner.lifecycle.currentState)
        assertEquals(Lifecycle.State.RESUMED, detailsOwner.lifecycle.currentState)
    }

    @Test
    fun `store save restores composition and ViewModel state by entry ID`() {
        val root = entry("root", "home")
        val firstStore = store()
        firstStore.reconcile(
            retainedEntries = listOf(root),
            visibleEntryIds = setOf(root.id),
            interactiveEntryId = root.id,
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
            interactiveEntryId = root.id,
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
            interactiveEntryId = root.id,
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
            interactiveEntryId = root.id,
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
            interactiveEntryId = details.id,
            hostState = NavHostLifecycleState.Resumed,
        )
        val rootViewModel = checkNotNull(store.ownerOrNull(root.id))
            .viewModel<TrackingViewModel>("root-vm")
        val detailsViewModel = checkNotNull(store.ownerOrNull(details.id))
            .viewModel<TrackingViewModel>("details-vm")

        store.reconcile(
            retainedEntries = listOf(root, details),
            visibleEntryIds = setOf(details.id),
            interactiveEntryId = details.id,
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
    fun `entry ID cannot be rebound to a different route`() {
        val store = store()
        store.ownerFor(entry("same", "first"))

        assertThrows<IllegalStateException> {
            store.ownerFor(entry("same", "second"))
        }
    }

    private fun store(restoredState: android.os.Bundle? = null): NavEntryOwnerStore {
        return NavEntryOwnerStore(
            application = RuntimeEnvironment.getApplication(),
            restoredState = restoredState,
        )
    }

    private fun entry(
        id: String,
        route: String,
    ): NavEntry {
        return NavEntry(
            id = NavEntryId(id),
            route = NavRoute(route),
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
