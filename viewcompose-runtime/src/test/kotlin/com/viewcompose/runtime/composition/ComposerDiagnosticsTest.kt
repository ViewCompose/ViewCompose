package com.viewcompose.runtime.composition

import com.viewcompose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerDiagnosticsTest {
    @Test
    fun `diagnostics explain state invalidation skips and captured locals`() {
        val state = mutableStateOf(0)
        val composer = ComposerLite(
            localSnapshotInspector = { snapshot ->
                listOf(
                    CompositionLocalDiagnostic(
                        name = "TestLocal",
                        value = snapshot.toString(),
                    ),
                )
            },
        )

        val initial = composer.prepareRoot(collectDiagnostics = true) {
            composer.runGroup(signature = "watched") { scope ->
                scope.updateLocalSnapshot("local-value")
                state.value
            }
            composer.runGroup(signature = "stable") { 7 }
        }
        initial.commit()

        assertTrue(
            initial.diagnostics.scopes.any { scope ->
                scope.reasons.contains(RecompositionReason.InitialComposition)
            },
        )

        state.value = 1
        val update = composer.prepareRoot(collectDiagnostics = true) {
            composer.runGroup(signature = "watched") { scope ->
                scope.updateLocalSnapshot("local-value")
                state.value
            }
            composer.runGroup(signature = "stable") { 7 }
        }
        update.commit()

        val stateScope = update.diagnostics.scopes.first { scope ->
            scope.signature.contains("watched")
        }
        val stableScope = update.diagnostics.scopes.first { scope ->
            scope.signature.contains("stable")
        }
        assertTrue(stateScope.recomposed)
        assertTrue(stateScope.reasons.contains(RecompositionReason.StateInvalidation))
        assertEquals(
            CompositionLocalDiagnostic("TestLocal", "local-value"),
            stateScope.locals.single(),
        )
        assertTrue(stableScope.skipped)
        assertEquals(1, update.diagnostics.invalidatedScopeCount)
    }

    @Test
    fun `diagnostics distinguish explicit root requests from changed group inputs`() {
        val composer = ComposerLite()
        var input = 1

        composer.prepareRoot(collectDiagnostics = true) {
            composer.runGroup(signature = "input", inputs = input) { input }
        }.commit()

        input = 2
        composer.requestRootRecompose()
        val update = composer.prepareRoot(collectDiagnostics = true) {
            composer.runGroup(signature = "input", inputs = input) { input }
        }
        update.commit()

        val root = update.diagnostics.scopes.first { scope -> scope.depth == 0 }
        val inputScope = update.diagnostics.scopes.first { scope ->
            scope.signature.contains("input")
        }
        assertTrue(root.reasons.contains(RecompositionReason.ExplicitRequest))
        assertTrue(inputScope.reasons.contains(RecompositionReason.InputsChanged))
    }
}
