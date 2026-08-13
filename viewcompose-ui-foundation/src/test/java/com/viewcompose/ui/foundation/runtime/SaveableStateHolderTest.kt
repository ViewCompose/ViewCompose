package com.viewcompose.ui.foundation

import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveableStateHolderTest {
    @Test
    fun `sibling child registries isolate equal automatic and explicit keys`() {
        val holder = SaveableStateHolder.create(bundleLikeRegistry())
        val first = ChildComposition(holder.acquire("first"))
        val second = ChildComposition(holder.acquire("second"))

        first.render(initial = "first")
        second.render(initial = "second")
        first.auto.value = "first-auto-edited"
        first.explicit.value = "first-explicit-edited"
        second.auto.value = "second-auto-edited"
        second.explicit.value = "second-explicit-edited"

        val saved = holder.save().deepValues()

        assertTrue(saved.containsAll(
            listOf(
                "first-auto-edited",
                "first-explicit-edited",
                "second-auto-edited",
                "second-explicit-edited",
            ),
        ))
        first.close()
        second.close()
    }

    @Test
    fun `recycling and keyed reorder restore state by logical key`() {
        val holder = SaveableStateHolder.create(bundleLikeRegistry())
        val first = ChildComposition(holder.acquire("first")).also {
            it.render("first")
            it.auto.value = "state-for-first"
            it.close()
        }
        val second = ChildComposition(holder.acquire("second")).also {
            it.render("second")
            it.auto.value = "state-for-second"
            it.close()
        }

        val reorderedSecond = ChildComposition(holder.acquire("second")).also { it.render("fallback") }
        val reorderedFirst = ChildComposition(holder.acquire("first")).also { it.render("fallback") }

        assertEquals("state-for-second", reorderedSecond.auto.value)
        assertEquals("state-for-first", reorderedFirst.auto.value)
        reorderedSecond.close()
        reorderedFirst.close()
    }

    @Test
    fun `parent registry saves and restores nested child maps across host recreation`() {
        val firstRoot = bundleLikeRegistry()
        val firstParent = ComposerRuntimeHarness()
        lateinit var firstHolder: SaveableStateHolder
        firstParent.render {
            UiTreeBuilder().apply {
                ProvideSaveableStateRegistry(firstRoot) {
                    firstHolder = checkNotNull(rememberSaveableStateHolder())
                }
            }
        }
        val firstChild = ChildComposition(firstHolder.acquire("page-a"))
        firstChild.render("fallback")
        firstChild.explicit.value = "restored-after-host-recreation"

        val savedRoot = firstRoot.performSave()
        firstChild.close()
        firstParent.dispose()

        val secondRoot = bundleLikeRegistry(savedRoot)
        val secondParent = ComposerRuntimeHarness()
        lateinit var restoredHolder: SaveableStateHolder
        secondParent.render {
            UiTreeBuilder().apply {
                ProvideSaveableStateRegistry(secondRoot) {
                    restoredHolder = checkNotNull(rememberSaveableStateHolder())
                }
            }
        }
        val restoredChild = ChildComposition(restoredHolder.acquire("page-a"))
        restoredChild.render("fallback")

        assertEquals("restored-after-host-recreation", restoredChild.explicit.value)
        restoredChild.close()
        secondParent.dispose()
    }

    @Test
    fun `nested holder is saved inside its parent child registry`() {
        val outer = SaveableStateHolder.create(bundleLikeRegistry())
        val outerLease = outer.acquire("outer-item")
        val parentHarness = ComposerRuntimeHarness()
        lateinit var nested: SaveableStateHolder
        parentHarness.render {
            UiTreeBuilder().apply {
                ProvideSaveableStateRegistry(outerLease.registry) {
                    nested = checkNotNull(rememberSaveableStateHolder())
                }
            }
        }
        val nestedChild = ChildComposition(nested.acquire("inner-item"))
        nestedChild.render("inner")
        nestedChild.auto.value = "nested-edited"

        assertTrue("nested-edited" in outer.save().deepValues())
        nestedChild.close()
        parentHarness.dispose()
        outerLease.close()
    }

    @Test
    fun `concurrent presentation replica cannot overwrite logical owner`() {
        val holder = SaveableStateHolder.create(bundleLikeRegistry())
        val owner = ChildComposition(holder.acquire("sticky"))
        owner.render("owner")
        owner.auto.value = "owner-current"

        val replica = ChildComposition(holder.acquire("sticky"))
        replica.render("replica-fallback")
        assertEquals("owner-current", replica.auto.value)
        replica.auto.value = "replica-only"
        replica.close()

        assertTrue("owner-current" in holder.save().deepValues())
        assertTrue("replica-only" !in holder.save().deepValues())
        owner.close()
    }

    @Test
    fun `committed key removal drops retained child state`() {
        val holder = SaveableStateHolder.create(bundleLikeRegistry())
        val child = ChildComposition(holder.acquire("removed"))
        child.render("removed")
        child.auto.value = "must-not-survive"
        child.close()

        holder.retainKeys(setOf("remaining"))

        assertTrue("must-not-survive" !in holder.save().deepValues())
    }

    @Test
    fun `pre hierarchy flat value is discarded instead of crashing restoration`() {
        val registry = bundleLikeRegistry()

        val restored = SaveableStateHolder.restore(
            parentRegistry = registry,
            saved = autoSaver<MutableState<Int>>().save(mutableStateOf(3)),
        )

        assertEquals(
            listOf(
                "com.viewcompose.ui.foundation.runtime.saveable.SaveableStateHolder",
                1,
                emptyList<Any?>(),
            ),
            restored.save(),
        )
    }

    @Test
    fun `pre hierarchy non list value at holder slot is discarded during composition`() {
        val registry = bundleLikeRegistry(
            restored = mapOf(
                "auto:root:0:1" to mapOf("legacy" to "flat-child-state"),
            ),
        )
        val harness = ComposerRuntimeHarness()
        lateinit var holder: SaveableStateHolder

        harness.render {
            UiTreeBuilder().apply {
                ProvideSaveableStateRegistry(registry) {
                    holder = checkNotNull(rememberSaveableStateHolder())
                }
            }
        }

        assertTrue("flat-child-state" !in holder.save().deepValues())
        harness.dispose()
    }

    @Test
    fun `failed parent composition does not publish candidate retained keys`() {
        val registry = bundleLikeRegistry()
        val harness = ComposerRuntimeHarness()
        lateinit var holder: SaveableStateHolder
        harness.render {
            UiTreeBuilder().apply {
                ProvideSaveableStateRegistry(registry) {
                    holder = checkNotNull(rememberSaveableStateHolder())
                }
            }
        }
        val child = ChildComposition(holder.acquire("kept"))
        child.render("kept")
        child.auto.value = "survives-parent-abort"
        child.close()

        val error = runCatching {
            harness.render {
                UiTreeBuilder().apply {
                    ProvideSaveableStateRegistry(registry) {
                        val candidate = checkNotNull(rememberSaveableStateHolder())
                        assertSame(holder, candidate)
                        SideEffect {
                            candidate.retainKeys(emptySet())
                        }
                        error("abort parent")
                    }
                }
            }
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("abort parent"))
        assertTrue("survives-parent-abort" in holder.save().deepValues())
        harness.dispose()
    }

    @Test
    fun `unsupported logical key fails when child registers saveable state`() {
        val parentRegistry = createSaveableStateRegistry(
            canBeSaved = { value -> value == null || value is String || value is List<*> },
        )
        val holder = SaveableStateHolder.create(parentRegistry)
        val child = ChildComposition(holder.acquire(UnsupportedKey(1)))

        val error = runCatching { child.render("value") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("cannot own saveable state"))
        child.closeAfterFailure()
    }

    @Test
    fun `duplicate provider inside one child scope remains an error`() {
        val holder = SaveableStateHolder.create(bundleLikeRegistry())
        val lease = holder.acquire("child")
        val first = lease.registry.registerProvider("shared") { "first" }

        val error = runCatching {
            lease.registry.registerProvider("shared") { "second" }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("already registered"))
        first.unregister()
        lease.close()
    }

    @Test
    fun `corrupt nested holder state is isolated to that holder`() {
        val registry = bundleLikeRegistry()
        val restored = SaveableStateHolder.restore(
            parentRegistry = registry,
            saved = listOf(
                "com.viewcompose.ui.foundation.runtime.saveable.SaveableStateHolder",
                1,
                listOf(listOf("child", listOf("not-a-map"))),
            ),
        )

        assertTrue(restored.save().deepValues().none { it == "child" })
    }

    private class ChildComposition(
        private val lease: SaveableStateRegistryLease,
    ) {
        private val harness = ComposerRuntimeHarness()
        lateinit var auto: MutableState<String>
        lateinit var explicit: MutableState<String>

        fun render(initial: String) {
            harness.render {
                UiTreeBuilder().apply {
                    ProvideSaveableStateRegistry(lease.registry) {
                        auto = rememberSaveable { mutableStateOf("$initial-auto") }
                        explicit = rememberSaveable(key = "field") {
                            mutableStateOf("$initial-explicit")
                        }
                    }
                }
            }
        }

        fun close() {
            harness.dispose()
            lease.close()
        }

        fun closeAfterFailure() {
            runCatching(harness::dispose)
            runCatching(lease::close)
        }
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
                is Map<*, *> -> value.all { (key, item) -> key is String && canSave(item) }
                else -> false
            }
        }
        return createSaveableStateRegistry(
            restoredValues = restored,
            canBeSaved = ::canSave,
        )
    }

    private fun Any?.deepValues(): List<Any?> {
        return when (this) {
            is Map<*, *> -> values.flatMap { it.deepValues() }
            is Iterable<*> -> flatMap { it.deepValues() }
            else -> listOf(this)
        }
    }

    private data class UnsupportedKey(val value: Int)
}
