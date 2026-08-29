package com.viewcompose.navigation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NavExecutionReducerTest {
    @Test
    fun `settled plan owns scene lifecycle presentation interaction and back conclusions`() {
        val home = entry("home")
        val details = entry("details")
        val stack = stackState(home, details)
        val panes = paneScene(home to NavPaneRole.Primary, details to NavPaneRole.Secondary)

        val plan = NavExecutionReducer.settled(
            currentLifecycleStates = emptyMap(),
            stackState = stack,
            paneScene = panes,
            hostState = NavHostLifecycleState.Resumed,
            presentedEntryIds = listOf(home.id),
            systemBackCommand = NavCommand.Pop,
        )

        assertEquals(NavExecutionPhase.Settled, plan.phase)
        assertEquals(listOf(details.id), plan.preparePresentationEntryIds)
        assertEquals(setOf(home.id, details.id), plan.inputEntryIds)
        assertEquals(setOf(home.id, details.id), plan.accessibilityEntryIds)
        assertEquals(NavCommand.Pop, plan.systemBackCommand)
        assertTrue(plan.ownsSystemBack)
        assertTrue(plan.scene.entries.all { it.transitionPhase == NavSceneTransitionPhase.Settled })
        assertTrue(plan.lifecycle.targetStates.values.all(NavEntryLifecycleState.Resumed::equals))
    }

    @Test
    fun `push transition prepares target and freezes one noninteractive lifecycle scene`() {
        val controller = controller("home", "details")
        val before = controller.snapshot()
        val transaction = controller.ready(NavCommand.Push(NavRoute("details")))
        val after = transaction.after

        val plan = NavExecutionReducer.transition(
            currentLifecycleStates = mapOf(before.top.id to NavEntryLifecycleState.Resumed),
            transaction = transaction,
            beforePaneScene = singlePane(before),
            afterPaneScene = singlePane(after),
            hostState = NavHostLifecycleState.Resumed,
            presentedEntryIds = listOf(before.top.id),
        )

        assertEquals(listOf(after.top.id), plan.preparePresentationEntryIds)
        assertEquals(emptySet<NavEntryId>(), plan.inputEntryIds)
        assertEquals(setOf(after.top.id), plan.accessibilityEntryIds)
        assertEquals(NavSceneTransitionPhase.Exiting, plan.scene[before.top.id]?.transitionPhase)
        assertEquals(NavSceneTransitionPhase.Entering, plan.scene[after.top.id]?.transitionPhase)
        assertEquals(NavEntryLifecycleState.Started, plan.lifecycle.targetStates[before.top.id])
        assertEquals(NavEntryLifecycleState.Started, plan.lifecycle.targetStates[after.top.id])
        assertEquals(setOf(before.top.id), plan.pauseRenderingEntryIds)
        assertEquals(listOf(after.top.id), plan.rollbackPresentationEntryIds)
        assertEquals(listOf(after.top.id), plan.rollbackOwnerEntryIds)
        transaction.rollback()
    }

    @Test
    fun `pop transition retains visible exit then emits terminal cleanup newest first`() {
        val controller = controller("home", "details", "support")
        controller.ready(NavCommand.Push(NavRoute("details"))).commit()
        controller.ready(NavCommand.Push(NavRoute("support"))).commit()
        val before = controller.snapshot()
        val transaction = controller.ready(NavCommand.Pop)
        val after = transaction.after

        val plan = NavExecutionReducer.transition(
            currentLifecycleStates = before.entries.associate { entry ->
                entry.id to if (entry == before.top) {
                    NavEntryLifecycleState.Resumed
                } else {
                    NavEntryLifecycleState.Created
                }
            },
            transaction = transaction,
            beforePaneScene = singlePane(before),
            afterPaneScene = singlePane(after),
            hostState = NavHostLifecycleState.Resumed,
            presentedEntryIds = before.entries.map(NavEntry::id),
        )

        assertEquals(before.top.id, plan.layerOrder.last())
        assertEquals(NavEntryPresence.Exiting, plan.scene[before.top.id]?.presence)
        assertEquals(NavEntryLifecycleState.Created, plan.lifecycle.targetStates[before.top.id])
        assertEquals(listOf(before.top.id), plan.terminalCleanupEntryIds)
        assertTrue(plan.disposeBeforeSceneEntryIds.isEmpty())
        transaction.rollback()
    }

    @Test
    fun `reset disposes hidden removals before scene and leaves visible exit for terminal cleanup`() {
        val controller = controller("home", "details", "replacement")
        controller.ready(NavCommand.Push(NavRoute("details"))).commit()
        val before = controller.snapshot()
        val transaction = controller.ready(NavCommand.Reset(NavRoute("replacement")))

        val plan = NavExecutionReducer.transition(
            currentLifecycleStates = before.entries.associate { entry ->
                entry.id to NavEntryLifecycleState.Created
            },
            transaction = transaction,
            beforePaneScene = singlePane(before),
            afterPaneScene = singlePane(transaction.after),
            hostState = NavHostLifecycleState.Resumed,
            presentedEntryIds = before.entries.map(NavEntry::id),
        )

        assertEquals(listOf(before.entries.first().id), plan.disposeBeforeSceneEntryIds)
        assertEquals(
            before.entries.map(NavEntry::id).asReversed(),
            plan.terminalCleanupEntryIds,
        )
        assertEquals(
            NavEntryLifecycleState.Destroyed,
            plan.lifecycle.targetStates[before.entries.first().id],
        )
        transaction.rollback()
    }

    @Test
    fun `predictive preview keeps committed stack and exposes only outgoing accessibility`() {
        val home = entry("home")
        val details = entry("details")
        val committed = stackState(home, details)
        val prospective = NavBackStackSnapshot(listOf(home))

        val plan = NavExecutionReducer.predictivePreview(
            currentLifecycleStates = mapOf(
                home.id to NavEntryLifecycleState.Created,
                details.id to NavEntryLifecycleState.Resumed,
            ),
            stackState = committed,
            prospectiveActiveStack = prospective,
            beforePaneScene = singlePane(committed.activeStack),
            afterPaneScene = singlePane(prospective),
            hostState = NavHostLifecycleState.Resumed,
            presentedEntryIds = listOf(details.id),
            systemBackCommand = NavCommand.Pop,
        )

        assertEquals(NavExecutionPhase.PredictivePreview, plan.phase)
        assertEquals(committed.activeStack, plan.before)
        assertEquals(prospective, plan.after)
        assertNull(plan.mutation)
        assertEquals(listOf(home.id), plan.preparePresentationEntryIds)
        assertEquals(setOf(details.id), plan.accessibilityEntryIds)
        assertEquals(NavCommand.Pop, plan.systemBackCommand)
        assertTrue(plan.inputEntryIds.isEmpty())
        assertTrue(plan.scene.entries.all { entry ->
            entry.visibility == NavSceneVisibility.Hidden ||
                entry.transitionPhase == NavSceneTransitionPhase.PredictivePreview
        })
        assertTrue(plan.terminalCleanupEntryIds.isEmpty())
    }

    @Test
    fun `hidden presentation eviction is deterministic and never evicts visible entries`() {
        val home = entry("home")
        val details = entry("details")
        val support = entry("support")
        val stack = stackState(home, details, support)

        val plan = NavExecutionReducer.settled(
            currentLifecycleStates = emptyMap(),
            stackState = stack,
            paneScene = singlePane(stack.activeStack),
            hostState = NavHostLifecycleState.Resumed,
            presentedEntryIds = listOf(home.id, details.id, support.id),
            hiddenPresentationRecency = listOf(home.id, details.id),
            maxRetainedHiddenPresentations = 1,
        )

        assertEquals(listOf(home.id), plan.evictPresentationEntryIds)
        assertEquals(listOf(details.id), plan.retainPresentationEntryIds)
        assertFalse(support.id in plan.evictPresentationEntryIds)
    }

    @Test
    fun `unbounded retention preserves every hidden presentation`() {
        val home = entry("home")
        val details = entry("details")
        val stack = stackState(home, details)

        val plan = NavExecutionReducer.settled(
            currentLifecycleStates = emptyMap(),
            stackState = stack,
            paneScene = singlePane(stack.activeStack),
            hostState = NavHostLifecycleState.Resumed,
            presentedEntryIds = listOf(home.id, details.id),
            hiddenPresentationRecency = listOf(home.id),
            maxRetainedHiddenPresentations = null,
        )

        assertTrue(plan.evictPresentationEntryIds.isEmpty())
        assertEquals(listOf(home.id), plan.retainPresentationEntryIds)
    }

    @Test
    fun `reconcile preserves scene identity while changing lifecycle and retention effects`() {
        val home = entry("home")
        val details = entry("details")
        val stack = stackState(home, details)
        val initial = NavExecutionReducer.settled(
            currentLifecycleStates = emptyMap(),
            stackState = stack,
            paneScene = singlePane(stack.activeStack),
            hostState = NavHostLifecycleState.Resumed,
            presentedEntryIds = listOf(home.id, details.id),
            hiddenPresentationRecency = listOf(home.id),
            maxRetainedHiddenPresentations = null,
        )

        val reconciled = NavExecutionReducer.reconcile(
            plan = initial,
            currentLifecycleStates = initial.lifecycle.targetStates,
            hostState = NavHostLifecycleState.Created,
            presentedEntryIds = listOf(home.id, details.id),
            hiddenPresentationRecency = listOf(home.id),
            maxRetainedHiddenPresentations = 0,
        )

        assertEquals(initial.scene, reconciled.scene)
        assertEquals(initial.before, reconciled.before)
        assertEquals(initial.after, reconciled.after)
        assertEquals(listOf(home.id), reconciled.evictPresentationEntryIds)
        assertTrue(
            reconciled.lifecycle.targetStates.values.all(
                NavEntryLifecycleState.Created::equals,
            ),
        )
    }

    @Test
    fun `transition computes system back ownership from candidate stack not previous stack`() {
        val controller = controller("home", "details", "login")
        val push = controller.ready(NavCommand.Push(NavRoute("details")))
        val pushedPlan = NavExecutionReducer.transition(
            currentLifecycleStates = emptyMap(),
            transaction = push,
            beforePaneScene = singlePane(push.before),
            afterPaneScene = singlePane(push.after),
            hostState = NavHostLifecycleState.Resumed,
        )
        assertEquals(NavCommand.Pop, pushedPlan.systemBackCommand)
        assertTrue(pushedPlan.ownsSystemBack)
        push.commit()

        val reset = controller.ready(NavCommand.Reset(NavRoute("login")))
        val resetPlan = NavExecutionReducer.transition(
            currentLifecycleStates = pushedPlan.lifecycle.targetStates,
            transaction = reset,
            beforePaneScene = singlePane(reset.before),
            afterPaneScene = singlePane(reset.after),
            hostState = NavHostLifecycleState.Resumed,
        )
        assertNull(resetPlan.systemBackCommand)
        assertFalse(resetPlan.ownsSystemBack)
        reset.rollback()
    }

    @Test
    fun `plan collections are immutable snapshots`() {
        val home = entry("home")
        val stack = stackState(home)
        val presented = mutableListOf(home.id)

        val plan = NavExecutionReducer.settled(
            currentLifecycleStates = emptyMap(),
            stackState = stack,
            paneScene = singlePane(stack.activeStack),
            hostState = NavHostLifecycleState.Resumed,
            presentedEntryIds = presented,
        )
        presented.clear()

        assertEquals(listOf(home.id), plan.layerOrder)
        assertThrows<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (plan.layerOrder as MutableList<NavEntryId>).clear()
        }
        assertThrows<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (plan.inputEntryIds as MutableSet<NavEntryId>).clear()
        }
    }

    @Test
    fun `invalid pane presentation and terminal transaction inputs fail before effects`() {
        val home = entry("home")
        val unknown = entry("unknown")
        val stack = stackState(home)

        assertThrows<IllegalArgumentException> {
            NavExecutionReducer.settled(
                currentLifecycleStates = emptyMap(),
                stackState = stack,
                paneScene = paneScene(unknown to NavPaneRole.Primary),
                hostState = NavHostLifecycleState.Resumed,
            )
        }
        assertThrows<IllegalArgumentException> {
            NavExecutionReducer.settled(
                currentLifecycleStates = emptyMap(),
                stackState = stack,
                paneScene = singlePane(stack.activeStack),
                hostState = NavHostLifecycleState.Resumed,
                presentedEntryIds = listOf(home.id, home.id),
            )
        }
        assertThrows<IllegalArgumentException> {
            NavExecutionReducer.settled(
                currentLifecycleStates = emptyMap(),
                stackState = stack,
                paneScene = singlePane(stack.activeStack),
                hostState = NavHostLifecycleState.Resumed,
                systemBackCommand = NavCommand.Push(NavRoute("unknown")),
            )
        }

        val details = entry("details")
        val previewState = stackState(home, details)
        assertThrows<IllegalArgumentException> {
            NavExecutionReducer.predictivePreview(
                currentLifecycleStates = emptyMap(),
                stackState = previewState,
                prospectiveActiveStack = previewState.activeStack,
                beforePaneScene = singlePane(previewState.activeStack),
                afterPaneScene = singlePane(previewState.activeStack),
                hostState = NavHostLifecycleState.Resumed,
                systemBackCommand = NavCommand.Pop,
            )
        }

        val controller = controller("home", "details")
        val transaction = controller.ready(NavCommand.Push(NavRoute("details")))
        transaction.commit()
        assertThrows<IllegalStateException> {
            NavExecutionReducer.transition(
                currentLifecycleStates = emptyMap(),
                transaction = transaction,
                beforePaneScene = singlePane(transaction.before),
                afterPaneScene = singlePane(transaction.after),
                hostState = NavHostLifecycleState.Resumed,
            )
        }
    }

    private fun controller(vararg routes: String): NavBackStackController {
        var nextId = 0
        return NavBackStackController.create(
            startDestination = NavRoute(checkNotNull(routes.firstOrNull())),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId("entry-${++nextId}")
            },
        )
    }

    private fun NavBackStackController.ready(command: NavCommand): NavTransaction {
        return (prepare(command) as NavPreparation.Ready).transaction
    }

    private fun entry(route: String): NavEntry {
        return NavEntry(
            id = NavEntryId(route),
            route = NavRoute(route),
        )
    }

    private fun stackState(vararg entries: NavEntry): NavStackSetSnapshot {
        return NavStackSetSnapshot(
            activeStackId = NavStackId.Default,
            stacks = mapOf(
                NavStackId.Default to NavBackStackSnapshot(entries.toList()),
            ),
        )
    }

    private fun singlePane(snapshot: NavBackStackSnapshot): NavPaneScene {
        return paneScene(snapshot.top to NavPaneRole.Primary)
    }

    private fun paneScene(vararg panes: Pair<NavEntry, NavPaneRole>): NavPaneScene {
        return NavPaneScene(
            panes.map { (entry, role) -> NavPane(role, entry.id) },
        )
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
