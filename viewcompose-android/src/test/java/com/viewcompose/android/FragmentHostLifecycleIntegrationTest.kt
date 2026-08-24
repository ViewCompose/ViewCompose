package com.viewcompose.android

/*
 * Test responsibility: verifies that Fragment hosting follows each View lifecycle generation while
 * retaining Fragment-scoped ViewModel and saved-state ownership.
 */

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import com.viewcompose.lifecycle.LocalLifecycleOwner
import com.viewcompose.lifecycle.LocalSavedStateRegistryOwner
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.LocalSaveableStateRegistry
import com.viewcompose.ui.foundation.SaveableStateRegistry
import com.viewcompose.ui.foundation.Text
import com.viewcompose.viewmodel.LocalViewModelStoreOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FragmentHostLifecycleIntegrationTest {
    @Test
    fun `content follows recreated Fragment view lifecycle owner`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java)
            .setup()
            .get()
        val fragment = LifecycleCapturingFragment()

        activity.supportFragmentManager.beginTransaction()
            .add(fragment, "host")
            .commitNow()

        val firstOwner = fragment.viewLifecycleOwner
        assertSame(firstOwner, fragment.capturedOwner)
        assertSame(fragment, fragment.capturedSavedStateRegistryOwner)
        assertSame(fragment, fragment.capturedViewModelStoreOwner)
        assertEquals(1, fragment.compositionCount)

        activity.supportFragmentManager.beginTransaction()
            .detach(fragment)
            .commitNow()
        assertEquals(1, fragment.disposalCount)
        activity.supportFragmentManager.beginTransaction()
            .attach(fragment)
            .commitNow()

        val secondOwner = fragment.viewLifecycleOwner
        assertNotSame(firstOwner, secondOwner)
        assertSame(secondOwner, fragment.capturedOwner)
        assertSame(fragment, fragment.capturedSavedStateRegistryOwner)
        assertSame(fragment, fragment.capturedViewModelStoreOwner)
        assertSame(fragment.firstSaveableStateRegistry, fragment.capturedSaveableStateRegistry)
        assertEquals(2, fragment.compositionCount)
    }
}

class LifecycleCapturingFragment : Fragment() {
    var capturedOwner: LifecycleOwner? = null
    var capturedSavedStateRegistryOwner: Any? = null
    var capturedViewModelStoreOwner: Any? = null
    var capturedSaveableStateRegistry: SaveableStateRegistry? = null
    var firstSaveableStateRegistry: SaveableStateRegistry? = null
    var compositionCount: Int = 0
    var disposalCount: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return setUiContent {
            capturedOwner = LocalLifecycleOwner.current
            capturedSavedStateRegistryOwner = LocalSavedStateRegistryOwner.current
            capturedViewModelStoreOwner = LocalViewModelStoreOwner.current
            capturedSaveableStateRegistry = LocalSaveableStateRegistry.current
            if (firstSaveableStateRegistry == null) {
                firstSaveableStateRegistry = capturedSaveableStateRegistry
            }
            compositionCount += 1
            DisposableEffect(Unit) {
                onDispose { disposalCount += 1 }
            }
            Text("Fragment generation $compositionCount")
        }
    }
}
