package com.viewcompose.navigation

import android.view.View
import androidx.lifecycle.Lifecycle
import com.viewcompose.lifecycle.LocalLifecycleOwner
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryLifecycleState
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.viewmodel.LocalViewModelStoreOwner
import com.viewcompose.widget.core.LocalSaveableStateRegistry
import com.viewcompose.widget.core.ProvideLocal
import com.viewcompose.widget.core.RenderFrameStatus
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.UiLocalSnapshot
import com.viewcompose.widget.core.UiLocals
import com.viewcompose.widget.core.buildVNodeTree
import com.viewcompose.widget.core.captureUiLocalSnapshot
import com.viewcompose.widget.core.uiLocalOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NavDestinationSessionStoreTest {
    private lateinit var ownerStore: NavEntryOwnerStore
    private lateinit var sessionStore: NavDestinationSessionStore

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        ownerStore = NavEntryOwnerStore(application)
        sessionStore = NavDestinationSessionStore(
            hostView = NavHostView(application),
            ownerStore = ownerStore,
        )
    }

    @After
    fun tearDown() {
        sessionStore.destroy()
    }

    @Test
    fun `candidate renders before it is staged or committed`() {
        val entry = entry("root", "home")

        val candidate = sessionStore.prepare(
            entry = entry,
            localSnapshot = captureUiLocalSnapshot(),
            hostLifecycleState = NavHostLifecycleState.Created,
        ) {
            Text("Home")
        }.readyCandidate()

        assertEquals(NavDestinationCandidateStatus.Prepared, candidate.status)
        assertEquals(0, sessionStore.hostView.childCount)
        assertTrue(candidate.destinationSession.container.childCount > 0)
        assertEquals(
            NavEntryLifecycleState.Created,
            checkNotNull(ownerStore.ownerOrNull(entry.id)).entryLifecycleState,
        )

        candidate.stage()
        assertEquals(NavDestinationCandidateStatus.Staged, candidate.status)
        assertEquals(1, sessionStore.hostView.childCount)
        assertEquals(View.GONE, candidate.destinationSession.container.visibility)

        val committed = candidate.commit()
        sessionStore.present(
            layerOrder = listOf(entry.id),
            visibleEntryIds = setOf(entry.id),
        )

        assertEquals(NavDestinationCandidateStatus.Committed, candidate.status)
        assertSame(committed, sessionStore.sessionOrNull(entry.id))
        assertEquals(View.VISIBLE, committed.container.visibility)
    }

    @Test
    fun `candidate remains initialized when platform host is still initializing`() {
        val entry = entry("root", "home")

        val candidate = sessionStore.prepare(
            entry = entry,
            localSnapshot = captureUiLocalSnapshot(),
            hostLifecycleState = NavHostLifecycleState.Initialized,
        ) {
            Text("Home")
        }.readyCandidate()
        val owner = checkNotNull(ownerStore.ownerOrNull(entry.id))

        assertEquals(NavEntryLifecycleState.Initialized, owner.entryLifecycleState)
        assertEquals(Lifecycle.State.INITIALIZED, owner.lifecycle.currentState)
        assertTrue(candidate.destinationSession.container.childCount > 0)
        candidate.rollback()
    }

    @Test
    fun `failed first render rolls back session and page owner`() {
        val entry = entry("broken", "broken")

        val result = sessionStore.prepare(
            entry = entry,
            localSnapshot = captureUiLocalSnapshot(),
            hostLifecycleState = NavHostLifecycleState.Created,
        ) {
            error("destination failed")
        }

        assertTrue(result is NavDestinationPreparation.Failed)
        val failed = result as NavDestinationPreparation.Failed
        assertTrue(failed.cause?.message.orEmpty().contains("destination failed"))
        assertEquals(0, sessionStore.hostView.childCount)
        assertNull(sessionStore.sessionOrNull(entry.id))
        assertNull(ownerStore.ownerOrNull(entry.id))
    }

    @Test
    fun `page render restores parent locals then overrides page owners`() {
        val businessLocal = uiLocalOf { "default" }
        var localSnapshot: UiLocalSnapshot? = null
        buildVNodeTree {
            ProvideLocal(businessLocal, "captured") {
                localSnapshot = captureUiLocalSnapshot()
            }
        }
        var observedBusinessValue = ""
        var observedLifecycleOwner: Any? = null
        var observedViewModelOwner: Any? = null
        var observedSaveableRegistry: Any? = null
        val entry = entry("details", "details")

        val candidate = sessionStore.prepare(
            entry = entry,
            localSnapshot = checkNotNull(localSnapshot),
            hostLifecycleState = NavHostLifecycleState.Created,
        ) {
            observedBusinessValue = UiLocals.current(businessLocal)
            observedLifecycleOwner = LocalLifecycleOwner.current
            observedViewModelOwner = LocalViewModelStoreOwner.current
            observedSaveableRegistry = LocalSaveableStateRegistry.current
            Text("Details")
        }.readyCandidate()
        val owner = checkNotNull(ownerStore.ownerOrNull(entry.id))

        assertEquals("captured", observedBusinessValue)
        assertSame(owner, observedLifecycleOwner)
        assertSame(owner, observedViewModelOwner)
        assertSame(owner.compositionSaveableStateRegistry, observedSaveableRegistry)
        candidate.rollback()
    }

    @Test
    fun `committed page refreshes parent locals and content closure without recreating session`() {
        val businessLocal = uiLocalOf { "default" }
        val firstSnapshot = captureSnapshot(businessLocal, "first")
        val secondSnapshot = captureSnapshot(businessLocal, "second")
        val observedValues = mutableListOf<String>()
        val entry = entry("refresh", "refresh")
        val candidate = sessionStore.prepare(
            entry = entry,
            localSnapshot = firstSnapshot,
            hostLifecycleState = NavHostLifecycleState.Created,
        ) {
            observedValues += "initial:${UiLocals.current(businessLocal)}"
            Text("Initial")
        }.readyCandidate()
        candidate.stage()
        val session = candidate.commit()

        session.render(secondSnapshot) {
            observedValues += "updated:${UiLocals.current(businessLocal)}"
            Text("Updated")
        }

        assertEquals(listOf("initial:first", "updated:second"), observedValues)
        assertSame(session, sessionStore.sessionOrNull(entry.id))
        assertEquals(RenderFrameStatus.Committed, session.lastFrameReport?.status)
    }

    @Test
    fun `staged candidate rollback detaches view disposes session and destroys owner`() {
        val entry = entry("candidate", "details")
        val candidate = sessionStore.prepare(
            entry = entry,
            localSnapshot = captureUiLocalSnapshot(),
            hostLifecycleState = NavHostLifecycleState.Created,
        ) {
            Text("Candidate")
        }.readyCandidate()
        val owner = checkNotNull(ownerStore.ownerOrNull(entry.id))
        candidate.stage()

        candidate.rollback()

        assertEquals(NavDestinationCandidateStatus.RolledBack, candidate.status)
        assertEquals(0, sessionStore.hostView.childCount)
        assertNull(sessionStore.sessionOrNull(entry.id))
        assertNull(ownerStore.ownerOrNull(entry.id))
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }

    @Test
    fun `presentation keeps hidden page session and orders visible layers`() {
        val root = entry("root", "home")
        val details = entry("details", "details")
        val rootSession = prepareAndCommit(root, "Home")
        val detailsSession = prepareAndCommit(details, "Details")

        sessionStore.present(
            layerOrder = listOf(root.id, details.id),
            visibleEntryIds = setOf(details.id),
        )

        assertEquals(View.GONE, rootSession.container.visibility)
        assertEquals(View.VISIBLE, detailsSession.container.visibility)
        assertSame(
            detailsSession.container,
            sessionStore.hostView.getChildAt(sessionStore.hostView.childCount - 1),
        )
        assertSame(rootSession, sessionStore.sessionOrNull(root.id))

        sessionStore.remove(details.id)

        assertEquals(1, sessionStore.hostView.childCount)
        assertNull(sessionStore.sessionOrNull(details.id))
        assertNull(ownerStore.ownerOrNull(details.id))
        assertSame(rootSession, sessionStore.sessionOrNull(root.id))
    }

    @Test
    fun `only one destination candidate may be prepared`() {
        val first = sessionStore.prepare(
            entry = entry("first", "first"),
            localSnapshot = captureUiLocalSnapshot(),
            hostLifecycleState = NavHostLifecycleState.Created,
        ) {
            Text("First")
        }.readyCandidate()

        assertThrows<IllegalStateException> {
            sessionStore.prepare(
                entry = entry("second", "second"),
                localSnapshot = captureUiLocalSnapshot(),
                hostLifecycleState = NavHostLifecycleState.Created,
            ) {
                Text("Second")
            }
        }

        first.close()
        assertEquals(NavDestinationCandidateStatus.RolledBack, first.status)
    }

    @Test
    fun `visible page must be included in layer order`() {
        val entry = entry("visible", "visible")
        prepareAndCommit(entry, "Visible")

        assertThrows<IllegalStateException> {
            sessionStore.present(
                layerOrder = emptyList(),
                visibleEntryIds = setOf(entry.id),
            )
        }
    }

    private fun prepareAndCommit(
        entry: NavEntry,
        text: String,
    ): NavDestinationSession {
        val candidate = sessionStore.prepare(
            entry = entry,
            localSnapshot = captureUiLocalSnapshot(),
            hostLifecycleState = NavHostLifecycleState.Created,
        ) {
            Text(text)
        }.readyCandidate()
        candidate.stage()
        return candidate.commit()
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

    private fun <T> captureSnapshot(
        local: com.viewcompose.widget.core.UiLocal<T>,
        value: T,
    ): UiLocalSnapshot {
        var snapshot: UiLocalSnapshot? = null
        buildVNodeTree {
            ProvideLocal(local, value) {
                snapshot = captureUiLocalSnapshot()
            }
        }
        return checkNotNull(snapshot)
    }

    private fun NavDestinationPreparation.readyCandidate(): NavDestinationCandidate {
        return (this as NavDestinationPreparation.Ready).candidate
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
}
