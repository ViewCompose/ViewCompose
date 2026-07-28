package com.viewcompose.runtime.composition

/**
 * scope 本次进入重组流程的原因。
 * Reason why a scope entered the recomposition flow in this pass.
 */
enum class RecompositionReason {
    InitialComposition,
    StateInvalidation,
    AncestorInvalidation,
    InputsChanged,
    ExplicitRequest,
    StructureChanged,
}

/**
 * composition local 的诊断快照，用字符串保存以避免泄漏平台对象。
 * Diagnostic snapshot of a composition local, stringified to avoid leaking platform objects.
 */
data class CompositionLocalDiagnostic(
    val name: String,
    val value: String,
)

/**
 * 单个重组 scope 的诊断信息。
 * Diagnostic information for one recomposition scope.
 */
data class RecomposeScopeDiagnostic(
    val path: String,
    val signature: String,
    val depth: Int,
    val reasons: Set<RecompositionReason> = emptySet(),
    val recomposed: Boolean,
    val skipped: Boolean,
    val locals: List<CompositionLocalDiagnostic> = emptyList(),
)

/**
 * 一次 composition 的汇总诊断结果。
 * Summary diagnostics for one composition pass.
 */
data class CompositionDiagnostics(
    val invalidatedScopeCount: Int = 0,
    val recomposedScopeCount: Int = 0,
    val skippedScopeCount: Int = 0,
    val scopes: List<RecomposeScopeDiagnostic> = emptyList(),
)
