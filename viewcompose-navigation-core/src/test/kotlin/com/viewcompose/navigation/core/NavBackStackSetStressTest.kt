package com.viewcompose.navigation.core

/*
 * 压力测试职责：覆盖 navigation core 中的 Nav Back Stack Set Stress 极端路径，防止高频导航操作破坏状态一致性。
 * Stress test responsibility: covers Nav Back Stack Set Stress edge paths in navigation core and guards state consistency under frequent navigation operations.
 */

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NavBackStackSetStressTest {
    @Test
    fun `deterministic randomized transactions preserve the complete stack model`() {
        repeat(SEED_COUNT) { seedIndex ->
            val seed = BASE_SEED + seedIndex
            val random = Random(seed)
            var nextEntryId = 0L
            val controller = NavBackStackController.create(
                configuration = configuration(),
                entryIdFactory = NavEntryIdFactory {
                    NavEntryId("stress-${nextEntryId++}")
                },
            )
            var model = initialModel()
            val allocatedEntryIds = controller.stackStateSnapshot()
                .allEntries
                .mapTo(linkedSetOf(), NavEntry::id)

            repeat(OPERATIONS_PER_SEED) { step ->
                val command = randomCommand(
                    random = random,
                    model = model,
                )
                val context = "seed=$seed step=$step command=$command"
                val before = controller.stackStateSnapshot()
                val expected = model.prepare(command)

                when (val preparation = controller.prepare(command)) {
                    is NavPreparation.NoChange -> {
                        assertEquals(context, expected.reason, preparation.reason)
                        assertSame(context, before, controller.stackStateSnapshot())
                    }

                    is NavPreparation.Ready -> {
                        val expectedState = checkNotNull(expected.state) {
                            "$context unexpectedly prepared a transaction."
                        }
                        val transaction = preparation.transaction
                        assertSame(context, before, controller.stackStateSnapshot())
                        assertMatches(
                            context = "$context candidate",
                            expected = expectedState,
                            actual = transaction.afterState,
                        )
                        assertMutationMatches(
                            context = context,
                            before = before,
                            after = transaction.afterState,
                            mutation = transaction.mutation,
                        )
                        val beforeIds = before.allEntries.mapTo(hashSetOf(), NavEntry::id)
                        val newlyAllocated = transaction.afterState.allEntries
                            .map(NavEntry::id)
                            .filterNot(beforeIds::contains)
                        assertTrue(
                            "$context reused an allocated entry ID: $newlyAllocated",
                            newlyAllocated.none(allocatedEntryIds::contains),
                        )
                        allocatedEntryIds += newlyAllocated

                        if (random.nextInt(ROLLBACK_RATE) == 0) {
                            transaction.rollback()
                            assertSame(context, before, controller.stackStateSnapshot())
                        } else {
                            transaction.commit()
                            model = expectedState
                            assertSame(
                                context,
                                transaction.afterState,
                                controller.stackStateSnapshot(),
                            )
                        }
                    }
                }

                assertMatches(
                    context = "$context settled",
                    expected = model,
                    actual = controller.stackStateSnapshot(),
                )
                assertControllerInvariants(
                    context = context,
                    controller = controller,
                )
            }
        }
    }

    private fun randomCommand(
        random: Random,
        model: ModelState,
    ): NavCommand {
        return when (random.nextInt(100)) {
            in 0..29 -> NavCommand.Push(
                route = randomRoute(random, model),
                launchMode = if (random.nextBoolean()) {
                    NavLaunchMode.Standard
                } else {
                    NavLaunchMode.SingleTop
                },
            )

            in 30..42 -> NavCommand.Pop
            in 43..52 -> NavCommand.ReplaceTop(randomRoute(random, model))
            in 53..61 -> NavCommand.Reset(randomRoute(random, model))
            in 62..77 -> NavCommand.SelectStack(
                stackId = StackIds[random.nextInt(StackIds.size)],
                selectionMode = if (random.nextInt(3) == 0) {
                    NavStackSelectionMode.PopToRoot
                } else {
                    NavStackSelectionMode.Preserve
                },
            )

            in 78..92 -> {
                val targetStackId = StackIds[random.nextInt(StackIds.size)]
                NavCommand.OpenDeepLink(
                    route = randomRoute(
                        random = random,
                        model = model,
                        stackId = targetStackId,
                    ),
                    targetStackId = targetStackId,
                    launchMode = NavDeepLinkLaunchMode.entries[
                        random.nextInt(NavDeepLinkLaunchMode.entries.size)
                    ],
                )
            }

            else -> model.systemBackCommand() ?: NavCommand.Pop
        }
    }

    private fun randomRoute(
        random: Random,
        model: ModelState,
        stackId: NavStackId = model.activeStackId,
    ): NavRoute {
        if (random.nextInt(5) == 0) {
            return checkNotNull(model.stacks[stackId]).last()
        }
        val arguments = if (random.nextBoolean()) {
            mapOf(
                "value" to NavValue.IntValue(random.nextInt(5)),
            )
        } else {
            emptyMap()
        }
        return NavRoute(
            name = "route-${random.nextInt(12)}",
            arguments = arguments,
        )
    }

    private fun assertMatches(
        context: String,
        expected: ModelState,
        actual: NavStackSetSnapshot,
    ) {
        assertEquals(context, expected.activeStackId, actual.activeStackId)
        assertEquals(context, expected.selectionHistory, actual.selectionHistory)
        assertEquals(context, StackIds, actual.stacks.keys.toList())
        StackIds.forEach { stackId ->
            assertEquals(
                context,
                checkNotNull(expected.stacks[stackId]),
                checkNotNull(actual[stackId]).entries.map(NavEntry::route),
            )
        }
    }

    private fun assertMutationMatches(
        context: String,
        before: NavStackSetSnapshot,
        after: NavStackSetSnapshot,
        mutation: NavStackMutation,
    ) {
        val beforeIds = before.allEntries.mapTo(linkedSetOf(), NavEntry::id)
        val afterIds = after.allEntries.mapTo(linkedSetOf(), NavEntry::id)
        assertEquals(
            context,
            after.allEntries.filter { entry -> entry.id !in beforeIds },
            mutation.added,
        )
        assertEquals(
            context,
            before.allEntries.filter { entry -> entry.id !in afterIds },
            mutation.removed,
        )
        assertEquals(context, before.activeStack.top, mutation.previousTop)
        assertEquals(context, after.activeStack.top, mutation.nextTop)
    }

    private fun assertControllerInvariants(
        context: String,
        controller: NavBackStackController,
    ) {
        val state = controller.stackStateSnapshot()
        assertTrue(context, state.stacks.values.all { stack -> stack.entries.isNotEmpty() })
        assertTrue(context, state.activeStackId !in state.selectionHistory)
        assertEquals(context, state.selectionHistory.distinct(), state.selectionHistory)
        assertTrue(context, state.selectionHistory.all(state.stacks::containsKey))
        assertEquals(context, state.activeStack, controller.snapshot())
        assertEquals(
            context,
            state.allEntries.size,
            state.allEntries.map(NavEntry::id).distinct().size,
        )
        val expectedBackCommand = when {
            state.activeStack.entries.size > 1 -> NavCommand.Pop
            state.selectionHistory.isNotEmpty() -> NavCommand.PopStackHistory
            else -> null
        }
        assertEquals(context, expectedBackCommand, controller.systemBackCommand())
    }

    private fun ModelState.prepare(command: NavCommand): ModelPreparation {
        val activeStack = checkNotNull(stacks[activeStackId])
        return when (command) {
            is NavCommand.Push -> {
                if (
                    command.launchMode == NavLaunchMode.SingleTop &&
                    activeStack.last() == command.route
                ) {
                    noChange(NavNoChangeReason.AlreadyAtDestination)
                } else {
                    changed(withActiveStack(activeStack + command.route))
                }
            }

            NavCommand.Pop -> {
                if (activeStack.size == 1) {
                    noChange(NavNoChangeReason.CannotPopRoot)
                } else {
                    changed(withActiveStack(activeStack.dropLast(1)))
                }
            }

            is NavCommand.ReplaceTop -> {
                if (activeStack.last() == command.route) {
                    noChange(NavNoChangeReason.AlreadyAtDestination)
                } else {
                    changed(withActiveStack(activeStack.dropLast(1) + command.route))
                }
            }

            is NavCommand.Reset -> {
                if (activeStack.size == 1 && activeStack.last() == command.route) {
                    noChange(NavNoChangeReason.AlreadyAtDestination)
                } else {
                    changed(withActiveStack(listOf(command.route)))
                }
            }

            is NavCommand.SelectStack -> {
                val target = checkNotNull(stacks[command.stackId])
                val selected = when (command.selectionMode) {
                    NavStackSelectionMode.Preserve -> target
                    NavStackSelectionMode.PopToRoot -> listOf(target.first())
                }
                if (command.stackId == activeStackId && selected == target) {
                    noChange(NavNoChangeReason.AlreadySelectedStack)
                } else {
                    val nextStacks = LinkedHashMap(stacks).apply {
                        put(command.stackId, selected)
                    }
                    val nextHistory = if (command.stackId == activeStackId) {
                        selectionHistory
                    } else {
                        selectionHistory.filterNot { it == command.stackId } + activeStackId
                    }
                    changed(
                        copy(
                            activeStackId = command.stackId,
                            stacks = nextStacks,
                            selectionHistory = nextHistory,
                        ),
                    )
                }
            }

            is NavCommand.OpenDeepLink -> {
                val targetStackId = command.targetStackId ?: activeStackId
                val target = checkNotNull(stacks[targetStackId])
                val selected = when (command.launchMode) {
                    NavDeepLinkLaunchMode.Push -> target + command.route
                    NavDeepLinkLaunchMode.SingleTop -> {
                        if (target.last() == command.route) target else target + command.route
                    }

                    NavDeepLinkLaunchMode.ReplaceTop -> {
                        if (target.last() == command.route) {
                            target
                        } else {
                            target.dropLast(1) + command.route
                        }
                    }

                    NavDeepLinkLaunchMode.Reset -> {
                        if (target.size == 1 && target.last() == command.route) {
                            target
                        } else {
                            listOf(command.route)
                        }
                    }
                }
                val next = replaceAndSelectStack(
                    targetStackId = targetStackId,
                    routes = selected,
                )
                if (next == this) {
                    noChange(NavNoChangeReason.AlreadyAtDestination)
                } else {
                    changed(next)
                }
            }

            NavCommand.PopStackHistory -> {
                check(activeStack.size == 1)
                val target = selectionHistory.lastOrNull()
                    ?: return noChange(NavNoChangeReason.CannotPopRoot)
                changed(
                    copy(
                        activeStackId = target,
                        selectionHistory = selectionHistory.dropLast(1),
                    ),
                )
            }
        }
    }

    private fun ModelState.withActiveStack(routes: List<NavRoute>): ModelState {
        return copy(
            stacks = LinkedHashMap(stacks).apply {
                put(activeStackId, routes)
            },
        )
    }

    private fun ModelState.replaceAndSelectStack(
        targetStackId: NavStackId,
        routes: List<NavRoute>,
    ): ModelState {
        return copy(
            activeStackId = targetStackId,
            stacks = LinkedHashMap(stacks).apply {
                put(targetStackId, routes)
            },
            selectionHistory = if (targetStackId == activeStackId) {
                selectionHistory
            } else {
                selectionHistory.filterNot { it == targetStackId } + activeStackId
            },
        )
    }

    private fun ModelState.systemBackCommand(): NavCommand? {
        return when {
            checkNotNull(stacks[activeStackId]).size > 1 -> NavCommand.Pop
            selectionHistory.isNotEmpty() -> NavCommand.PopStackHistory
            else -> null
        }
    }

    private fun ModelState.changed(next: ModelState): ModelPreparation {
        return ModelPreparation(state = next)
    }

    private fun ModelState.noChange(reason: NavNoChangeReason): ModelPreparation {
        return ModelPreparation(reason = reason)
    }

    private fun configuration(): NavStackConfiguration {
        return NavStackConfiguration(
            initialStackId = HomeStack,
            stacks = listOf(
                NavStackSpec(HomeStack, NavRoute("home")),
                NavStackSpec(SearchStack, NavRoute("search")),
                NavStackSpec(SettingsStack, NavRoute("settings")),
            ),
            rootBackBehavior = NavRootBackBehavior.PreviousStack,
        )
    }

    private fun initialModel(): ModelState {
        return ModelState(
            activeStackId = HomeStack,
            stacks = linkedMapOf(
                HomeStack to listOf(NavRoute("home")),
                SearchStack to listOf(NavRoute("search")),
                SettingsStack to listOf(NavRoute("settings")),
            ),
        )
    }

    private data class ModelPreparation(
        val state: ModelState? = null,
        val reason: NavNoChangeReason? = null,
    )

    private data class ModelState(
        val activeStackId: NavStackId,
        val stacks: Map<NavStackId, List<NavRoute>>,
        val selectionHistory: List<NavStackId> = emptyList(),
    )

    private companion object {
        val HomeStack = NavStackId("home")
        val SearchStack = NavStackId("search")
        val SettingsStack = NavStackId("settings")
        val StackIds = listOf(HomeStack, SearchStack, SettingsStack)

        const val BASE_SEED = 0x51A7
        const val SEED_COUNT = 64
        const val OPERATIONS_PER_SEED = 2_000
        const val ROLLBACK_RATE = 5
    }
}
