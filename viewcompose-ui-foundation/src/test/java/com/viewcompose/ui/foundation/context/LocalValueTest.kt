package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core context 中的 Local Value 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Local Value behavior in widget-core context and guards DSL, state, or theme contracts against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalValueTest {
    @Test
    fun `snapshot identity stays stable until a provider boundary`() {
        val local = LocalValue { "default" }
        val outside = LocalContext.snapshot()

        assertSame(outside, LocalContext.snapshot())
        LocalContext.provide(local, "provided") {
            val provided = LocalContext.snapshot()

            assertNotSame(outside, provided)
            assertSame(provided, LocalContext.snapshot())
            assertEquals("provided", LocalContext.current(local))
        }
        assertSame(outside, LocalContext.snapshot())
    }

    @Test
    fun `nested providers restore exact previous snapshot identities`() {
        val first = LocalValue { "first-default" }
        val second = LocalValue { "second-default" }
        val outside = LocalContext.snapshot()

        LocalContext.provide(first, "outer") {
            val outer = LocalContext.snapshot()
            LocalContext.provide(second, "inner") {
                val inner = LocalContext.snapshot()

                assertNotSame(outer, inner)
                assertSame(inner, LocalContext.snapshot())
            }
            assertSame(outer, LocalContext.snapshot())
        }

        assertSame(outside, LocalContext.snapshot())
    }

    @Test
    fun `with snapshot installs supplied identity and restores caller after failure`() {
        val local = LocalValue { "default" }
        lateinit var captured: LocalSnapshot
        LocalContext.provide(local, "captured") {
            captured = LocalContext.snapshot()
        }
        val caller = LocalContext.snapshot()

        val failure = runCatching {
            LocalContext.withSnapshot(captured) {
                assertSame(captured, LocalContext.snapshot())
                assertEquals("captured", LocalContext.current(local))
                error("expected failure")
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertSame(caller, LocalContext.snapshot())
    }

    @Test
    fun `public captures share installed delegate without sharing wrappers`() {
        val first = captureUiLocalSnapshot()
        val second = captureUiLocalSnapshot()

        assertNotSame(first, second)
        assertSame(first.delegate, second.delegate)
    }

    @Test
    fun `local uses default outside provider`() {
        val local = LocalValue { 7 }

        assertEquals(7, LocalContext.current(local))
    }

    @Test
    fun `nested providers restore previous values`() {
        val local = LocalValue { "default" }
        var outer = ""
        var inner = ""
        var restored = ""

        LocalContext.provide(local, "outer") {
            outer = LocalContext.current(local)
            LocalContext.provide(local, "inner") {
                inner = LocalContext.current(local)
            }
            restored = LocalContext.current(local)
        }

        assertEquals("outer", outer)
        assertEquals("inner", inner)
        assertEquals("outer", restored)
    }

    @Test
    fun `captured snapshot can restore deferred locals`() {
        val local = LocalValue { "default" }
        var captured: LocalSnapshot? = null
        var restored = ""

        LocalContext.provide(local, "deferred") {
            captured = LocalContext.snapshot()
        }
        LocalContext.withSnapshot(captured!!) {
            restored = LocalContext.current(local)
        }

        assertEquals("deferred", restored)
        assertEquals("default", LocalContext.current(local))
    }

    @Test
    fun `opaque public snapshot restores locals and returns block result`() {
        val local = LocalValue { "default" }
        var snapshot: UiLocalSnapshot? = null

        LocalContext.provide(local, "page") {
            snapshot = captureUiLocalSnapshot()
        }
        val result = withUiLocalSnapshot(checkNotNull(snapshot)) {
            assertEquals("page", LocalContext.current(local))
            42
        }

        assertEquals(42, result)
        assertEquals("default", LocalContext.current(local))
    }

    @Test
    fun `provided null overrides a non-null default across nested and batch providers`() {
        val nullable = uiLocalOf<String?>(debugName = "NullableLocal") { "default" }
        val marker = uiLocalOf(debugName = "MarkerLocal") { "outside" }
        val values = mutableListOf<String?>()

        buildVNodeTree {
            values += UiLocals.current(nullable)
            ProvideLocal(nullable, null) {
                values += UiLocals.current(nullable)
                ProvideLocal(nullable, "inner") {
                    values += UiLocals.current(nullable)
                }
                values += UiLocals.current(nullable)
            }
            ProvideLocals(
                nullable provides null,
                marker provides "inside",
            ) {
                values += UiLocals.current(nullable)
                values += UiLocals.current(marker)
            }
            values += UiLocals.current(nullable)
        }

        assertEquals(
            listOf("default", null, "inner", null, null, "inside", "default"),
            values,
        )
    }

    @Test
    fun `batch provider exposes one stable snapshot and restores its caller`() {
        val first = uiLocalOf(debugName = "BatchFirst") { "first-default" }
        val second = uiLocalOf(debugName = "BatchSecond") { "second-default" }
        lateinit var caller: LocalSnapshot

        buildVNodeTree {
            caller = LocalContext.snapshot()
            ProvideLocals(
                first provides "first-provided",
                second provides "second-provided",
            ) {
                val batch = LocalContext.snapshot()

                assertSame(batch, LocalContext.snapshot())
                assertEquals("first-provided", UiLocals.current(first))
                assertEquals("second-provided", UiLocals.current(second))
            }
            assertSame(caller, LocalContext.snapshot())
        }
    }

    @Test
    fun `nullable public snapshot restores null and restores caller after failure`() {
        val nullable = uiLocalOf<String?>(debugName = "NullableSnapshotLocal") { "default" }
        lateinit var snapshot: UiLocalSnapshot

        buildVNodeTree {
            ProvideLocal(nullable, null) {
                snapshot = captureUiLocalSnapshot()
            }
        }

        assertNull(withUiLocalSnapshot(snapshot) { UiLocals.current(nullable) })
        val failure = runCatching {
            withUiLocalSnapshot(snapshot) {
                assertNull(UiLocals.current(nullable))
                error("expected failure")
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("default", UiLocals.current(nullable))
    }
}
