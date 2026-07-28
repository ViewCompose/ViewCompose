package com.viewcompose.host.android

/*
 * 测试职责：覆盖 Android host 中的 Android Saveable State Registry 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Android Saveable State Registry behavior in Android host and guards the contract against regressions.
 */

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidSaveableStateRegistryTest {
    @Test
    fun `registry bundle round trip preserves nested nullable values`() {
        val original = linkedMapOf(
            "nullable" to null,
            "nested" to listOf(
                "value",
                mapOf(
                    "count" to 7,
                    "enabled" to true,
                ),
            ),
        )

        val restored = decodeRegistryState(
            bundle = encodeRegistryState(original),
            classLoader = javaClass.classLoader,
        )

        assertEquals(original, restored)
    }

    @Test
    fun `malformed entry is isolated without discarding valid restored state`() {
        val encoded = encodeRegistryState(
            mapOf(
                "valid" to "kept",
                "broken" to "replace",
            ),
        )
        encoded.getBundle("entries")?.putBundle(
            "broken",
            Bundle().apply {
                putInt("valueType", Int.MAX_VALUE)
            },
        )

        val restored = decodeRegistryState(
            bundle = encoded,
            classLoader = javaClass.classLoader,
        )

        assertEquals(mapOf("valid" to "kept"), restored)
    }

    @Test
    fun `unknown registry format is ignored`() {
        val encoded = encodeRegistryState(mapOf("value" to 1)).apply {
            putInt("formatVersion", Int.MAX_VALUE)
        }

        assertEquals(
            emptyMap<String, Any?>(),
            decodeRegistryState(
                bundle = encoded,
                classLoader = javaClass.classLoader,
            ),
        )
    }
}
