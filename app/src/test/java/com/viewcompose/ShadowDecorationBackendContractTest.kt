package com.viewcompose

import com.viewcompose.renderer.decoration.AndroidViewDecorationBackend
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ServiceLoader

class ShadowDecorationBackendContractTest {
    @Test
    fun shadowBackend_isDiscoverableViaServiceLoader() {
        val providers = ServiceLoader.load(
            AndroidViewDecorationBackend::class.java,
            AndroidViewDecorationBackend::class.java.classLoader,
        ).toList()

        assertTrue(
            "Missing optional Android shadow decoration backend service provider.",
            providers.any { provider ->
                provider::class.java.name ==
                    "com.viewcompose.shadow.android.ShadowViewDecorationBackend"
            },
        )
    }
}
