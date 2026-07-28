package com.viewcompose.navigation

/*
 * 测试职责：覆盖 Android navigation runtime 中的 Nav Host Saved State 行为，防止导航契约在后续重构中回退。
 * Test responsibility: covers Nav Host Saved State behavior in Android navigation runtime and guards navigation contracts against regressions.
 */

import android.os.Bundle
import com.viewcompose.navigation.core.NavBackStackSnapshot
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryIdFactory
import com.viewcompose.navigation.core.NavGraph
import com.viewcompose.navigation.core.NavGraphEntry
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavStackConfiguration
import com.viewcompose.navigation.core.NavStackId
import com.viewcompose.navigation.core.NavStackSetSnapshot
import com.viewcompose.navigation.core.NavStackSpec
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
    fun `codec preserves all stacks selection history routes and destination state`() {
        val homeStack = NavBackStackSnapshot(
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
        val searchStack = NavBackStackSnapshot(
            listOf(
                NavEntry(
                    id = NavEntryId("search-root"),
                    route = NavRoute("search"),
                ),
            ),
        )
        val stackState = NavStackSetSnapshot(
            activeStackId = SearchStack,
            stacks = linkedMapOf(
                HomeStack to homeStack,
                SearchStack to searchStack,
            ),
            selectionHistory = listOf(HomeStack),
        )
        val destinationState = Bundle().apply {
            putString("query", "restored")
            putInt("selection", 3)
        }

        val decoded = checkNotNull(
            decodeNavHostState(
                encodeNavHostState(
                    NavHostRestorableState(
                        stackState = stackState,
                        destinationState = destinationState,
                    ),
                ),
            ),
        )

        assertEquals(stackState, decoded.stackState)
        assertEquals(
            listOf("app", "account"),
            checkNotNull(decoded.stackState[HomeStack]).top.graphHierarchy,
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
                    "stacks" to emptyList<Any?>(),
                ),
            ),
        )
        assertNull(
            decodeNavHostState(
                mapOf(
                    "formatVersion" to 4,
                    "activeStackId" to "home",
                    "selectionHistory" to emptyList<String>(),
                    "stacks" to listOf(
                        encodedStack(
                            id = "home",
                            entries = listOf(
                                encodedEntry(id = "same", routeName = "home"),
                                encodedEntry(id = "same", routeName = "details"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertNull(
            decodeNavHostState(
                mapOf(
                    "formatVersion" to 4,
                    "activeStackId" to "home",
                    "selectionHistory" to emptyList<String>(),
                    "stacks" to listOf(
                        encodedStack(
                            id = "home",
                            entries = listOf(
                                encodedEntry(
                                    id = "root",
                                    routeName = "",
                                ),
                            ),
                        ),
                    ),
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
                "formatVersion" to 4,
                "activeStackId" to "default",
                "selectionHistory" to emptyList<String>(),
                "stacks" to listOf("not-a-stack"),
                "destinationState" to null,
            ),
        )

        assertEquals(
            listOf(startDestination),
            restored.snapshot.entries.map(NavEntry::route),
        )
    }

    @Test
    fun `multi stack saver restores every stack and active selection`() {
        val configuration = NavStackConfiguration(
            initialStackId = HomeStack,
            stacks = listOf(
                NavStackSpec(HomeStack, NavRoute("home")),
                NavStackSpec(SearchStack, NavRoute("search")),
            ),
        )
        val ids = ArrayDeque(listOf("home-root", "search-root"))
        val controller = createNavHostController(
            stackConfiguration = configuration,
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(ids.removeFirst())
            },
        )
        val encoded = encodeNavHostState(controller.stateForSave())
            .toMutableMap()
            .apply {
                this["activeStackId"] = "search"
                this["selectionHistory"] = listOf("home")
            }

        val restored = navHostControllerSaver(configuration).restore(encoded)

        assertEquals(SearchStack, restored.activeStackId)
        assertEquals(listOf(HomeStack), restored.stackState.selectionHistory)
        assertEquals("home-root", restored.stackSnapshot(HomeStack).top.id.value)
        assertEquals("search-root", restored.stackSnapshot(SearchStack).top.id.value)
    }

    private fun encodedStack(
        id: String,
        entries: List<Map<String, Any?>>,
    ): Map<String, Any?> {
        return mapOf(
            "stackId" to id,
            "entries" to entries,
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

    private companion object {
        val HomeStack = NavStackId("home")
        val SearchStack = NavStackId("search")
    }
}
