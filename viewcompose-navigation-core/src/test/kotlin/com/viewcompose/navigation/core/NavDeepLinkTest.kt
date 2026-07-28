package com.viewcompose.navigation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NavDeepLinkTest {
    @Test
    fun `typed path and query arguments resolve from an allowlisted URI`() {
        val deepLink = NavDeepLink(
            uriPattern = "https://example.com/users/{userId}?source={source}",
            argumentTypes = mapOf(
                "userId" to NavDeepLinkArgumentType.Long,
            ),
            targetStackId = NavStackId("account"),
        )
        val graph = graphWith(
            "profile" to listOf(deepLink),
        )

        val resolution = graph.resolveDeepLink(
            "HTTPS://EXAMPLE.COM/users/42?source=push%20message&ignored=value",
        )

        val match = (resolution as NavDeepLinkResolution.Matched).match
        assertEquals(deepLink, match.deepLink)
        assertEquals("profile", match.route.name)
        assertEquals(NavValue.LongValue(42L), match.route["userId"])
        assertEquals(NavValue.Text("push message"), match.route["source"])
        assertEquals(NavStackId("account"), match.deepLink.targetStackId)
    }

    @Test
    fun `static path wins over a placeholder path`() {
        val graph = graphWith(
            "profile" to listOf(
                NavDeepLink("https://example.com/users/{userId}"),
            ),
            "current-user" to listOf(
                NavDeepLink("https://example.com/users/me"),
            ),
        )

        val resolution = graph.resolveDeepLink("https://example.com/users/me")

        assertEquals(
            "current-user",
            (resolution as NavDeepLinkResolution.Matched).match.route.name,
        )
    }

    @Test
    fun `malformed URI and invalid typed argument are rejected`() {
        val graph = graphWith(
            "profile" to listOf(
                NavDeepLink(
                    uriPattern = "https://example.com/users/{userId}",
                    argumentTypes = mapOf(
                        "userId" to NavDeepLinkArgumentType.Long,
                    ),
                ),
            ),
        )

        val malformed = graph.resolveDeepLink("https://example.com/users/%GG")
        val invalidArgument = graph.resolveDeepLink("https://example.com/users/not-a-long")

        assertEquals(
            NavDeepLinkRejectionReason.MalformedUri,
            (malformed as NavDeepLinkResolution.Rejected).rejection.reason,
        )
        assertEquals(
            NavDeepLinkRejectionReason.InvalidArgument,
            (invalidArgument as NavDeepLinkResolution.Rejected).rejection.reason,
        )
        assertEquals("userId", invalidArgument.rejection.argumentName)
    }

    @Test
    fun `a more specific invalid candidate cannot downgrade to a broad match`() {
        val graph = graphWith(
            "broad" to listOf(
                NavDeepLink("https://example.com/{section}/{value}"),
            ),
            "typed-user" to listOf(
                NavDeepLink(
                    uriPattern = "https://example.com/users/{userId}",
                    argumentTypes = mapOf(
                        "userId" to NavDeepLinkArgumentType.Long,
                    ),
                ),
            ),
        )

        val resolution = graph.resolveDeepLink("https://example.com/users/not-a-long")

        assertEquals(
            NavDeepLinkRejectionReason.InvalidArgument,
            (resolution as NavDeepLinkResolution.Rejected).rejection.reason,
        )
        assertEquals("userId", resolution.rejection.argumentName)
    }

    @Test
    fun `duplicate constrained query values and equal matches are rejected`() {
        val queryGraph = graphWith(
            "search" to listOf(
                NavDeepLink("https://example.com/search?query={query}"),
            ),
        )
        val ambiguousGraph = graphWith(
            "article" to listOf(
                NavDeepLink("https://example.com/content/{articleId}"),
            ),
            "video" to listOf(
                NavDeepLink("https://example.com/content/{videoId}"),
            ),
        )

        val duplicateQuery = queryGraph.resolveDeepLink(
            "https://example.com/search?query=first&query=second",
        )
        val ambiguous = ambiguousGraph.resolveDeepLink(
            "https://example.com/content/42",
        )

        assertEquals(
            NavDeepLinkRejectionReason.InvalidArgument,
            (duplicateQuery as NavDeepLinkResolution.Rejected).rejection.reason,
        )
        assertEquals(
            NavDeepLinkRejectionReason.AmbiguousMatch,
            (ambiguous as NavDeepLinkResolution.Rejected).rejection.reason,
        )
        assertEquals(2, ambiguous.rejection.matchingPatterns.size)
    }

    @Test
    fun `untrusted URI components do not match`() {
        val graph = graphWith(
            "profile" to listOf(
                NavDeepLink("https://example.com/users/{userId}"),
            ),
        )

        assertSame(
            NavDeepLinkResolution.NoMatch,
            graph.resolveDeepLink("https://attacker.example/users/42"),
        )
        assertTrue(
            graph.resolveDeepLink("https://example.com/users/42#fragment")
                is NavDeepLinkResolution.Rejected,
        )
        assertTrue(
            graph.resolveDeepLink("https://example.com/users/%0A")
                is NavDeepLinkResolution.Rejected,
        )
        assertTrue(
            graph.resolveDeepLink("https://user@example.com/users/42")
                is NavDeepLinkResolution.Rejected,
        )
    }

    @Test
    fun `deep link on a nested graph enters its leaf start destination`() {
        val graph = navGraph(
            route = "app",
            startDestination = NavRoute("home"),
        ) {
            destination("home")
            navigation(
                route = "account",
                startDestination = NavRoute("profile"),
                deepLinks = listOf(
                    NavDeepLink("viewcompose://account/{userId}"),
                ),
            ) {
                destination("profile")
            }
        }

        val match = (
            graph.resolveDeepLink("viewcompose://account/42")
                as NavDeepLinkResolution.Matched
            ).match
        val resolved = graph.resolve(match.route)

        assertEquals("account", match.route.name)
        assertEquals(NavValue.Text("42"), match.route["userId"])
        assertEquals("profile", resolved.destination.name)
        assertEquals(NavValue.Text("42"), resolved.destination["userId"])
        assertEquals(listOf("app", "account"), resolved.hierarchy)
    }

    @Test
    fun `invalid declarations and duplicate registrations fail fast`() {
        assertThrows<IllegalArgumentException> {
            NavDeepLink("https://example.com/users/prefix-{userId}")
        }
        assertThrows<IllegalArgumentException> {
            NavDeepLink("https://example.com/search?{name}=value")
        }
        assertThrows<IllegalArgumentException> {
            NavDeepLink("https://example.com/{value}?copy={value}")
        }
        assertThrows<IllegalArgumentException> {
            NavDeepLink(
                uriPattern = "https://example.com/users/{userId}",
                argumentTypes = mapOf(
                    "missing" to NavDeepLinkArgumentType.Long,
                ),
            )
        }
        assertThrows<IllegalStateException> {
            graphWith(
                "first" to listOf(
                    NavDeepLink("https://example.com/duplicate"),
                ),
                "second" to listOf(
                    NavDeepLink("https://example.com/duplicate"),
                ),
            )
        }
    }

    private fun graphWith(
        vararg destinations: Pair<String, List<NavDeepLink>>,
    ): NavGraph {
        return navGraph(
            route = "app",
            startDestination = NavRoute("home"),
        ) {
            destination("home")
            destinations.forEach { (route, deepLinks) ->
                destination(
                    route = route,
                    deepLinks = deepLinks,
                )
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
