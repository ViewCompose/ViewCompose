package com.viewcompose.runtime.composition

/** Identifies why a composition scope was considered during one composition attempt. */
enum class RecompositionReason {
    /** The scope had no previously committed composition. */
    InitialComposition,

    /** State read by the scope changed after its previous composition. */
    StateInvalidation,

    /** A descendant invalidation required this ancestor to participate in recomposition. */
    AncestorInvalidation,

    /** The explicit [ComposerLite.runGroup] input value changed. */
    InputsChanged,

    /** The host requested root recomposition through [ComposerLite.requestRootRecompose]. */
    ExplicitRequest,

    /** The group sequence or signature no longer matched the previously committed structure. */
    StructureChanged,
}

/**
 * Describes one composition-local value without retaining the original value object.
 *
 * @property name stable diagnostic name assigned by the local provider
 * @property value formatted, potentially redacted or truncated representation captured by the host
 */
data class CompositionLocalDiagnostic(
    val name: String,
    val value: String,
)

/**
 * Identifies one JVM line-table call site captured when a composition scope was created.
 *
 * Source locations are diagnostic hints rather than stable API identifiers. Refactoring, inlining,
 * obfuscation, or missing debug information may change or remove them.
 *
 * @property className binary or qualified JVM class name containing the call
 * @property methodName JVM method name containing the call
 * @property fileName source file reported by the line table
 * @property lineNumber one-based source line, or the line-table sentinel supplied by the collector
 */
data class CompositionSourceCallSite(
    val className: String,
    val methodName: String,
    val fileName: String,
    val lineNumber: Int,
)

/**
 * Describes how one composition scope participated in a composition attempt.
 *
 * @property path deterministic structural/saveable path assigned by the composer
 * @property signature human-readable group signature, truncated when it exceeds the diagnostic limit
 * @property depth number of ancestors between this scope and the root; the root has depth `0`
 * @property reasons reasons recorded for entering or invalidating this scope
 * @property recomposed whether the scope body ran during the attempt
 * @property skipped whether a previously committed result was reused without running the body
 * @property locals formatted composition-local values captured for this scope
 * @property sourceCallSites source locations captured when the scope was first created
 */
data class RecomposeScopeDiagnostic(
    val path: String,
    val signature: String,
    val depth: Int,
    val reasons: Set<RecompositionReason> = emptySet(),
    val recomposed: Boolean,
    val skipped: Boolean,
    val locals: List<CompositionLocalDiagnostic> = emptyList(),
    val sourceCallSites: List<CompositionSourceCallSite> = emptyList(),
)

/**
 * Summarizes invalidation and reuse decisions from one prepared composition.
 *
 * Counts cover the complete attempt, while [scopes] is capped by the composer's diagnostic limit
 * and may therefore contain fewer entries than the count totals. An instance with zero counts and
 * an empty list is returned when diagnostic collection is disabled.
 *
 * @property invalidatedScopeCount number of queued scopes drained at the start of the attempt
 * @property recomposedScopeCount number of scope bodies executed during the attempt
 * @property skippedScopeCount number of scopes whose committed results were reused
 * @property scopes bounded per-scope diagnostic details in traversal/recording order
 */
data class CompositionDiagnostics(
    val invalidatedScopeCount: Int = 0,
    val recomposedScopeCount: Int = 0,
    val skippedScopeCount: Int = 0,
    val scopes: List<RecomposeScopeDiagnostic> = emptyList(),
)
