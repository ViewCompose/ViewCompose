package com.viewcompose.runtime.composition

enum class RecompositionReason {
    InitialComposition,
    StateInvalidation,
    AncestorInvalidation,
    InputsChanged,
    ExplicitRequest,
    StructureChanged,
}

data class CompositionLocalDiagnostic(
    val name: String,
    val value: String,
)

data class RecomposeScopeDiagnostic(
    val path: String,
    val signature: String,
    val depth: Int,
    val reasons: Set<RecompositionReason> = emptySet(),
    val recomposed: Boolean,
    val skipped: Boolean,
    val locals: List<CompositionLocalDiagnostic> = emptyList(),
)

data class CompositionDiagnostics(
    val invalidatedScopeCount: Int = 0,
    val recomposedScopeCount: Int = 0,
    val skippedScopeCount: Int = 0,
    val scopes: List<RecomposeScopeDiagnostic> = emptyList(),
)
