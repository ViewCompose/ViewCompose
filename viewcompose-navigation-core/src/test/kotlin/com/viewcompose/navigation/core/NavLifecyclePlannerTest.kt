package com.viewcompose.navigation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NavLifecyclePlannerTest {
    @Test
    fun `settled scene resumes interactive top and keeps hidden entries created`() {
        val root = entry("root")
        val details = entry("details")

        val plan = NavLifecyclePlanner.plan(
            currentStates = emptyMap(),
            entries = listOf(root, details),
            scene = NavScene(
                listOf(
                    hidden(root),
                    settled(details, NavPaneRole.Primary),
                ),
            ),
            hostState = NavHostLifecycleState.Resumed,
        )

        assertEquals(NavEntryLifecycleState.Created, plan.targetStates[root.id])
        assertEquals(NavEntryLifecycleState.Resumed, plan.targetStates[details.id])
        assertEquals(1, plan.targetStates.values.count(NavEntryLifecycleState.Resumed::equals))
    }

    @Test
    fun `active transition scene caps every visible participant at started`() {
        val root = entry("root")
        val details = entry("details")

        val plan = NavLifecyclePlanner.plan(
            currentStates = mapOf(root.id to NavEntryLifecycleState.Resumed),
            entries = listOf(root, details),
            scene = NavScene(
                listOf(
                    transitioning(root, NavSceneTransitionPhase.Exiting),
                    transitioning(details, NavSceneTransitionPhase.Entering),
                ),
            ),
            hostState = NavHostLifecycleState.Resumed,
        )

        assertEquals(NavEntryLifecycleState.Started, plan.targetStates[root.id])
        assertEquals(NavEntryLifecycleState.Started, plan.targetStates[details.id])
        assertFalse(plan.targetStates.values.contains(NavEntryLifecycleState.Resumed))
    }

    @Test
    fun `settled multi-pane scene can resume multiple interactive destinations`() {
        val list = entry("list")
        val details = entry("details")
        val support = entry("support")

        val plan = NavLifecyclePlanner.plan(
            currentStates = emptyMap(),
            entries = listOf(list, details, support),
            scene = NavScene(
                listOf(
                    settled(list, NavPaneRole.Primary),
                    settled(details, NavPaneRole.Secondary),
                    settled(support, NavPaneRole.Tertiary),
                ),
            ),
            hostState = NavHostLifecycleState.Resumed,
        )

        assertTrue(plan.targetStates.values.all(NavEntryLifecycleState.Resumed::equals))
    }

    @Test
    fun `host lifecycle caps every scene and entry target`() {
        val root = entry("root")
        val scene = NavScene(listOf(settled(root, NavPaneRole.Primary)))

        val created = NavLifecyclePlanner.plan(
            currentStates = emptyMap(),
            entries = listOf(root),
            scene = scene,
            hostState = NavHostLifecycleState.Created,
        )
        val started = NavLifecyclePlanner.plan(
            currentStates = created.targetStates,
            entries = listOf(root),
            scene = scene,
            hostState = NavHostLifecycleState.Started,
        )

        assertEquals(NavEntryLifecycleState.Created, created.targetStates[root.id])
        assertEquals(NavEntryLifecycleState.Started, started.targetStates[root.id])
    }

    @Test
    fun `removed owner is destroyed before revealed destination resumes`() {
        val root = entry("root")
        val details = entry("details")

        val plan = NavLifecyclePlanner.plan(
            currentStates = linkedMapOf(
                root.id to NavEntryLifecycleState.Created,
                details.id to NavEntryLifecycleState.Resumed,
            ),
            entries = listOf(root),
            scene = NavScene(listOf(settled(root, NavPaneRole.Primary))),
            hostState = NavHostLifecycleState.Resumed,
        )

        assertEquals(
            NavLifecycleTransition(
                entryId = details.id,
                from = NavEntryLifecycleState.Resumed,
                to = NavEntryLifecycleState.Destroyed,
            ),
            plan.transitions.first(),
        )
        assertEquals(
            NavLifecycleTransition(
                entryId = root.id,
                from = NavEntryLifecycleState.Created,
                to = NavEntryLifecycleState.Resumed,
            ),
            plan.transitions.last(),
        )
    }

    @Test
    fun `popped exiting destination is created until its presentation leaves`() {
        val root = entry("root")
        val details = entry("details")

        val plan = NavLifecyclePlanner.plan(
            currentStates = mapOf(
                root.id to NavEntryLifecycleState.Created,
                details.id to NavEntryLifecycleState.Resumed,
            ),
            entries = listOf(root, details),
            scene = NavScene(
                listOf(
                    transitioning(root, NavSceneTransitionPhase.Entering),
                    transitioning(
                        entry = details,
                        phase = NavSceneTransitionPhase.Exiting,
                        presence = NavEntryPresence.Exiting,
                    ),
                ),
            ),
            hostState = NavHostLifecycleState.Resumed,
        )

        assertEquals(NavEntryLifecycleState.Started, plan.targetStates[root.id])
        assertEquals(NavEntryLifecycleState.Created, plan.targetStates[details.id])
    }

    @Test
    fun `host destruction destroys destinations and graph owners`() {
        val graph = NavGraphEntry(NavEntryId("graph"), NavRoute("graph"))
        val root = entry("root", graph)
        val details = entry("details", graph)

        val plan = NavLifecyclePlanner.plan(
            currentStates = mapOf(
                graph.id to NavEntryLifecycleState.Resumed,
                root.id to NavEntryLifecycleState.Created,
                details.id to NavEntryLifecycleState.Resumed,
            ),
            entries = listOf(root, details),
            scene = NavScene(listOf(hidden(root), settled(details, NavPaneRole.Primary))),
            hostState = NavHostLifecycleState.Destroyed,
        )

        assertTrue(plan.targetStates.values.all(NavEntryLifecycleState.Destroyed::equals))
    }

    @Test
    fun `destroyed destination or graph identity cannot be resurrected`() {
        val graph = NavGraphEntry(NavEntryId("graph"), NavRoute("graph"))
        val root = entry("root", graph)
        val scene = NavScene(listOf(settled(root, NavPaneRole.Primary)))

        assertThrows<IllegalArgumentException> {
            NavLifecyclePlanner.plan(
                currentStates = mapOf(root.id to NavEntryLifecycleState.Destroyed),
                entries = listOf(root),
                scene = scene,
                hostState = NavHostLifecycleState.Resumed,
            )
        }
        assertThrows<IllegalArgumentException> {
            NavLifecyclePlanner.plan(
                currentStates = mapOf(graph.id to NavEntryLifecycleState.Destroyed),
                entries = listOf(root),
                scene = scene,
                hostState = NavHostLifecycleState.Resumed,
            )
        }
    }

    @Test
    fun `scene and owned destination identities must match exactly`() {
        val root = entry("root")
        val missing = entry("missing")

        assertThrows<IllegalArgumentException> {
            NavLifecyclePlanner.plan(
                currentStates = emptyMap(),
                entries = listOf(root),
                scene = NavScene(listOf(settled(missing, NavPaneRole.Primary))),
                hostState = NavHostLifecycleState.Resumed,
            )
        }
        assertThrows<IllegalArgumentException> {
            NavLifecyclePlanner.plan(
                currentStates = emptyMap(),
                entries = listOf(root, root),
                scene = NavScene(listOf(settled(root, NavPaneRole.Primary))),
                hostState = NavHostLifecycleState.Resumed,
            )
        }
    }

    @Test
    fun `graph lifecycle aggregates the highest effective descendant target`() {
        val rootGraph = NavGraphEntry(NavEntryId("root-graph"), NavRoute("root-graph"))
        val accountGraph = NavGraphEntry(NavEntryId("account-graph"), NavRoute("account-graph"))
        val home = entry("home", rootGraph)
        val profile = entry("profile", rootGraph, accountGraph)

        val plan = NavLifecyclePlanner.plan(
            currentStates = emptyMap(),
            entries = listOf(home, profile),
            scene = NavScene(
                listOf(
                    hidden(home),
                    settled(profile, NavPaneRole.Primary),
                ),
            ),
            hostState = NavHostLifecycleState.Resumed,
        )

        assertEquals(NavEntryLifecycleState.Created, plan.targetStates[home.id])
        assertEquals(NavEntryLifecycleState.Resumed, plan.targetStates[profile.id])
        assertEquals(NavEntryLifecycleState.Resumed, plan.targetStates[accountGraph.id])
        assertEquals(NavEntryLifecycleState.Resumed, plan.targetStates[rootGraph.id])
    }

    @Test
    fun `shared graph identity and depth must remain stable across destinations`() {
        val graphId = NavEntryId("graph")
        val firstGraph = NavGraphEntry(graphId, NavRoute("first"))
        val secondGraph = NavGraphEntry(graphId, NavRoute("second"))
        val first = entry("first", firstGraph)
        val second = entry("second", secondGraph)

        assertThrows<IllegalArgumentException> {
            NavLifecyclePlanner.plan(
                currentStates = emptyMap(),
                entries = listOf(first, second),
                scene = NavScene(
                    listOf(
                        hidden(first),
                        settled(second, NavPaneRole.Primary),
                    ),
                ),
                hostState = NavHostLifecycleState.Resumed,
            )
        }
    }

    @Test
    fun `accepted scene matrix derives independent scene and entry caps`() {
        val cases = listOf(
            prepared("prepared") to
                (NavEntryLifecycleState.Created to NavEntryLifecycleState.Created),
            hidden(entry("hidden")) to
                (NavEntryLifecycleState.Created to NavEntryLifecycleState.Resumed),
            settled(entry("settled"), NavPaneRole.Primary) to
                (NavEntryLifecycleState.Resumed to NavEntryLifecycleState.Resumed),
            transitioning(entry("entering"), NavSceneTransitionPhase.Entering) to
                (NavEntryLifecycleState.Started to NavEntryLifecycleState.Resumed),
            covered(entry("covered"), NavPaneRole.Primary) to
                (NavEntryLifecycleState.Started to NavEntryLifecycleState.Resumed),
            overlay(entry("overlay")) to
                (NavEntryLifecycleState.Resumed to NavEntryLifecycleState.Resumed),
            transitioning(
                entry = entry("exiting"),
                phase = NavSceneTransitionPhase.Exiting,
                presence = NavEntryPresence.Exiting,
            ) to (NavEntryLifecycleState.Started to NavEntryLifecycleState.Created),
            removed("removed") to
                (NavEntryLifecycleState.Destroyed to NavEntryLifecycleState.Destroyed),
        )

        cases.forEach { (projection, caps) ->
            assertEquals(caps.first, projection.sceneLifecycleCap)
            assertEquals(caps.second, projection.entryLifecycleCap)
        }
    }

    @Test
    fun `every valid projection is capped by host scene and entry state`() {
        val projections = listOf(
            prepared("prepared"),
            hidden(entry("hidden")),
            settled(entry("settled"), NavPaneRole.Primary),
            transitioning(entry("entering"), NavSceneTransitionPhase.Entering),
            covered(entry("covered"), NavPaneRole.Primary),
            overlay(entry("overlay")),
            transitioning(
                entry("exiting"),
                NavSceneTransitionPhase.Exiting,
                NavEntryPresence.Exiting,
            ),
        )

        NavHostLifecycleState.entries.forEach { hostState ->
            projections.forEach { projection ->
                val owner = entry(projection.entryId.value)
                val plan = NavLifecyclePlanner.plan(
                    currentStates = emptyMap(),
                    entries = listOf(owner),
                    scene = NavScene(listOf(projection)),
                    hostState = hostState,
                )
                val target = checkNotNull(plan.targetStates[owner.id])
                assertTrue(target.rank() <= projection.sceneLifecycleCap.rank())
                assertTrue(target.rank() <= projection.entryLifecycleCap.rank())
                assertTrue(target.rank() <= hostState.rank())
            }
        }
    }

    @Test
    fun `scene rejects contradictory roles and active transition interaction`() {
        val root = entry("root")
        val details = entry("details")

        assertThrows<IllegalArgumentException> {
            NavSceneEntry(
                entryId = root.id,
                presence = NavEntryPresence.Retained,
                visibility = NavSceneVisibility.Hidden,
                interaction = NavSceneInteraction.Interactive,
                transitionPhase = NavSceneTransitionPhase.Settled,
                paneRole = null,
            )
        }
        assertThrows<IllegalArgumentException> {
            NavScene(
                listOf(
                    settled(root, NavPaneRole.Primary),
                    transitioning(details, NavSceneTransitionPhase.Entering),
                ),
            )
        }
        assertThrows<IllegalArgumentException> {
            NavScene(
                listOf(
                    overlay(root),
                    hidden(details),
                ),
            )
        }
    }

    @Test
    fun `scene copies its input and exposes immutable collections`() {
        val root = entry("root")
        val mutableEntries = mutableListOf(settled(root, NavPaneRole.Primary))
        val scene = NavScene(mutableEntries)

        mutableEntries.clear()

        assertEquals(1, scene.entries.size)
        assertNotSame(mutableEntries, scene.entries)
        assertThrows<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (scene.entries as MutableList<NavSceneEntry>).clear()
        }
        assertThrows<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (scene.entryIds as MutableSet<NavEntryId>).clear()
        }
    }

    private fun entry(
        id: String,
        vararg graphs: NavGraphEntry,
    ): NavEntry {
        return NavEntry(
            id = NavEntryId(id),
            route = NavRoute(id),
            graphEntries = graphs.toList(),
        )
    }

    private fun hidden(entry: NavEntry): NavSceneEntry {
        return NavSceneEntry(
            entryId = entry.id,
            presence = NavEntryPresence.Retained,
            visibility = NavSceneVisibility.Hidden,
            interaction = NavSceneInteraction.NonInteractive,
            transitionPhase = NavSceneTransitionPhase.Settled,
            paneRole = null,
        )
    }

    private fun settled(
        entry: NavEntry,
        paneRole: NavPaneRole,
    ): NavSceneEntry {
        return NavSceneEntry(
            entryId = entry.id,
            presence = NavEntryPresence.Retained,
            visibility = NavSceneVisibility.Visible,
            interaction = NavSceneInteraction.Interactive,
            transitionPhase = NavSceneTransitionPhase.Settled,
            paneRole = paneRole,
        )
    }

    private fun transitioning(
        entry: NavEntry,
        phase: NavSceneTransitionPhase,
        presence: NavEntryPresence = NavEntryPresence.Retained,
    ): NavSceneEntry {
        return NavSceneEntry(
            entryId = entry.id,
            presence = presence,
            visibility = NavSceneVisibility.Visible,
            interaction = NavSceneInteraction.NonInteractive,
            transitionPhase = phase,
            paneRole = NavPaneRole.Primary,
        )
    }

    private fun covered(
        entry: NavEntry,
        paneRole: NavPaneRole,
    ): NavSceneEntry {
        return NavSceneEntry(
            entryId = entry.id,
            presence = NavEntryPresence.Retained,
            visibility = NavSceneVisibility.Covered,
            interaction = NavSceneInteraction.NonInteractive,
            transitionPhase = NavSceneTransitionPhase.Settled,
            paneRole = paneRole,
        )
    }

    private fun overlay(entry: NavEntry): NavSceneEntry {
        return NavSceneEntry(
            entryId = entry.id,
            presence = NavEntryPresence.Retained,
            visibility = NavSceneVisibility.Visible,
            interaction = NavSceneInteraction.Interactive,
            transitionPhase = NavSceneTransitionPhase.Settled,
            paneRole = null,
            layerRole = NavSceneLayerRole.Overlay,
        )
    }

    private fun prepared(id: String): NavSceneEntry {
        return NavSceneEntry(
            entryId = NavEntryId(id),
            presence = NavEntryPresence.Prepared,
            visibility = NavSceneVisibility.Hidden,
            interaction = NavSceneInteraction.NonInteractive,
            transitionPhase = NavSceneTransitionPhase.Prepared,
            paneRole = null,
        )
    }

    private fun removed(id: String): NavSceneEntry {
        return NavSceneEntry(
            entryId = NavEntryId(id),
            presence = NavEntryPresence.Removed,
            visibility = NavSceneVisibility.Hidden,
            interaction = NavSceneInteraction.NonInteractive,
            transitionPhase = NavSceneTransitionPhase.Settled,
            paneRole = null,
        )
    }

    private fun NavEntryLifecycleState.rank(): Int {
        return when (this) {
            NavEntryLifecycleState.Destroyed -> -1
            NavEntryLifecycleState.Initialized -> 0
            NavEntryLifecycleState.Created -> 1
            NavEntryLifecycleState.Started -> 2
            NavEntryLifecycleState.Resumed -> 3
        }
    }

    private fun NavHostLifecycleState.rank(): Int {
        return when (this) {
            NavHostLifecycleState.Destroyed -> -1
            NavHostLifecycleState.Initialized -> 0
            NavHostLifecycleState.Created -> 1
            NavHostLifecycleState.Started -> 2
            NavHostLifecycleState.Resumed -> 3
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
