package com.viewcompose.navigation.core

/*
 * 测试职责：覆盖 navigation core 中的 Nav Deep Link 行为，防止导航契约在后续重构中回退。
 * Test responsibility: covers Nav Deep Link behavior in navigation core and guards navigation contracts against regressions.
 */

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
    fun `unknown query values stay outside route arguments and declared navigation policy`() {
        val accountStack = NavStackId("account")
        val deepLink = NavDeepLink(
            uriPattern = "https://example.com/users/{userId}?source={source}",
            argumentTypes = mapOf("userId" to NavDeepLinkArgumentType.Long),
            targetStackId = accountStack,
        )
        val graph = graphWith("profile" to listOf(deepLink))

        val resolution = graph.resolveDeepLink(
            "https://example.com/users/42?source=push" +
                "&userId=999&targetStackId=attacker&launchMode=Reset",
        )
        val match = (resolution as NavDeepLinkResolution.Matched).match

        assertEquals(
            mapOf(
                "userId" to NavValue.LongValue(42L),
                "source" to NavValue.Text("push"),
            ),
            match.route.arguments,
        )
        assertEquals(accountStack, match.deepLink.targetStackId)
    }

    @Test
    fun `unknown query values do not break an otherwise ambiguous best match`() {
        val graph = graphWith(
            "article" to listOf(
                NavDeepLink("https://example.com/content/{articleId}"),
            ),
            "video" to listOf(
                NavDeepLink("https://example.com/content/{videoId}"),
            ),
        )

        val resolution = graph.resolveDeepLink(
            "https://example.com/content/42?articleId=42&type=article",
        )

        assertEquals(
            NavDeepLinkRejectionReason.AmbiguousMatch,
            (resolution as NavDeepLinkResolution.Rejected).rejection.reason,
        )
        assertEquals(2, resolution.rejection.candidates.size)
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
        assertEquals(
            "https://example.com/users/{userId}",
            invalidArgument.rejection.candidates.single().uriPattern,
        )
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
        assertEquals(2, ambiguous.rejection.candidates.size)
    }

    @Test
    fun `action MIME and combined requests share one strict matcher`() {
        val actionLink = NavDeepLink(action = "com.example.OPEN_SETTINGS")
        val imageLink = NavDeepLink(mimeType = "image/*")
        val combinedLink = NavDeepLink(
            uriPattern = "content://example/items/{itemId}",
            action = "com.example.EDIT",
            mimeType = "Image/PNG",
        )
        val graph = graphWith(
            "settings" to listOf(actionLink),
            "image" to listOf(imageLink),
            "editor" to listOf(combinedLink),
        )

        val actionMatch = graph.resolveDeepLink(
            NavDeepLinkRequest(action = "com.example.OPEN_SETTINGS"),
        ) as NavDeepLinkResolution.Matched
        val mimeMatch = graph.resolveDeepLink(
            NavDeepLinkRequest(mimeType = "IMAGE/JPEG"),
        ) as NavDeepLinkResolution.Matched
        val combinedMatch = graph.resolveDeepLink(
            NavDeepLinkRequest(
                uri = "content://example/items/42",
                action = "com.example.EDIT",
                mimeType = "image/png",
            ),
        ) as NavDeepLinkResolution.Matched

        assertEquals("settings", actionMatch.match.route.name)
        assertEquals("image", mimeMatch.match.route.name)
        assertEquals("editor", combinedMatch.match.route.name)
        assertEquals(NavValue.Text("42"), combinedMatch.match.route["itemId"])
        assertEquals("image/png", combinedLink.mimeType)
    }

    @Test
    fun `more constrained request declaration wins over broad candidates`() {
        val combined = NavDeepLink(
            uriPattern = "content://example/items/{itemId}",
            action = "com.example.EDIT",
            mimeType = "image/*",
        )
        val graph = graphWith(
            "uri-only" to listOf(NavDeepLink("content://example/items/{itemId}")),
            "action-only" to listOf(NavDeepLink(action = "com.example.EDIT")),
            "combined" to listOf(combined),
        )

        val resolution = graph.resolveDeepLink(
            NavDeepLinkRequest(
                uri = "content://example/items/42",
                action = "com.example.EDIT",
                mimeType = "image/png",
            ),
        )

        assertEquals(
            "combined",
            (resolution as NavDeepLinkResolution.Matched).match.route.name,
        )
    }

    @Test
    fun `missing or mismatched action and MIME constraints do not partially match`() {
        val graph = graphWith(
            "editor" to listOf(
                NavDeepLink(
                    uriPattern = "content://example/items/42",
                    action = "com.example.EDIT",
                    mimeType = "image/png",
                ),
            ),
        )

        assertSame(
            NavDeepLinkResolution.NoMatch,
            graph.resolveDeepLink(NavDeepLinkRequest(uri = "content://example/items/42")),
        )
        assertSame(
            NavDeepLinkResolution.NoMatch,
            graph.resolveDeepLink(
                NavDeepLinkRequest(
                    uri = "content://example/items/42",
                    action = "com.example.VIEW",
                    mimeType = "image/png",
                ),
            ),
        )
        assertSame(
            NavDeepLinkResolution.NoMatch,
            graph.resolveDeepLink(
                NavDeepLinkRequest(
                    uri = "content://example/items/42",
                    action = "com.example.EDIT",
                    mimeType = "text/plain",
                ),
            ),
        )
    }

    @Test
    fun `malformed structured request fields are rejected before broad fallback`() {
        val graph = graphWith(
            "action" to listOf(NavDeepLink(action = "com.example.OPEN")),
        )

        val malformedUri = graph.resolveDeepLink(
            NavDeepLinkRequest(uri = "https://example.com/%GG", action = "com.example.OPEN"),
        ) as NavDeepLinkResolution.Rejected
        val malformedAction = graph.resolveDeepLink(
            NavDeepLinkRequest(action = "\n"),
        ) as NavDeepLinkResolution.Rejected
        val malformedMime = graph.resolveDeepLink(
            NavDeepLinkRequest(mimeType = "image"),
        ) as NavDeepLinkResolution.Rejected

        assertEquals(NavDeepLinkRejectionReason.MalformedUri, malformedUri.rejection.reason)
        assertEquals(NavDeepLinkRejectionReason.MalformedAction, malformedAction.rejection.reason)
        assertEquals(NavDeepLinkRejectionReason.MalformedMimeType, malformedMime.rejection.reason)
    }

    @Test
    fun `same URI may declare distinct action constraints while exact duplicates fail`() {
        val uri = "content://example/items/42"
        val graph = graphWith(
            "view" to listOf(NavDeepLink(uriPattern = uri, action = "com.example.VIEW")),
            "edit" to listOf(NavDeepLink(uriPattern = uri, action = "com.example.EDIT")),
        )

        val match = graph.resolveDeepLink(
            NavDeepLinkRequest(uri = uri, action = "com.example.EDIT"),
        ) as NavDeepLinkResolution.Matched

        assertEquals("edit", match.match.route.name)
        assertThrows<IllegalStateException> {
            graphWith(
                "first" to listOf(NavDeepLink(action = "com.example.OPEN")),
                "second" to listOf(NavDeepLink(action = "com.example.OPEN")),
            )
        }
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
        assertThrows<IllegalArgumentException> {
            NavDeepLink()
        }
        assertThrows<IllegalArgumentException> {
            NavDeepLink(action = " ")
        }
        assertThrows<IllegalArgumentException> {
            NavDeepLink(mimeType = "image")
        }
        assertThrows<IllegalArgumentException> {
            NavDeepLink(
                action = "com.example.OPEN",
                argumentTypes = mapOf("missing" to NavDeepLinkArgumentType.Long),
            )
        }
        assertThrows<IllegalArgumentException> {
            NavDeepLinkRequest()
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
