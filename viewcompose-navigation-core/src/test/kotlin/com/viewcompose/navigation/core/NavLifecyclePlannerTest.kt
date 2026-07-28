package com.viewcompose.navigation.core

/*
 * 测试职责：覆盖 navigation core 中的 Nav Lifecycle Planner 行为，防止导航契约在后续重构中回退。
 * Test responsibility: covers Nav Lifecycle Planner behavior in navigation core and guards navigation contracts against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NavLifecyclePlannerTest {
    @Test
    fun `settled stack resumes only interactive top and keeps hidden entries created`() {
        val root = NavEntryId("root")
        val details = NavEntryId("details")

        val plan = NavLifecyclePlanner.plan(
            currentStates = emptyMap(),
            retainedEntryIds = listOf(root, details),
            visibleEntryIds = setOf(details),
            interactiveEntryId = details,
            hostState = NavHostLifecycleState.Resumed,
        )

        assertEquals(NavEntryLifecycleState.Created, plan.targetStates[root])
        assertEquals(NavEntryLifecycleState.Resumed, plan.targetStates[details])
        assertEquals(1, plan.targetStates.values.count { it == NavEntryLifecycleState.Resumed })
    }

    @Test
    fun `transition downgrades old interactive entry before upgrading new one`() {
        val root = NavEntryId("root")
        val details = NavEntryId("details")

        val plan = NavLifecyclePlanner.plan(
            currentStates = mapOf(
                root to NavEntryLifecycleState.Resumed,
            ),
            retainedEntryIds = listOf(root, details),
            visibleEntryIds = setOf(root, details),
            interactiveEntryId = details,
            hostState = NavHostLifecycleState.Resumed,
        )

        assertEquals(
            listOf(
                NavLifecycleTransition(
                    entryId = root,
                    from = NavEntryLifecycleState.Resumed,
                    to = NavEntryLifecycleState.Started,
                ),
                NavLifecycleTransition(
                    entryId = details,
                    from = NavEntryLifecycleState.Initialized,
                    to = NavEntryLifecycleState.Resumed,
                ),
            ),
            plan.transitions,
        )
    }

    @Test
    fun `multiple interactive owners can share the resumed lifecycle`() {
        val appGraph = NavEntryId("app-graph")
        val accountGraph = NavEntryId("account-graph")
        val profile = NavEntryId("profile")

        val plan = NavLifecyclePlanner.plan(
            currentStates = emptyMap(),
            retainedEntryIds = listOf(appGraph, accountGraph, profile),
            visibleEntryIds = setOf(appGraph, accountGraph, profile),
            interactiveEntryIds = setOf(appGraph, accountGraph, profile),
            hostState = NavHostLifecycleState.Resumed,
        )

        assertTrue(
            plan.targetStates.values.all { state ->
                state == NavEntryLifecycleState.Resumed
            },
        )
    }

    @Test
    fun `host lifecycle caps destination lifecycle`() {
        val root = NavEntryId("root")

        val created = NavLifecyclePlanner.plan(
            currentStates = emptyMap(),
            retainedEntryIds = listOf(root),
            visibleEntryIds = setOf(root),
            interactiveEntryId = root,
            hostState = NavHostLifecycleState.Created,
        )
        val started = NavLifecyclePlanner.plan(
            currentStates = created.targetStates,
            retainedEntryIds = listOf(root),
            visibleEntryIds = setOf(root),
            interactiveEntryId = root,
            hostState = NavHostLifecycleState.Started,
        )

        assertEquals(NavEntryLifecycleState.Created, created.targetStates[root])
        assertEquals(NavEntryLifecycleState.Started, started.targetStates[root])
    }

    @Test
    fun `removed entry is destroyed before revealed entry resumes`() {
        val root = NavEntryId("root")
        val details = NavEntryId("details")

        val plan = NavLifecyclePlanner.plan(
            currentStates = linkedMapOf(
                root to NavEntryLifecycleState.Created,
                details to NavEntryLifecycleState.Resumed,
            ),
            retainedEntryIds = listOf(root),
            visibleEntryIds = setOf(root),
            interactiveEntryId = root,
            hostState = NavHostLifecycleState.Resumed,
        )

        assertEquals(
            NavLifecycleTransition(
                entryId = details,
                from = NavEntryLifecycleState.Resumed,
                to = NavEntryLifecycleState.Destroyed,
            ),
            plan.transitions.first(),
        )
        assertEquals(
            NavLifecycleTransition(
                entryId = root,
                from = NavEntryLifecycleState.Created,
                to = NavEntryLifecycleState.Resumed,
            ),
            plan.transitions.last(),
        )
    }

    @Test
    fun `host destruction destroys every retained destination`() {
        val root = NavEntryId("root")
        val details = NavEntryId("details")

        val plan = NavLifecyclePlanner.plan(
            currentStates = mapOf(
                root to NavEntryLifecycleState.Created,
                details to NavEntryLifecycleState.Resumed,
            ),
            retainedEntryIds = listOf(root, details),
            visibleEntryIds = setOf(details),
            interactiveEntryId = details,
            hostState = NavHostLifecycleState.Destroyed,
        )

        assertTrue(plan.targetStates.values.all { it == NavEntryLifecycleState.Destroyed })
    }

    @Test
    fun `destroyed destination cannot be resurrected`() {
        val root = NavEntryId("root")

        assertThrows<IllegalArgumentException> {
            NavLifecyclePlanner.plan(
                currentStates = mapOf(
                    root to NavEntryLifecycleState.Destroyed,
                ),
                retainedEntryIds = listOf(root),
                visibleEntryIds = setOf(root),
                interactiveEntryId = root,
                hostState = NavHostLifecycleState.Resumed,
            )
        }
    }

    @Test
    fun `visibility and interactive ownership invariants fail fast`() {
        val root = NavEntryId("root")
        val missing = NavEntryId("missing")

        assertThrows<IllegalArgumentException> {
            NavLifecyclePlanner.plan(
                currentStates = emptyMap(),
                retainedEntryIds = listOf(root),
                visibleEntryIds = setOf(missing),
                interactiveEntryId = null,
                hostState = NavHostLifecycleState.Resumed,
            )
        }
        assertThrows<IllegalArgumentException> {
            NavLifecyclePlanner.plan(
                currentStates = emptyMap(),
                retainedEntryIds = listOf(root),
                visibleEntryIds = setOf(root),
                interactiveEntryId = missing,
                hostState = NavHostLifecycleState.Resumed,
            )
        }
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
