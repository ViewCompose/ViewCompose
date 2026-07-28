package com.viewcompose.navigation.core

/*
 * 测试职责：覆盖 navigation core 中的 Nav Graph 行为，防止导航契约在后续重构中回退。
 * Test responsibility: covers Nav Graph behavior in navigation core and guards navigation contracts against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NavGraphTest {
    @Test
    fun `graph route resolves recursively to its leaf start destination`() {
        val graph = testGraph()

        val resolved = graph.resolve(
            NavRoute(
                name = "account",
                arguments = mapOf(
                    "userId" to NavValue.LongValue(42L),
                ),
            ),
        )

        assertEquals("profile", resolved.destination.name)
        assertEquals(NavValue.Text("summary"), resolved.destination["screen"])
        assertEquals(NavValue.LongValue(42L), resolved.destination["userId"])
        assertEquals(listOf("app", "account"), resolved.hierarchy)
    }

    @Test
    fun `direct destination keeps arguments and reports all parent graphs`() {
        val graph = testGraph()
        val requested = NavRoute(
            name = "security",
            arguments = mapOf(
                "source" to NavValue.Text("settings"),
            ),
        )

        val resolved = graph.resolve(requested)

        assertEquals(requested, resolved.destination)
        assertEquals(listOf("app", "account"), resolved.hierarchy)
        assertTrue(graph.contains("security"))
        assertFalse(graph.contains("missing"))
    }

    @Test
    fun `root start can enter a nested graph atomically`() {
        val graph = navGraph(
            route = "app",
            startDestination = NavRoute(
                name = "account",
                arguments = mapOf(
                    "source" to NavValue.Text("startup"),
                ),
            ),
        ) {
            destination("home")
            navigation(
                route = "account",
                startDestination = NavRoute("profile"),
            ) {
                destination("profile")
            }
        }

        val resolved = graph.resolve(graph.startDestination)

        assertEquals("profile", resolved.destination.name)
        assertEquals(NavValue.Text("startup"), resolved.destination["source"])
        assertEquals(listOf("app", "account"), resolved.hierarchy)
    }

    @Test
    fun `graph rejects missing starts duplicate routes and unknown destinations`() {
        assertThrows<IllegalArgumentException> {
            navGraph(
                route = "app",
                startDestination = NavRoute("missing"),
            ) {
                destination("home")
            }
        }
        assertThrows<IllegalStateException> {
            navGraph(
                route = "app",
                startDestination = NavRoute("home"),
            ) {
                destination("home")
                navigation(
                    route = "account",
                    startDestination = NavRoute("home"),
                ) {
                    destination("home")
                }
            }
        }

        val graph = testGraph()
        assertThrows<IllegalArgumentException> {
            graph.resolve(NavRoute("missing"))
        }
    }

    private fun testGraph(): NavGraph {
        return navGraph(
            route = "app",
            startDestination = NavRoute("home"),
        ) {
            destination("home")
            navigation(
                route = "account",
                startDestination = NavRoute(
                    name = "profile",
                    arguments = mapOf(
                        "screen" to NavValue.Text("summary"),
                    ),
                ),
            ) {
                destination("profile")
                destination("security")
            }
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
