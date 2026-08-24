package com.viewcompose.maps.google

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.viewcompose.host.android.renderInto
import com.viewcompose.lifecycle.ProvideLifecycleOwner
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.google.android.gms.maps.MapView

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class GoogleMapViewMountTest {
    @Test
    fun `credential-free mount retains same map and options replace construction identity`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val owner = TestMapLifecycleOwner().apply { moveTo(Lifecycle.State.CREATED) }
        var options = GoogleMapViewOptions.Default
        var properties = GoogleMapProperties.Default
        val session = renderInto(root) {
            ProvideLifecycleOwner(owner) {
                GoogleMapView(options = options, properties = properties, key = "map")
            }
        }
        val initial = root.requireMapDescendant()

        properties = GoogleMapProperties(mapType = GoogleMapType.Satellite)
        session.render()
        assertSame(initial, root.requireMapDescendant())

        options = GoogleMapViewOptions(liteMode = true)
        session.render()
        assertTrue(initial !== root.requireMapDescendant())

        session.dispose()
    }
}

private class TestMapLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = registry

    fun moveTo(state: Lifecycle.State) {
        registry.currentState = state
    }
}

private fun View.requireMapDescendant(): MapView {
    if (this is MapView) return this
    if (this is ViewGroup) {
        repeat(childCount) { index ->
            runCatching { getChildAt(index).requireMapDescendant() }.getOrNull()?.let { return it }
        }
    }
    error("Missing descendant ${MapView::class.java.name}")
}
