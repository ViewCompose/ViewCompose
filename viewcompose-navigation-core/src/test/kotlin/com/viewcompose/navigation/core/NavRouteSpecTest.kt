package com.viewcompose.navigation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NavRouteSpecTest {
    @Test
    fun `spec round trips typed value through immutable route arguments`() {
        val mutableArguments = linkedMapOf<String, NavValue>()
        val spec = profileSpec { profile ->
            mutableArguments.apply {
                clear()
                put("id", NavValue.LongValue(profile.id))
                put("editable", NavValue.BooleanValue(profile.editable))
            }
        }

        val route = spec.encode(Profile(42L, editable = true))
        mutableArguments["id"] = NavValue.LongValue(7L)

        assertEquals(Profile(42L, editable = true), spec.decode(route))
        assertEquals(NavValue.LongValue(42L), route["id"])
    }

    @Test
    fun `decode rejects another route before invoking application decoder`() {
        var decodeCalls = 0
        val spec = NavRouteSpec(
            name = "profile",
            encodeArguments = { emptyMap() },
            decodeArguments = {
                decodeCalls += 1
                Profile(0L, editable = false)
            },
        )

        assertThrows<IllegalArgumentException> {
            spec.decode(NavRoute("settings"))
        }
        assertEquals(0, decodeCalls)
    }

    @Test
    fun `entry exposes typed decode and route identity through one spec`() {
        val spec = profileSpec()
        val entry = NavEntry(
            id = NavEntryId("profile-entry"),
            route = spec.encode(Profile(9L, editable = false)),
        )

        assertTrue(entry.hasRoute(spec))
        assertFalse(entry.hasRoute(profileSpec(name = "other")))
        assertEquals(Profile(9L, editable = false), entry.toRoute(spec))
    }

    @Test
    fun `typed graph overloads preserve ordinary resolution and deep links`() {
        val account = unitSpec("account")
        val profile = profileSpec()
        val graph = navGraph(
            route = "app",
            startDestination = NavRoute("home"),
        ) {
            destination(unitSpec("home"))
            navigation(
                route = account,
                startDestination = profile.encode(Profile(1L, editable = false)),
            ) {
                destination(
                    route = profile,
                    deepLinks = listOf(NavDeepLink("https://example.com/profile/{id}")),
                )
            }
        }

        val resolved = graph.resolve(profile.encode(Profile(8L, editable = true)))
        val deepLink = graph.resolveDeepLink("https://example.com/profile/8")

        assertEquals(Profile(8L, editable = true), profile.decode(resolved.destination))
        assertEquals(listOf("app", "account"), resolved.hierarchy)
        assertEquals(
            "profile",
            (deepLink as NavDeepLinkResolution.Matched).match.route.name,
        )
    }

    @Test
    fun `spec rejects blank stable name`() {
        assertThrows<IllegalArgumentException> {
            NavRouteSpec(
                name = " ",
                encodeArguments = { _: Unit -> emptyMap() },
                decodeArguments = { Unit },
            )
        }
    }

    private fun profileSpec(
        name: String = "profile",
        encoder: (Profile) -> Map<String, NavValue> = { profile ->
            mapOf(
                "id" to NavValue.LongValue(profile.id),
                "editable" to NavValue.BooleanValue(profile.editable),
            )
        },
    ): NavRouteSpec<Profile> {
        return NavRouteSpec(
            name = name,
            encodeArguments = encoder,
            decodeArguments = { arguments ->
                Profile(
                    id = (arguments.getValue("id") as NavValue.LongValue).value,
                    editable = (arguments.getValue("editable") as NavValue.BooleanValue).value,
                )
            },
        )
    }

    private fun unitSpec(name: String): NavRouteSpec<Unit> {
        return NavRouteSpec(
            name = name,
            encodeArguments = { emptyMap() },
            decodeArguments = { Unit },
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

    private data class Profile(
        val id: Long,
        val editable: Boolean,
    )
}
