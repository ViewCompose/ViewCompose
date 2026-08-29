package com.viewcompose.navigation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NavSceneLayoutTest {
    @Test
    fun `trailing overlay strategy delegates content projection and preserves stack order`() {
        val home = entry("home")
        val details = entry("details")
        val dialog = entry("dialog")
        val confirmation = entry("confirmation")
        val snapshot = NavBackStackSnapshot(listOf(home, details, dialog, confirmation))

        val layout = resolveNavSceneLayout(
            snapshot = snapshot,
            maxPaneCount = 2,
            sceneStrategies = listOf(
                NavSceneStrategies.trailingOverlays { entry ->
                    entry.route.name == "dialog" || entry.route.name == "confirmation"
                },
            ),
            paneStrategy = NavPaneStrategies.BackStack,
        )

        assertEquals(listOf(home.id, details.id), layout.contentPaneScene.panes.map(NavPane::entryId))
        assertEquals(listOf(dialog.id, confirmation.id), layout.overlayEntryIds)
        assertEquals(setOf(confirmation.id), layout.interactiveEntryIds)
        assertEquals(
            listOf(home.id, details.id, dialog.id, confirmation.id),
            layout.visibleEntryIds.toList(),
        )
    }

    @Test
    fun `ordered strategies stop after the first nonnull result`() {
        val home = entry("home")
        val dialog = entry("dialog")
        val snapshot = NavBackStackSnapshot(listOf(home, dialog))
        var laterInvoked = false
        val selected = NavSceneLayout(
            contentPaneScene = paneScene(home),
            overlayEntryIds = listOf(dialog.id),
        )

        val layout = resolveNavSceneLayout(
            snapshot = snapshot,
            maxPaneCount = 1,
            sceneStrategies = listOf(
                NavSceneStrategy { null },
                NavSceneStrategy { selected },
                NavSceneStrategy {
                    laterInvoked = true
                    error("A later strategy must not run.")
                },
            ),
        )

        assertSame(selected, layout)
        assertTrue(!laterInvoked)
    }

    @Test
    fun `content fallback applies when no scene strategy matches`() {
        val home = entry("home")
        val details = entry("details")
        val snapshot = NavBackStackSnapshot(listOf(home, details))

        val layout = resolveNavSceneLayout(
            snapshot = snapshot,
            maxPaneCount = 2,
            sceneStrategies = listOf(NavSceneStrategy { null }),
            paneStrategy = NavPaneStrategies.BackStack,
        )

        assertEquals(listOf(home.id, details.id), layout.contentPaneScene.panes.map(NavPane::entryId))
        assertTrue(layout.overlayEntryIds.isEmpty())
        assertEquals(setOf(home.id, details.id), layout.interactiveEntryIds)
    }

    @Test
    fun `invalid overlay membership and all-overlay stacks fail closed`() {
        val home = entry("home")
        val details = entry("details")
        val dialog = entry("dialog")
        val snapshot = NavBackStackSnapshot(listOf(home, details, dialog))

        assertThrows<IllegalArgumentException> {
            NavSceneLayout(
                contentPaneScene = paneScene(home),
                overlayEntryIds = listOf(home.id),
            )
        }
        assertThrows<IllegalArgumentException> {
            resolveNavSceneLayout(
                snapshot = snapshot,
                maxPaneCount = 1,
                sceneStrategies = listOf(
                    NavSceneStrategy {
                        NavSceneLayout(
                            contentPaneScene = paneScene(home),
                            overlayEntryIds = listOf(details.id),
                        )
                    },
                ),
            )
        }
        assertThrows<IllegalArgumentException> {
            resolveNavSceneLayout(
                snapshot = snapshot,
                maxPaneCount = 1,
                sceneStrategies = listOf(
                    NavSceneStrategies.trailingOverlays { true },
                ),
            )
        }
    }

    @Test
    fun `layout copies overlay identities and exposes immutable collections`() {
        val home = entry("home")
        val dialog = entry("dialog")
        val overlays = mutableListOf(dialog.id)
        val layout = NavSceneLayout(
            contentPaneScene = paneScene(home),
            overlayEntryIds = overlays,
        )
        overlays.clear()

        assertEquals(listOf(dialog.id), layout.overlayEntryIds)
        assertThrows<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (layout.overlayEntryIds as MutableList<NavEntryId>).clear()
        }
        assertThrows<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (layout.visibleEntryIds as MutableSet<NavEntryId>).clear()
        }
    }

    private fun entry(route: String): NavEntry {
        return NavEntry(
            id = NavEntryId(route),
            route = NavRoute(route),
        )
    }

    private fun paneScene(vararg entries: NavEntry): NavPaneScene {
        return NavPaneScene(
            entries.mapIndexed { index, entry ->
                NavPane(NavPaneRole.entries[index], entry.id)
            },
        )
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        try {
            block()
        } catch (throwable: Throwable) {
            if (throwable is T) return throwable
            throw throwable
        }
        fail("Expected ${T::class.simpleName} to be thrown.")
        error("Unreachable")
    }
}
