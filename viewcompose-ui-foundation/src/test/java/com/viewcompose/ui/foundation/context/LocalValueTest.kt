package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core context 中的 Local Value 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Local Value behavior in widget-core context and guards DSL, state, or theme contracts against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalValueTest {
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
