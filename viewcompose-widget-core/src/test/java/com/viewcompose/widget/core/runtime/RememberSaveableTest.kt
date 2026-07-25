package com.viewcompose.widget.core

import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.state.LazyListConnector
import com.viewcompose.ui.state.LazyListPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RememberSaveableTest {
    @Test
    fun `registry preserves nullable restored values`() {
        val registry = createSaveableStateRegistry(
            restoredValues = mapOf("nullable" to null),
        )

        val restored = registry.consumeRestored("nullable")

        assertTrue(restored != null)
        assertNull(restored?.value)
        assertNull(registry.consumeRestored("nullable"))
    }

    @Test
    fun `unregistered provider retains latest value for composition recreation`() {
        val registry = createSaveableStateRegistry()
        var value = 1
        val entry = registry.registerProvider("counter") { value }
        value = 7

        entry.unregister()

        assertEquals(7, registry.consumeRestored("counter")?.value)
    }

    @Test
    fun `rememberSaveable restores mutable state across composer recreation`() {
        val firstRegistry = bundleLikeRegistry()
        val firstHarness = ComposerRuntimeHarness()
        val firstState = renderCounter(
            harness = firstHarness,
            registry = firstRegistry,
        )
        firstState.value = 42
        val saved = firstRegistry.performSave()
        firstHarness.dispose()

        val secondRegistry = bundleLikeRegistry(saved)
        val secondHarness = ComposerRuntimeHarness()
        val restoredState = renderCounter(
            harness = secondHarness,
            registry = secondRegistry,
        )

        assertEquals(42, restoredState.value)
        secondHarness.dispose()
    }

    @Test
    fun `rememberSaveable custom map saver restores domain value`() {
        val citySaver = mapSaver<City>(
            save = { city ->
                mapOf(
                    "name" to city.name,
                    "population" to city.population,
                )
            },
            restore = { values ->
                City(
                    name = values.getValue("name") as String,
                    population = values.getValue("population") as Int,
                )
            },
        )
        val firstRegistry = bundleLikeRegistry()
        val firstHarness = ComposerRuntimeHarness()
        var city = City("", 0)
        firstHarness.render {
            UiTreeBuilder().apply {
                ProvideSaveableStateRegistry(firstRegistry) {
                    city = rememberSaveable(saver = citySaver) {
                        City("Shanghai", 24_870_000)
                    }
                }
            }
        }
        val saved = firstRegistry.performSave()
        firstHarness.dispose()

        val secondHarness = ComposerRuntimeHarness()
        secondHarness.render {
            UiTreeBuilder().apply {
                ProvideSaveableStateRegistry(bundleLikeRegistry(saved)) {
                    city = rememberSaveable(saver = citySaver) {
                        City("fallback", 0)
                    }
                }
            }
        }

        assertEquals(City("Shanghai", 24_870_000), city)
        secondHarness.dispose()
    }

    @Test
    fun `rememberSaveable inputs reset value and save replacement`() {
        val registry = bundleLikeRegistry()
        val harness = ComposerRuntimeHarness()
        var input = "first"
        var state = renderInputState(
            harness = harness,
            registry = registry,
            input = input,
        )
        state.value = "edited"

        input = "second"
        state = renderInputState(
            harness = harness,
            registry = registry,
            input = input,
        )

        assertEquals("second", state.value)
        val savedValue = registry.performSave().values.single() as List<*>
        assertEquals("second", savedValue[3])
        harness.dispose()
    }

    @Test
    fun `autoSaver preserves lists that resemble internal envelopes`() {
        val original = listOf(
            "com.viewcompose.widget.core.runtime.saveable.AutoSaver",
            1,
            1,
            "ordinary value",
        )
        val saver = autoSaver<List<Any?>>()

        val restored = saver.restore(saver.save(original))

        assertEquals(original, restored)
    }

    @Test
    fun `performSave rejects values outside host save policy`() {
        val registry = createSaveableStateRegistry(
            canBeSaved = { value -> value == null || value is String },
        )
        registry.registerProvider("unsupported") { Any() }

        val error = runCatching {
            registry.performSave()
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("unsupported"))
    }

    @Test
    fun `rememberLazyListState restores captured item and offset`() {
        val firstRegistry = bundleLikeRegistry()
        val firstHarness = ComposerRuntimeHarness()
        lateinit var firstState: com.viewcompose.ui.state.LazyListState
        firstHarness.render {
            UiTreeBuilder().apply {
                ProvideSaveableStateRegistry(firstRegistry) {
                    firstState = rememberLazyListState()
                }
            }
        }
        firstState.attach(
            object : LazyListConnector {
                override fun scrollToPosition(index: Int, smooth: Boolean) = Unit

                override fun currentPosition(): LazyListPosition {
                    return LazyListPosition(
                        index = 18,
                        scrollOffset = 31,
                    )
                }
            },
        )
        val saved = firstRegistry.performSave()
        firstHarness.dispose()

        val restoredCalls = mutableListOf<Triple<Int, Int, Boolean>>()
        val secondHarness = ComposerRuntimeHarness()
        lateinit var restoredState: com.viewcompose.ui.state.LazyListState
        secondHarness.render {
            UiTreeBuilder().apply {
                ProvideSaveableStateRegistry(bundleLikeRegistry(saved)) {
                    restoredState = rememberLazyListState()
                }
            }
        }
        restoredState.attach(
            object : LazyListConnector {
                override fun scrollToPosition(index: Int, smooth: Boolean) = Unit

                override fun scrollToPosition(
                    index: Int,
                    scrollOffset: Int,
                    smooth: Boolean,
                ) {
                    restoredCalls += Triple(index, scrollOffset, smooth)
                }
            },
        )

        assertEquals(listOf(Triple(18, 31, false)), restoredCalls)
        secondHarness.dispose()
    }

    private fun renderCounter(
        harness: ComposerRuntimeHarness,
        registry: SaveableStateRegistry,
    ): MutableState<Int> {
        lateinit var state: MutableState<Int>
        harness.render {
            UiTreeBuilder().apply {
                ProvideSaveableStateRegistry(registry) {
                    state = rememberSaveable {
                        mutableStateOf(0)
                    }
                }
            }
        }
        return state
    }

    private fun renderInputState(
        harness: ComposerRuntimeHarness,
        registry: SaveableStateRegistry,
        input: String,
    ): MutableState<String> {
        lateinit var state: MutableState<String>
        harness.render {
            UiTreeBuilder().apply {
                ProvideSaveableStateRegistry(registry) {
                    state = rememberSaveable(input) {
                        mutableStateOf(input)
                    }
                }
            }
        }
        return state
    }

    private fun bundleLikeRegistry(
        restored: Map<String, Any?> = emptyMap(),
    ): SaveableStateRegistry {
        fun canSave(value: Any?): Boolean {
            return when (value) {
                null,
                is String,
                is Int,
                is Long,
                is Float,
                is Double,
                is Boolean,
                -> true
                is List<*> -> value.all(::canSave)
                is Map<*, *> -> value.all { (key, item) ->
                    key is String && canSave(item)
                }
                else -> false
            }
        }
        return createSaveableStateRegistry(
            restoredValues = restored,
            canBeSaved = ::canSave,
        )
    }

    private data class City(
        val name: String,
        val population: Int,
    )
}
