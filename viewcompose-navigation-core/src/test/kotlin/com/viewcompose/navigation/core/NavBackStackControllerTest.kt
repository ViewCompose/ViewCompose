package com.viewcompose.navigation.core

/*
 * 测试职责：覆盖 navigation core 中的 Nav Back Stack Controller 行为，防止导航契约在后续重构中回退。
 * Test responsibility: covers Nav Back Stack Controller behavior in navigation core and guards navigation contracts against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NavBackStackControllerTest {
    @Test
    fun `prepared push is invisible until commit`() {
        val controller = controllerWithIds("root", "details")
        val before = controller.snapshot()

        val transaction = controller.prepare(
            NavCommand.Push(NavRoute("details")),
        ).readyTransaction()

        assertSame(before, controller.snapshot())
        assertEquals(listOf("home"), controller.snapshot().routeNames())
        assertEquals(listOf("home", "details"), transaction.after.routeNames())
        assertEquals(NavTransactionStatus.Prepared, transaction.status)

        val committed = transaction.commit()

        assertSame(committed, controller.snapshot())
        assertEquals(listOf("home", "details"), committed.routeNames())
        assertEquals(NavTransactionStatus.Committed, transaction.status)
    }

    @Test
    fun `rollback preserves previous stack and closes prepared slot`() {
        val controller = controllerWithIds("root", "first-candidate", "second-candidate")
        val before = controller.snapshot()
        val first = controller.prepare(
            NavCommand.Push(NavRoute("first")),
        ).readyTransaction()

        first.rollback()

        assertSame(before, controller.snapshot())
        assertEquals(NavTransactionStatus.RolledBack, first.status)

        val second = controller.prepare(
            NavCommand.Push(NavRoute("second")),
        ).readyTransaction()
        assertEquals("second-candidate", second.after.top.id.value)
    }

    @Test
    fun `controller rejects a second command while transaction is prepared`() {
        val controller = controllerWithIds("root", "details")
        val transaction = controller.prepare(
            NavCommand.Push(NavRoute("details")),
        ).readyTransaction()

        assertThrows<IllegalStateException> {
            controller.prepare(NavCommand.Pop)
        }

        transaction.rollback()
    }

    @Test
    fun `pop root and single top return explicit no-change reasons`() {
        val controller = controllerWithIds("root", "unused")

        val pop = controller.prepare(NavCommand.Pop) as NavPreparation.NoChange
        val singleTop = controller.prepare(
            NavCommand.Push(
                route = NavRoute("home"),
                launchMode = NavLaunchMode.SingleTop,
            ),
        ) as NavPreparation.NoChange

        assertEquals(NavNoChangeReason.CannotPopRoot, pop.reason)
        assertEquals(NavNoChangeReason.AlreadyAtDestination, singleTop.reason)
        assertSame(controller.snapshot(), pop.snapshot)
        assertSame(controller.snapshot(), singleTop.snapshot)
    }

    @Test
    fun `result bearing pop prepares the same removal without publishing early`() {
        val controller = controllerWithIds("root", "details")
        controller.prepare(
            NavCommand.Push(NavRoute("details")),
        ).readyTransaction().commit()
        val before = controller.snapshot()
        val command = NavCommand.PopWithResult(
            NavResultKey.text("selection").encode("primary"),
        )

        val transaction = controller.prepare(command).readyTransaction()

        assertSame(before, controller.snapshot())
        assertSame(command, transaction.command)
        assertEquals(listOf("home"), transaction.after.routeNames())
        assertEquals(listOf("details"), transaction.mutation.removed.map { it.route.name })
        transaction.rollback()
    }

    @Test
    fun `single top no-change does not allocate an entry ID`() {
        val ids = ArrayDeque(listOf("root", "details"))
        val controller = NavBackStackController.create(
            startDestination = NavRoute("home"),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(ids.removeFirst())
            },
        )

        controller.prepare(
            NavCommand.Push(
                route = NavRoute("home"),
                launchMode = NavLaunchMode.SingleTop,
            ),
        )
        val push = controller.prepare(
            NavCommand.Push(NavRoute("details")),
        ).readyTransaction()

        assertEquals("details", push.after.top.id.value)
    }

    @Test
    fun `replace and reset report exact added and removed entries`() {
        val controller = controllerWithIds("root", "details", "editor", "login")
        controller.prepare(
            NavCommand.Push(NavRoute("details")),
        ).readyTransaction().commit()

        val replace = controller.prepare(
            NavCommand.ReplaceTop(NavRoute("editor")),
        ).readyTransaction()

        assertEquals(listOf("details"), replace.mutation.removed.map { it.route.name })
        assertEquals(listOf("editor"), replace.mutation.added.map { it.route.name })
        assertEquals("details", replace.mutation.previousTop.route.name)
        assertEquals("editor", replace.mutation.nextTop.route.name)
        replace.commit()

        val reset = controller.prepare(
            NavCommand.Reset(NavRoute("login")),
        ).readyTransaction()

        assertEquals(listOf("home", "editor"), reset.mutation.removed.map { it.route.name })
        assertEquals(listOf("login"), reset.mutation.added.map { it.route.name })
        assertEquals(listOf("login"), reset.after.routeNames())
    }

    @Test
    fun `abandoned entry IDs cannot be reused`() {
        val ids = ArrayDeque(listOf("root", "candidate", "candidate"))
        val controller = NavBackStackController.create(
            startDestination = NavRoute("home"),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(ids.removeFirst())
            },
        )
        controller.prepare(
            NavCommand.Push(NavRoute("first")),
        ).readyTransaction().rollback()

        val error = assertThrows<IllegalStateException> {
            controller.prepare(NavCommand.Push(NavRoute("second")))
        }

        assertTrue(error.message.orEmpty().contains("already allocated"))
    }

    @Test
    fun `restored stack remains stable and allocates only new IDs`() {
        val restored = NavBackStackSnapshot(
            listOf(
                NavEntry(NavEntryId("root"), NavRoute("home")),
                NavEntry(NavEntryId("details"), NavRoute("details")),
            ),
        )
        val controller = NavBackStackController.restore(
            snapshot = restored,
            entryIdFactory = NavEntryIdFactory {
                NavEntryId("editor")
            },
        )

        assertSame(restored, controller.snapshot())

        val transaction = controller.prepare(
            NavCommand.Push(NavRoute("editor")),
        ).readyTransaction()

        assertEquals(listOf("root", "details", "editor"), transaction.after.entries.map { it.id.value })
    }

    @Test
    fun `graph navigation commits resolved leaves with hierarchy metadata`() {
        val ids = ArrayDeque(listOf("root", "profile", "security"))
        val graph = testGraph()
        val controller = NavBackStackController.create(
            graph = graph,
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(ids.removeFirst())
            },
        )

        assertEquals("home", controller.snapshot().top.route.name)
        assertEquals(listOf("app"), controller.snapshot().top.graphHierarchy)

        val enterGraph = controller.prepare(
            NavCommand.Push(
                NavRoute(
                    name = "account",
                    arguments = mapOf(
                        "userId" to NavValue.LongValue(42L),
                    ),
                ),
            ),
        ).readyTransaction()

        assertEquals("profile", enterGraph.after.top.route.name)
        assertEquals(NavValue.LongValue(42L), enterGraph.after.top.route["userId"])
        assertEquals(listOf("app", "account"), enterGraph.after.top.graphHierarchy)
        enterGraph.commit()

        val directChild = controller.prepare(
            NavCommand.Push(NavRoute("security")),
        ).readyTransaction()

        assertEquals(listOf("app", "account"), directChild.after.top.graphHierarchy)
    }

    @Test
    fun `graph single top compares the resolved destination`() {
        val ids = ArrayDeque(listOf("root", "unused"))
        val controller = NavBackStackController.create(
            graph = testGraph(),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(ids.removeFirst())
            },
        )
        controller.prepare(
            NavCommand.Reset(NavRoute("account")),
        ).readyTransaction().commit()

        val result = controller.prepare(
            NavCommand.Push(
                route = NavRoute("account"),
                launchMode = NavLaunchMode.SingleTop,
            ),
        )

        assertEquals(NavNoChangeReason.AlreadyAtDestination, (result as NavPreparation.NoChange).reason)
        assertEquals(0, ids.size)
    }

    @Test
    fun `graph restore requires the exact persisted hierarchy`() {
        val graph = testGraph()
        val restored = NavBackStackSnapshot(
            listOf(
                NavEntry(
                    id = NavEntryId("root"),
                    route = NavRoute("home"),
                    graphEntries = listOf(
                        NavGraphEntry(
                            id = NavEntryId("app-scope"),
                            route = NavRoute("app"),
                        ),
                    ),
                ),
                NavEntry(
                    id = NavEntryId("profile"),
                    route = NavRoute("profile"),
                    graphEntries = listOf(
                        NavGraphEntry(
                            id = NavEntryId("app-scope"),
                            route = NavRoute("app"),
                        ),
                        NavGraphEntry(
                            id = NavEntryId("account-scope"),
                            route = NavRoute("account"),
                        ),
                    ),
                ),
            ),
        )

        val controller = NavBackStackController.restore(
            snapshot = restored,
            graph = graph,
        )

        assertSame(restored, controller.snapshot())
        assertThrows<IllegalArgumentException> {
            NavBackStackController.restore(
                snapshot = NavBackStackSnapshot(
                    listOf(
                        NavEntry(
                            id = NavEntryId("root"),
                            route = NavRoute("home"),
                            graphEntries = listOf(
                                NavGraphEntry(
                                    id = NavEntryId("moved-scope"),
                                    route = NavRoute("moved"),
                                ),
                            ),
                        ),
                    ),
                ),
                graph = graph,
            )
        }
        assertThrows<IllegalArgumentException> {
            NavBackStackController.restore(snapshot = restored)
        }
    }

    @Test
    fun `graph instances are shared within a graph and recreated when entering it again`() {
        val ids = ArrayDeque(listOf("root", "profile", "security", "profile-again"))
        val controller = NavBackStackController.create(
            graph = testGraph(),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(ids.removeFirst())
            },
        )
        val appScope = controller.snapshot().top.graphEntries.single()

        val firstProfile = controller.prepare(
            NavCommand.Push(
                NavRoute(
                    name = "account",
                    arguments = mapOf(
                        "userId" to NavValue.LongValue(42L),
                    ),
                ),
            ),
        ).readyTransaction()
        val firstAccountScope = firstProfile.after.top.graphEntries.last()
        firstProfile.commit()

        val security = controller.prepare(
            NavCommand.Push(NavRoute("security")),
        ).readyTransaction()
        assertEquals(
            listOf(appScope, firstAccountScope),
            security.after.top.graphEntries,
        )
        assertEquals(
            NavValue.LongValue(42L),
            security.after.top.graphEntries.last().route["userId"],
        )
        security.commit()

        val secondProfile = controller.prepare(
            NavCommand.Push(NavRoute("account")),
        ).readyTransaction()

        assertEquals(appScope, secondProfile.after.top.graphEntries.first())
        assertNotEquals(
            firstAccountScope.id,
            secondProfile.after.top.graphEntries.last().id,
        )
    }

    @Test
    fun `reset allocates a fresh root graph instance`() {
        val ids = ArrayDeque(listOf("root", "profile"))
        val controller = NavBackStackController.create(
            graph = testGraph(),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(ids.removeFirst())
            },
        )
        val originalAppScope = controller.snapshot().top.graphEntries.single()

        val reset = controller.prepare(
            NavCommand.Reset(NavRoute("account")),
        ).readyTransaction()

        assertNotEquals(
            originalAppScope.id,
            reset.after.top.graphEntries.first().id,
        )
        assertEquals(
            listOf("app", "account"),
            reset.after.top.graphHierarchy,
        )
    }

    @Test
    fun `route arguments and stack entries are defensively copied`() {
        val arguments = linkedMapOf<String, NavValue>(
            "documentId" to NavValue.LongValue(42L),
        )
        val route = NavRoute("editor", arguments)
        arguments["documentId"] = NavValue.LongValue(99L)

        val mutableEntries = mutableListOf(
            NavEntry(NavEntryId("root"), route),
        )
        val snapshot = NavBackStackSnapshot(mutableEntries)
        mutableEntries += NavEntry(NavEntryId("other"), NavRoute("other"))

        assertEquals(NavValue.LongValue(42L), route["documentId"])
        assertEquals(1, snapshot.entries.size)
        assertThrows<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (route.arguments as MutableMap<String, NavValue>)["other"] = NavValue.Text("value")
        }
        assertThrows<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (snapshot.entries as MutableList<NavEntry>).clear()
        }
    }

    @Test
    fun `closing a prepared transaction rolls it back and close is idempotent`() {
        val controller = controllerWithIds("root", "details")
        val before = controller.snapshot()
        val transaction = controller.prepare(
            NavCommand.Push(NavRoute("details")),
        ).readyTransaction()

        transaction.close()
        transaction.close()

        assertSame(before, controller.snapshot())
        assertEquals(NavTransactionStatus.RolledBack, transaction.status)
    }

    @Test
    fun `entry and transaction invariants fail fast`() {
        assertThrows<IllegalArgumentException> {
            NavEntryId(" ")
        }
        assertThrows<IllegalArgumentException> {
            NavRoute("")
        }
        assertThrows<IllegalArgumentException> {
            NavBackStackSnapshot(emptyList())
        }
        assertThrows<IllegalArgumentException> {
            NavBackStackSnapshot(
                listOf(
                    NavEntry(NavEntryId("same"), NavRoute("first")),
                    NavEntry(NavEntryId("same"), NavRoute("second")),
                ),
            )
        }
        assertThrows<IllegalArgumentException> {
            NavBackStackSnapshot(
                listOf(
                    NavEntry(
                        id = NavEntryId("home"),
                        route = NavRoute("home"),
                        graphEntries = listOf(
                            NavGraphEntry(
                                id = NavEntryId("shared-scope"),
                                route = NavRoute("app"),
                            ),
                        ),
                    ),
                    NavEntry(
                        id = NavEntryId("details"),
                        route = NavRoute("details"),
                        graphEntries = listOf(
                            NavGraphEntry(
                                id = NavEntryId("shared-scope"),
                                route = NavRoute("other"),
                            ),
                        ),
                    ),
                ),
            )
        }

        val controller = controllerWithIds("root", "details")
        val transaction = controller.prepare(
            NavCommand.Push(NavRoute("details")),
        ).readyTransaction()
        transaction.commit()
        assertThrows<IllegalStateException> {
            transaction.commit()
        }
        assertNotEquals(NavTransactionStatus.Prepared, transaction.status)
    }

    private fun controllerWithIds(vararg ids: String): NavBackStackController {
        val remainingIds = ArrayDeque(ids.toList())
        return NavBackStackController.create(
            startDestination = NavRoute("home"),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(remainingIds.removeFirst())
            },
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
                destination("security")
            }
        }
    }

    private fun NavPreparation.readyTransaction(): NavTransaction {
        return (this as NavPreparation.Ready).transaction
    }

    private fun NavBackStackSnapshot.routeNames(): List<String> {
        return entries.map { it.route.name }
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
