package com.viewcompose.navigation

/*
 * 测试职责：覆盖 Android navigation runtime 中的 Nav Entry Owner 行为，防止导航契约在后续重构中回退。
 * Test responsibility: covers Nav Entry Owner behavior in Android navigation runtime and guards navigation contracts against regressions.
 */

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.viewcompose.lifecycle.LocalLifecycleOwner
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryLifecycleState
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavValue
import com.viewcompose.viewmodel.LocalViewModelStoreOwner
import com.viewcompose.widget.core.LocalSaveableStateRegistry
import com.viewcompose.widget.core.buildVNodeTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NavEntryOwnerTest {
    @Test
    fun `owner maps framework lifecycle to Android lifecycle`() {
        val owner = owner(entry("root", "home"))

        assertEquals(Lifecycle.State.INITIALIZED, owner.lifecycle.currentState)

        owner.moveTo(NavEntryLifecycleState.Resumed)
        assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)

        owner.moveTo(NavEntryLifecycleState.Created)
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)

        owner.moveTo(NavEntryLifecycleState.Destroyed)
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        assertThrows<IllegalStateException> {
            owner.moveTo(NavEntryLifecycleState.Created)
        }
    }

    @Test
    fun `route arguments become SavedStateHandle defaults`() {
        val owner = owner(
            NavEntry(
                id = NavEntryId("editor"),
                route = NavRoute(
                    name = "editor",
                    arguments = mapOf(
                        "documentId" to NavValue.LongValue(42L),
                        "editable" to NavValue.BooleanValue(true),
                        "title" to NavValue.Text("Draft"),
                        "optional" to NavValue.Null,
                    ),
                ),
            ),
        )
        owner.moveTo(NavEntryLifecycleState.Created)

        val viewModel = owner.viewModel<SavedStateViewModel>("editor-vm")

        assertEquals(42L, viewModel.handle["documentId"])
        assertEquals(true, viewModel.handle["editable"])
        assertEquals("Draft", viewModel.handle["title"])
        assertTrue(viewModel.handle.contains("optional"))
        assertNull(viewModel.handle["optional"])
    }

    @Test
    fun `SavedStateHandle survives owner save and recreation`() {
        val entry = entry("editor", "editor")
        val first = owner(entry)
        first.moveTo(NavEntryLifecycleState.Created)
        first.viewModel<SavedStateViewModel>("editor-vm").handle["cursor"] = 17

        val saved = first.performSave()
        first.moveTo(NavEntryLifecycleState.Destroyed)

        val restored = owner(
            entry = entry,
            restoredState = saved,
        )
        restored.moveTo(NavEntryLifecycleState.Created)

        assertEquals(
            17,
            restored.viewModel<SavedStateViewModel>("editor-vm").handle["cursor"],
        )
    }

    @Test
    fun `composition saveable state survives owner save and recreation`() {
        val entry = entry("details", "details")
        val first = owner(entry)
        first.compositionSaveableStateRegistry.registerProvider("scroll") { 88 }

        val saved = first.performSave()
        first.moveTo(NavEntryLifecycleState.Destroyed)

        val restored = owner(
            entry = entry,
            restoredState = saved,
        )
        val restoredValue = restored.compositionSaveableStateRegistry
            .consumeRestored("scroll")

        assertEquals(88, restoredValue?.value)
    }

    @Test
    fun `destroy clears ViewModelStore exactly once`() {
        val owner = owner(entry("root", "home"))
        owner.moveTo(NavEntryLifecycleState.Created)
        val viewModel = owner.viewModel<ClearedViewModel>("cleared-vm")

        owner.moveTo(NavEntryLifecycleState.Destroyed)
        owner.moveTo(NavEntryLifecycleState.Destroyed)

        assertTrue(viewModel.cleared)
        assertEquals(1, viewModel.clearCount)
    }

    @Test
    fun `entry owner environment injects all page scoped locals`() {
        val owner = owner(entry("root", "home"))
        var lifecycleOwner: Any? = null
        var viewModelOwner: Any? = null
        var saveableRegistry: Any? = null

        buildVNodeTree {
            ProvideNavEntryOwner(owner) {
                lifecycleOwner = LocalLifecycleOwner.current
                viewModelOwner = LocalViewModelStoreOwner.current
                saveableRegistry = LocalSaveableStateRegistry.current
            }
        }

        assertSame(owner, lifecycleOwner)
        assertSame(owner, viewModelOwner)
        assertSame(owner.compositionSaveableStateRegistry, saveableRegistry)
        assertNull(LocalLifecycleOwner.current)
        assertNull(LocalViewModelStoreOwner.current)
        assertNull(LocalSaveableStateRegistry.current)
    }

    @Test
    fun `owner cannot save after permanent destruction`() {
        val owner = owner(entry("root", "home"))
        owner.moveTo(NavEntryLifecycleState.Destroyed)

        assertThrows<IllegalStateException> {
            owner.performSave()
        }
    }

    private fun owner(
        entry: NavEntry,
        restoredState: android.os.Bundle? = null,
    ): NavEntryOwner {
        return NavEntryOwner(
            entry = entry,
            application = RuntimeEnvironment.getApplication(),
            restoredState = restoredState,
        )
    }

    private fun entry(
        id: String,
        route: String,
    ): NavEntry {
        return NavEntry(
            id = NavEntryId(id),
            route = NavRoute(route),
        )
    }

    private inline fun <reified VM : ViewModel> NavEntryOwner.viewModel(key: String): VM {
        return ViewModelProvider(
            viewModelStore,
            defaultViewModelProviderFactory,
            defaultViewModelCreationExtras,
        )[key, VM::class.java]
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

    class SavedStateViewModel(
        val handle: SavedStateHandle,
    ) : ViewModel()

    class ClearedViewModel : ViewModel() {
        var cleared: Boolean = false
        var clearCount: Int = 0

        override fun onCleared() {
            cleared = true
            clearCount += 1
        }
    }
}
