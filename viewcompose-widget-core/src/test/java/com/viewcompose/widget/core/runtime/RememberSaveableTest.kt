package com.viewcompose.widget.core

import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.text.TextFieldState
import com.viewcompose.text.TextFieldValue
import com.viewcompose.text.TextSpanStyle
import com.viewcompose.text.TextRange
import com.viewcompose.text.textDocument
import com.viewcompose.ui.state.LazyListConnector
import com.viewcompose.ui.state.LazyListStateSnapshot
import kotlinx.coroutines.Dispatchers
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
    fun `released restored claim can be consumed by a later composition`() {
        val registry = createSaveableStateRegistry(
            restoredValues = mapOf("counter" to 7),
        )
        val claim = registry.claimRestored("counter")

        assertEquals(7, claim?.value)
        assertNull(registry.claimRestored("counter"))

        claim?.release()

        assertEquals(7, registry.consumeRestored("counter")?.value)
    }

    @Test
    fun `performSave includes a restored value claimed by an in-flight composition`() {
        val registry = createSaveableStateRegistry(
            restoredValues = mapOf("counter" to 11),
        )
        val claim = registry.claimRestored("counter")

        assertEquals(mapOf("counter" to 11), registry.performSave())

        claim?.commit()
        assertTrue(registry.performSave().isEmpty())
    }

    @Test
    fun `aborted composition releases restored rememberSaveable value`() {
        val registry = bundleLikeRegistry(
            restored = mapOf("user:counter" to autoSaver<MutableState<Int>>().save(mutableStateOf(23))),
        )
        val composer = ComposerLite()
        lateinit var firstAttemptState: MutableState<Int>
        val prepared = ComposerContext.withComposer(
            composer = composer,
            coroutineContext = Dispatchers.Unconfined,
        ) {
            composer.prepareRoot {
                UiTreeBuilder().apply {
                    ProvideSaveableStateRegistry(registry) {
                        firstAttemptState = rememberSaveable(key = "counter") {
                            mutableStateOf(-1)
                        }
                    }
                }
            }
        }

        assertEquals(23, firstAttemptState.value)
        prepared.abort()

        lateinit var retryState: MutableState<Int>
        val retry = ComposerContext.withComposer(
            composer = composer,
            coroutineContext = Dispatchers.Unconfined,
        ) {
            composer.prepareRoot {
                UiTreeBuilder().apply {
                    ProvideSaveableStateRegistry(registry) {
                        retryState = rememberSaveable(key = "counter") {
                            mutableStateOf(-1)
                        }
                    }
                }
            }
        }
        retry.commit()

        assertEquals(23, retryState.value)
        assertTrue(registry.performSave().containsKey("user:counter"))
        composer.dispose()
    }

    @Test
    fun `aborted composition does not publish a replacement saver`() {
        val registry = bundleLikeRegistry()
        val composer = ComposerLite()
        val originalSaver = Saver<Int, String>(
            save = { value -> "old:$value" },
            restore = { saved -> saved.substringAfter(':').toInt() },
        )
        val replacementSaver = Saver<Int, String>(
            save = { value -> "new:$value" },
            restore = { saved -> saved.substringAfter(':').toInt() },
        )

        fun prepare(saver: Saver<Int, String>) = ComposerContext.withComposer(
            composer = composer,
            coroutineContext = Dispatchers.Unconfined,
        ) {
            composer.prepareRoot {
                UiTreeBuilder().apply {
                    ProvideSaveableStateRegistry(registry) {
                        rememberSaveable(
                            key = "value",
                            saver = saver,
                        ) {
                            5
                        }
                    }
                }
            }
        }

        prepare(originalSaver).commit()
        composer.commitSideEffects()
        composer.requestRootRecompose()
        prepare(replacementSaver).abort()

        assertEquals("old:5", registry.performSave().getValue("user:value"))
        composer.dispose()
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
                override fun scrollToItem(
                    index: Int,
                    scrollOffset: Int,
                    animated: Boolean,
                ) = Unit

                override fun currentSnapshot(): LazyListStateSnapshot {
                    return LazyListStateSnapshot.initial(
                        firstVisibleItemIndex = 18,
                        firstVisibleItemScrollOffset = 31,
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
                override fun scrollToItem(
                    index: Int,
                    scrollOffset: Int,
                    animated: Boolean,
                ) {
                    restoredCalls += Triple(index, scrollOffset, animated)
                }
            },
        )

        assertEquals(listOf(Triple(18, 31, false)), restoredCalls)
        secondHarness.dispose()
    }

    @Test
    fun `remember text field state restores text and directional selection only`() {
        val firstRegistry = bundleLikeRegistry()
        val firstHarness = ComposerRuntimeHarness()
        val firstState = renderTextFieldState(
            harness = firstHarness,
            registry = firstRegistry,
        )
        firstState.updateFromInput(
            TextFieldValue(
                text = "ni",
                selection = TextRange(2, 0),
                composition = TextRange(0, 2),
            ),
        )
        val saved = firstRegistry.performSave()
        firstHarness.dispose()

        val secondHarness = ComposerRuntimeHarness()
        val restoredState = renderTextFieldState(
            harness = secondHarness,
            registry = bundleLikeRegistry(saved),
        )

        assertEquals("ni", restoredState.text)
        assertEquals(TextRange(2, 0), restoredState.selection)
        assertNull(restoredState.composition)
        assertTrue(!restoredState.canUndo)
        secondHarness.dispose()
    }

    @Test
    fun `remember text field state restores rich document annotations`() {
        val firstRegistry = bundleLikeRegistry()
        val firstHarness = ComposerRuntimeHarness()
        val firstState = renderTextFieldState(
            harness = firstHarness,
            registry = firstRegistry,
        )
        val richDocument = textDocument {
            append("saved", TextSpanStyle(fontWeight = 700))
        }
        firstState.setDocumentAndPlaceCursorAtEnd(richDocument)
        val saved = firstRegistry.performSave()
        firstHarness.dispose()

        val secondHarness = ComposerRuntimeHarness()
        val restoredState = renderTextFieldState(
            harness = secondHarness,
            registry = bundleLikeRegistry(saved),
        )

        assertEquals(richDocument, restoredState.document)
        assertEquals(TextRange(5), restoredState.selection)
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

    private fun renderTextFieldState(
        harness: ComposerRuntimeHarness,
        registry: SaveableStateRegistry,
    ): TextFieldState {
        lateinit var state: TextFieldState
        harness.render {
            UiTreeBuilder().apply {
                ProvideSaveableStateRegistry(registry) {
                    state = rememberTextFieldState(initialText = "fallback")
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
