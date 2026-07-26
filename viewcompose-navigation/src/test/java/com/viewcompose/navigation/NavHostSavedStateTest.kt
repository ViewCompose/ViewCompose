package com.viewcompose.navigation

import android.os.Bundle
import com.viewcompose.navigation.core.NavBackStackSnapshot
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavValue
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
                    "formatVersion" to 1,
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
                    "formatVersion" to 1,
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
                "formatVersion" to 1,
                "entries" to listOf("not-an-entry"),
                "destinationState" to null,
            ),
        )

        assertEquals(
            listOf(startDestination),
            restored.snapshot.entries.map(NavEntry::route),
        )
    }

    private fun encodedEntry(
        id: String,
        routeName: String,
    ): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "routeName" to routeName,
            "routeArguments" to emptyMap<String, Any?>(),
        )
    }
}
