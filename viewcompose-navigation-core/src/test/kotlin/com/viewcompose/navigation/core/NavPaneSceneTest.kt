package com.viewcompose.navigation.core

/*
 * 测试职责：覆盖 navigation core 中的 Nav Pane Scene 行为，防止导航契约在后续重构中回退。
 * Test responsibility: covers Nav Pane Scene behavior in navigation core and guards navigation contracts against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NavPaneSceneTest {
    @Test
    fun `back-stack strategy assigns the newest entries to contiguous panes`() {
        val snapshot = snapshot("home", "list", "details", "supporting")

        val single = NavPaneStrategies.BackStack.calculateValidated(
            snapshot = snapshot,
            maxPaneCount = 1,
        )
        val dual = NavPaneStrategies.BackStack.calculateValidated(
            snapshot = snapshot,
            maxPaneCount = 2,
        )
        val triple = NavPaneStrategies.BackStack.calculateValidated(
            snapshot = snapshot,
            maxPaneCount = 3,
        )

        assertEquals(
            listOf(NavPane(NavPaneRole.Primary, NavEntryId("supporting"))),
            single.panes,
        )
        assertEquals(
            listOf(
                NavPane(NavPaneRole.Primary, NavEntryId("details")),
                NavPane(NavPaneRole.Secondary, NavEntryId("supporting")),
            ),
            dual.panes,
        )
        assertEquals(
            listOf(
                NavPane(NavPaneRole.Primary, NavEntryId("list")),
                NavPane(NavPaneRole.Secondary, NavEntryId("details")),
                NavPane(NavPaneRole.Tertiary, NavEntryId("supporting")),
            ),
            triple.panes,
        )
    }

    @Test
    fun `single strategy always exposes only the active top`() {
        val snapshot = snapshot("home", "details")

        val scene = NavPaneStrategies.Single.calculateValidated(
            snapshot = snapshot,
            maxPaneCount = 3,
        )

        assertEquals(
            listOf(NavPane(NavPaneRole.Primary, snapshot.top.id)),
            scene.panes,
        )
        assertSame(scene.visibleEntryIds, scene.interactiveEntryIds)
    }

    @Test
    fun `pane scene copies its input and exposes immutable collections`() {
        val mutablePanes = mutableListOf(
            NavPane(NavPaneRole.Primary, NavEntryId("home")),
        )
        val scene = NavPaneScene(mutablePanes)

        mutablePanes += NavPane(NavPaneRole.Secondary, NavEntryId("details"))

        assertEquals(1, scene.panes.size)
        assertNotSame(mutablePanes, scene.panes)
        assertThrows<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (scene.panes as MutableList<NavPane>).clear()
        }
        assertThrows<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (scene.visibleEntryIds as MutableSet<NavEntryId>).clear()
        }
    }

    @Test
    fun `invalid scenes and custom strategy output fail before host publication`() {
        assertThrows<IllegalArgumentException> {
            NavPaneScene(emptyList())
        }
        assertThrows<IllegalArgumentException> {
            NavPaneScene(
                listOf(
                    NavPane(NavPaneRole.Secondary, NavEntryId("details")),
                ),
            )
        }
        assertThrows<IllegalArgumentException> {
            NavPaneScene(
                listOf(
                    NavPane(NavPaneRole.Primary, NavEntryId("home")),
                    NavPane(NavPaneRole.Secondary, NavEntryId("home")),
                ),
            )
        }

        val snapshot = snapshot("home", "details")
        val unknownEntry = NavPaneStrategy { _, _ ->
            NavPaneScene(
                listOf(
                    NavPane(NavPaneRole.Primary, NavEntryId("unknown")),
                ),
            )
        }
        val missingTop = NavPaneStrategy { current, _ ->
            NavPaneScene(
                listOf(
                    NavPane(NavPaneRole.Primary, current.entries.first().id),
                ),
            )
        }

        assertThrows<IllegalArgumentException> {
            unknownEntry.calculateValidated(snapshot, 1)
        }
        assertThrows<IllegalArgumentException> {
            missingTop.calculateValidated(snapshot, 1)
        }
        assertTrue(snapshot.top.id !in missingTop.calculate(snapshot, 1).visibleEntryIds)
    }

    private fun snapshot(vararg routes: String): NavBackStackSnapshot {
        return NavBackStackSnapshot(
            routes.map { route ->
                NavEntry(
                    id = NavEntryId(route),
                    route = NavRoute(route),
                )
            },
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
