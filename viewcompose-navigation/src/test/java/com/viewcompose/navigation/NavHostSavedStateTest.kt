package com.viewcompose.navigation

import android.os.Bundle
import com.viewcompose.navigation.core.NavBackStackSnapshot
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryIdFactory
import com.viewcompose.navigation.core.NavGraph
import com.viewcompose.navigation.core.NavGraphEntry
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavValue
import com.viewcompose.navigation.core.navGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavHostSavedStateTest {
    @Test
    fun `codec preserves stack IDs routes arguments and destination state`() {
        val snapshot = NavBackStackSnapshot(
            listOf(
                NavEntry(
                    id = NavEntryId("root"),
                    route = NavRoute("home"),
                ),
                NavEntry(
                    id = NavEntryId("details"),
                    route = NavRoute(
                        name = "details",
                        arguments = linkedMapOf(
                            "null" to NavValue.Null,
                            "text" to NavValue.Text("ViewCompose"),
                            "int" to NavValue.IntValue(7),
                            "long" to NavValue.LongValue(Long.MAX_VALUE),
                            "boolean" to NavValue.BooleanValue(true),
                            "float" to NavValue.FloatValue(1.5f),
                            "double" to NavValue.DoubleValue(2.5),
                        ),
                    ),
                    graphEntries = listOf(
                        NavGraphEntry(
                            id = NavEntryId("app-scope"),
                            route = NavRoute("app"),
                        ),
                        NavGraphEntry(
                            id = NavEntryId("account-scope"),
                            route = NavRoute(
                                name = "account",
                                arguments = mapOf(
                                    "userId" to NavValue.LongValue(42L),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val destinationState = Bundle().apply {
            putString("query", "restored")
            putInt("selection", 3)
        }

        val decoded = checkNotNull(
            decodeNavHostState(
                encodeNavHostState(
                    NavHostRestorableState(
                        snapshot = snapshot,
                        destinationState = destinationState,
                    ),
                ),
            ),
        )

        assertEquals(snapshot, decoded.snapshot)
        assertEquals(
            listOf("app", "account"),
            decoded.snapshot.top.graphHierarchy,
        )
        assertEquals("restored", decoded.destinationState?.getString("query"))
        assertEquals(3, decoded.destinationState?.getInt("selection"))
    }

    @Test
    fun `decoder rejects incompatible and structurally invalid state`() {
        assertNull(
            decodeNavHostState(
                mapOf(
                    "formatVersion" to Int.MAX_VALUE,
                    "entries" to emptyList<Any?>(),
                ),
            ),
        )
        assertNull(
            decodeNavHostState(
                mapOf(
                    "formatVersion" to 3,
                    "entries" to listOf(
                        encodedEntry(id = "same", routeName = "home"),
                        encodedEntry(id = "same", routeName = "details"),
                    ),
                    "destinationState" to null,
                ),
            ),
        )
        assertNull(
            decodeNavHostState(
                mapOf(
                    "formatVersion" to 3,
                    "entries" to listOf(
                        encodedEntry(
                            id = "root",
                            routeName = "",
                        ),
                    ),
                    "destinationState" to null,
                ),
            ),
        )
    }

    @Test
    fun `controller saver falls back to start destination when restored state is corrupt`() {
        val startDestination = NavRoute(
            name = "safe-start",
            arguments = mapOf(
                "source" to NavValue.Text("fallback"),
            ),
        )

        val restored = navHostControllerSaver(startDestination).restore(
            mapOf(
                "formatVersion" to 3,
                "entries" to listOf("not-an-entry"),
                "destinationState" to null,
            ),
        )

        assertEquals(
            listOf(startDestination),
            restored.snapshot.entries.map(NavEntry::route),
        )
    }

    @Test
    fun `graph controller saver restores only the exact graph hierarchy`() {
        val graph = testGraph()
        val controller = createNavHostController(
            graph = graph,
            entryIdFactory = NavEntryIdFactory {
                NavEntryId("root")
            },
        )
        val encoded = encodeNavHostState(controller.stateForSave())

        val restored = navHostControllerSaver(graph).restore(encoded)

        assertEquals("home", restored.snapshot.top.route.name)
        assertEquals(listOf("app"), restored.snapshot.top.graphHierarchy)

        val movedGraph = navGraph(
            route = "moved-app",
            startDestination = NavRoute("home"),
        ) {
            destination("home")
        }
        val fallback = navHostControllerSaver(movedGraph).restore(encoded)

        assertEquals("home", fallback.snapshot.top.route.name)
        assertEquals(listOf("moved-app"), fallback.snapshot.top.graphHierarchy)
    }

    private fun encodedEntry(
        id: String,
        routeName: String,
    ): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "routeName" to routeName,
            "graphEntries" to emptyList<Map<String, Any?>>(),
            "routeArguments" to emptyMap<String, Any?>(),
        )
    }

    private fun testGraph(): NavGraph {
        return navGraph(
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
    }
}
