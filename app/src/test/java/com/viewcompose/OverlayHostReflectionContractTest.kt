package com.viewcompose

/*
 * 测试职责：覆盖 app demo 中的 Overlay Host Reflection Contract 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Overlay Host Reflection Contract behavior in app demo and guards the contract against regressions.
 */

import com.viewcompose.host.android.overlay.AndroidOverlayHostFactoryProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ServiceLoader

class OverlayHostReflectionContractTest {
    @Test
    fun androidOverlayHostProvider_isDiscoverableViaServiceLoader() {
        val providers = ServiceLoader.load(
            AndroidOverlayHostFactoryProvider::class.java,
            AndroidOverlayHostFactoryProvider::class.java.classLoader,
        ).toList()

        assertTrue(
            "Missing Android overlay host service provider.",
            providers.any { provider ->
                provider::class.java.name == "com.viewcompose.overlay.material3.android.host.AndroidOverlayHostFactoryProvider"
            },
        )
    }
}
