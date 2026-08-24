package com.viewcompose.lifecycle

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.viewcompose.host.android.AndroidView
import com.viewcompose.host.android.AndroidViewCommitScope
import com.viewcompose.host.android.AndroidViewCreateScope
import com.viewcompose.host.android.AndroidViewUpdateScope
import com.viewcompose.host.android.renderInto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class AndroidViewSavedStateBindingTest {
    @Test
    fun `process recreation restores the latest committed View snapshot once`() {
        val firstOwner = TestSavedStateOwner()
        val firstResults = mutableListOf<AndroidViewSavedStateBindResult>()
        var state = SavedAdapterState(firstOwner, value = "first")
        val firstRoot = FrameLayout(RuntimeEnvironment.getApplication())
        val firstSession = renderInto(firstRoot) {
            AndroidView(
                adapter = SavedRecordingAdapter(firstResults, formatVersion = 1),
                state = state,
                key = "saved",
            )
        }
        assertTrue(firstResults.single() is AndroidViewSavedStateBindResult.Initial)
        assertNull((firstResults.single() as AndroidViewSavedStateBindResult.Initial).restoredState)

        state = SavedAdapterState(firstOwner, value = "latest")
        firstSession.render()
        assertEquals(AndroidViewSavedStateBindResult.Retained, firstResults.last())
        val saved = firstOwner.performSave()
        firstSession.dispose()
        firstOwner.destroy()

        val secondOwner = TestSavedStateOwner(restoredState = saved)
        val secondResults = mutableListOf<AndroidViewSavedStateBindResult>()
        val secondRoot = FrameLayout(RuntimeEnvironment.getApplication())
        val secondSession = renderInto(secondRoot) {
            AndroidView(
                adapter = SavedRecordingAdapter(secondResults, formatVersion = 1),
                state = SavedAdapterState(secondOwner, value = "replacement"),
                key = "saved",
            )
        }

        val restored = secondResults.single() as AndroidViewSavedStateBindResult.Initial
        assertEquals("latest", restored.restoredState?.getString("value"))

        secondSession.dispose()
        secondOwner.destroy()
    }

    @Test
    fun `incompatible format is isolated without blocking a new provider`() {
        val firstOwner = TestSavedStateOwner()
        val firstRoot = FrameLayout(RuntimeEnvironment.getApplication())
        val firstSession = renderInto(firstRoot) {
            AndroidView(
                adapter = SavedRecordingAdapter(mutableListOf(), formatVersion = 1),
                state = SavedAdapterState(firstOwner, value = "old"),
                key = "saved",
            )
        }
        val saved = firstOwner.performSave()
        firstSession.dispose()
        firstOwner.destroy()

        val restoredOwner = TestSavedStateOwner(restoredState = saved)
        val results = mutableListOf<AndroidViewSavedStateBindResult>()
        val restoredRoot = FrameLayout(RuntimeEnvironment.getApplication())
        val restoredSession = renderInto(restoredRoot) {
            AndroidView(
                adapter = SavedRecordingAdapter(results, formatVersion = 2),
                state = SavedAdapterState(restoredOwner, value = "new"),
                key = "saved",
            )
        }

        assertNull(
            (results.single() as AndroidViewSavedStateBindResult.Initial).restoredState,
        )
        val nextSaved = restoredOwner.performSave()
        assertTrue(nextSaved.keySet().isNotEmpty())

        restoredSession.dispose()
        restoredOwner.destroy()
    }

    @Test
    fun `lifecycle adapter release automatically removes its saved state provider`() {
        val owner = TestSavedStateOwner()
        val firstRoot = FrameLayout(RuntimeEnvironment.getApplication())
        val firstSession = renderInto(firstRoot) {
            AndroidView(
                adapter = SavedRecordingAdapter(mutableListOf(), formatVersion = 1),
                state = SavedAdapterState(owner, value = "first"),
                key = "first-view",
            )
        }

        firstSession.dispose()

        val secondRoot = FrameLayout(RuntimeEnvironment.getApplication())
        val secondSession = renderInto(secondRoot) {
            AndroidView(
                adapter = SavedRecordingAdapter(mutableListOf(), formatVersion = 1),
                state = SavedAdapterState(owner, value = "second"),
                key = "second-view",
            )
        }

        secondSession.dispose()
        owner.destroy()
    }

    private data class SavedAdapterState(
        val owner: TestSavedStateOwner,
        val value: String,
    )

    private class SavedRecordingAdapter(
        private val results: MutableList<AndroidViewSavedStateBindResult>,
        private val formatVersion: Int,
    ) : LifecycleAndroidViewAdapter<View, SavedAdapterState>() {
        override fun lifecycleOwner(state: SavedAdapterState): LifecycleOwner = state.owner

        override fun create(scope: AndroidViewCreateScope): View = View(scope.context)

        override fun update(scope: AndroidViewUpdateScope<View>, state: SavedAdapterState) {
            scope.view.contentDescription = state.value
        }

        override fun onViewCommit(
            scope: AndroidViewCommitScope<View>,
            state: SavedAdapterState,
        ) {
            results += scope.bindAndroidViewSavedState(
                owner = state.owner,
                key = "fixture",
                formatVersion = formatVersion,
            ) {
                Bundle().apply {
                    putString("value", view.contentDescription.toString())
                }
            }
        }

        override fun onLifecycleEvent(
            scope: AndroidViewLifecycleEventScope<View>,
            state: SavedAdapterState,
            event: Lifecycle.Event,
        ) = Unit

    }

    private class TestSavedStateOwner(
        restoredState: Bundle? = null,
    ) : SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry.createUnsafe(this)
        private val controller = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry
            get() = controller.savedStateRegistry

        init {
            controller.performAttach()
            controller.performRestore(restoredState)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }

        fun performSave(): Bundle = Bundle().also(controller::performSave)

        fun destroy() {
            if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
                lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            }
        }
    }
}
