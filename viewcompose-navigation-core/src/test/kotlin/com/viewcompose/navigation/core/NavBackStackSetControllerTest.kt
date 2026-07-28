package com.viewcompose.navigation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NavBackStackSetControllerTest {
    @Test
    fun `each stack preserves its own history and stable entry identities`() {
        val controller = controllerWithIds(
            ids = listOf("home-root", "search-root", "home-details", "search-result"),
        )
        controller.prepare(
            NavCommand.Push(NavRoute("home-details")),
        ).readyTransaction().commit()
        val homeTop = controller.snapshot().top

        val selectSearch = controller.prepare(
            NavCommand.SelectStack(SearchStack),
        ).readyTransaction()

        assertEquals("home-details", selectSearch.before.top.route.name)
        assertEquals("search", selectSearch.after.top.route.name)
        assertTrue(selectSearch.mutation.added.isEmpty())
        assertTrue(selectSearch.mutation.removed.isEmpty())
        selectSearch.commit()

        controller.prepare(
            NavCommand.Push(NavRoute("search-result")),
        ).readyTransaction().commit()
        val searchTop = controller.snapshot().top

        controller.prepare(
            NavCommand.SelectStack(HomeStack),
        ).readyTransaction().commit()

        assertSame(homeTop, controller.snapshot().top)
        assertSame(
            searchTop,
            checkNotNull(controller.stackStateSnapshot()[SearchStack]).top,
        )
        assertEquals(
            listOf(SearchStack),
            controller.stackStateSnapshot().selectionHistory,
        )
    }

    @Test
    fun `stack selection remains invisible until commit and rollback restores history`() {
        val controller = controllerWithIds(
            ids = listOf("home-root", "search-root"),
        )
        val before = controller.stackStateSnapshot()
        val transaction = controller.prepare(
            NavCommand.SelectStack(SearchStack),
        ).readyTransaction()

        assertSame(before, controller.stackStateSnapshot())
        assertEquals(SearchStack, transaction.afterState.activeStackId)

        transaction.rollback()

        assertSame(before, controller.stackStateSnapshot())
        assertTrue(controller.stackStateSnapshot().selectionHistory.isEmpty())
    }

    @Test
    fun `reselect can atomically pop only the selected stack to root`() {
        val controller = controllerWithIds(
            ids = listOf("home-root", "search-root", "details", "result"),
        )
        controller.prepare(
            NavCommand.Push(NavRoute("details")),
        ).readyTransaction().commit()
        controller.prepare(
            NavCommand.SelectStack(SearchStack),
        ).readyTransaction().commit()
        controller.prepare(
            NavCommand.Push(NavRoute("result")),
        ).readyTransaction().commit()

        val transaction = controller.prepare(
            NavCommand.SelectStack(
                stackId = SearchStack,
                selectionMode = NavStackSelectionMode.PopToRoot,
            ),
        ).readyTransaction()

        assertEquals(listOf("result"), transaction.mutation.removed.map { it.route.name })
        assertEquals("search", transaction.after.top.route.name)
        transaction.commit()

        assertEquals(
            listOf("home", "details"),
            checkNotNull(controller.stackStateSnapshot()[HomeStack]).routeNames(),
        )
        assertEquals(listOf("search"), controller.snapshot().routeNames())
    }

    @Test
    fun `system back at a stack root returns to previous stack when configured`() {
        val controller = controllerWithIds(
            ids = listOf("home-root", "search-root", "details"),
            rootBackBehavior = NavRootBackBehavior.PreviousStack,
        )
        controller.prepare(
            NavCommand.Push(NavRoute("details")),
        ).readyTransaction().commit()
        controller.prepare(
            NavCommand.SelectStack(SearchStack),
        ).readyTransaction().commit()

        assertEquals(NavCommand.PopStackHistory, controller.systemBackCommand())

        val transaction = controller.prepare(
            checkNotNull(controller.systemBackCommand()),
        ).readyTransaction()
        assertEquals("search", transaction.before.top.route.name)
        assertEquals("details", transaction.after.top.route.name)
        transaction.commit()

        assertEquals(HomeStack, controller.stackStateSnapshot().activeStackId)
        assertTrue(controller.stackStateSnapshot().selectionHistory.isEmpty())
        assertEquals(NavCommand.Pop, controller.systemBackCommand())
    }

    @Test
    fun `preserving an already selected root reports explicit no change`() {
        val controller = controllerWithIds(
            ids = listOf("home-root", "search-root"),
        )

        val result = controller.prepare(
            NavCommand.SelectStack(HomeStack),
        ) as NavPreparation.NoChange

        assertEquals(NavNoChangeReason.AlreadySelectedStack, result.reason)
        assertSame(controller.snapshot(), result.snapshot)
    }

    @Test
    fun `graph instances stay independent across stack roots`() {
        val ids = ArrayDeque(listOf("home-root", "account-root"))
        val controller = NavBackStackController.create(
            configuration = NavStackConfiguration(
                initialStackId = HomeStack,
                stacks = listOf(
                    NavStackSpec(HomeStack, NavRoute("home")),
                    NavStackSpec(SearchStack, NavRoute("account")),
                ),
            ),
            graph = testGraph(),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(ids.removeFirst())
            },
        )

        val homeGraph = checkNotNull(
            controller.stackStateSnapshot()[HomeStack],
        ).top.graphEntries.first()
        val accountGraph = checkNotNull(
            controller.stackStateSnapshot()[SearchStack],
        ).top.graphEntries.first()

        assertEquals("app", homeGraph.route.name)
        assertEquals("app", accountGraph.route.name)
        assertNotEquals(homeGraph.id, accountGraph.id)
    }

    @Test
    fun `complete stack set restores selection history and future transactions`() {
        val first = controllerWithIds(
            ids = listOf("home-root", "search-root", "details"),
            rootBackBehavior = NavRootBackBehavior.PreviousStack,
        )
        first.prepare(
            NavCommand.Push(NavRoute("details")),
        ).readyTransaction().commit()
        first.prepare(
            NavCommand.SelectStack(SearchStack),
        ).readyTransaction().commit()
        val saved = first.stackStateSnapshot()

        val restored = NavBackStackController.restore(
            state = saved,
            configuration = configuration(NavRootBackBehavior.PreviousStack),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId("search-result")
            },
        )

        assertSame(saved, restored.stackStateSnapshot())
        assertEquals(NavCommand.PopStackHistory, restored.systemBackCommand())
        val pushed = restored.prepare(
            NavCommand.Push(NavRoute("search-result")),
        ).readyTransaction()
        assertEquals("search-result", pushed.after.top.id.value)
    }

    @Test
    fun `deep link selects and resets its target stack as one rollback-safe transaction`() {
        val ids = ArrayDeque(
            listOf(
                "home-root",
                "account-root",
                "security-rolled-back",
                "security-committed",
            ),
        )
        val accountStack = NavStackId("account")
        val graph = navGraph(
            route = "app",
            startDestination = NavRoute("home"),
        ) {
            destination("home")
            navigation(
                route = "account",
                startDestination = NavRoute("profile"),
            ) {
                destination("profile")
                destination(
                    route = "security",
                    deepLinks = listOf(
                        NavDeepLink(
                            uriPattern = "viewcompose://account/security/{section}",
                            targetStackId = accountStack,
                        ),
                    ),
                )
            }
        }
        val controller = NavBackStackController.create(
            configuration = NavStackConfiguration(
                initialStackId = HomeStack,
                stacks = listOf(
                    NavStackSpec(HomeStack, NavRoute("home")),
                    NavStackSpec(accountStack, NavRoute("account")),
                ),
            ),
            graph = graph,
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(ids.removeFirst())
            },
        )
        val match = (
            controller.resolveDeepLink("viewcompose://account/security/privacy")
                as NavDeepLinkResolution.Matched
            ).match
        val before = controller.stackStateSnapshot()

        val rolledBack = controller.prepare(
            NavCommand.OpenDeepLink(
                route = match.route,
                targetStackId = match.deepLink.targetStackId,
            ),
        ).readyTransaction()

        assertSame(before, controller.stackStateSnapshot())
        assertEquals(accountStack, rolledBack.afterState.activeStackId)
        assertEquals(listOf(HomeStack), rolledBack.afterState.selectionHistory)
        assertEquals(
            listOf("security"),
            checkNotNull(rolledBack.afterState[accountStack]).routeNames(),
        )
        assertEquals(
            NavValue.Text("privacy"),
            rolledBack.after.top.route["section"],
        )
        assertEquals(
            listOf("security-rolled-back"),
            rolledBack.mutation.added.map { entry -> entry.id.value },
        )
        assertEquals(
            listOf("account-root"),
            rolledBack.mutation.removed.map { entry -> entry.id.value },
        )
        rolledBack.rollback()

        assertSame(before, controller.stackStateSnapshot())

        controller.prepare(
            NavCommand.OpenDeepLink(
                route = match.route,
                targetStackId = match.deepLink.targetStackId,
            ),
        ).readyTransaction().commit()

        assertEquals(accountStack, controller.stackStateSnapshot().activeStackId)
        assertEquals("security-committed", controller.snapshot().top.id.value)
        assertEquals(listOf(HomeStack), controller.stackStateSnapshot().selectionHistory)
    }

    @Test
    fun `deep-link resolution is unsupported without a graph`() {
        val controller = controllerWithIds(
            ids = listOf("home-root", "search-root"),
        )

        assertSame(
            NavDeepLinkResolution.Unsupported,
            controller.resolveDeepLink("viewcompose://account/security"),
        )
    }

    @Test
    fun `stack set rejects owner identity collisions across stacks`() {
        val sharedEntry = NavEntry(
            id = NavEntryId("shared"),
            route = NavRoute("root"),
        )

        val error = assertThrows<IllegalArgumentException> {
            NavStackSetSnapshot(
                activeStackId = HomeStack,
                stacks = linkedMapOf(
                    HomeStack to NavBackStackSnapshot(listOf(sharedEntry)),
                    SearchStack to NavBackStackSnapshot(listOf(sharedEntry)),
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("must not be shared"))
    }

    private fun controllerWithIds(
        ids: List<String>,
        rootBackBehavior: NavRootBackBehavior = NavRootBackBehavior.Delegate,
    ): NavBackStackController {
        val remainingIds = ArrayDeque(ids)
        return NavBackStackController.create(
            configuration = configuration(rootBackBehavior),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(remainingIds.removeFirst())
            },
        )
    }

    private fun configuration(
        rootBackBehavior: NavRootBackBehavior,
    ): NavStackConfiguration {
        return NavStackConfiguration(
            initialStackId = HomeStack,
            stacks = listOf(
                NavStackSpec(HomeStack, NavRoute("home")),
                NavStackSpec(SearchStack, NavRoute("search")),
            ),
            rootBackBehavior = rootBackBehavior,
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

    private fun NavPreparation.readyTransaction(): NavTransaction {
        return (this as NavPreparation.Ready).transaction
    }

    private fun NavBackStackSnapshot.routeNames(): List<String> {
        return entries.map { entry -> entry.route.name }
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

    private companion object {
        val HomeStack = NavStackId("home")
        val SearchStack = NavStackId("search")
    }
}
