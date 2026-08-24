package com.viewcompose.paging

/**
 * Selects the lifetime of AndroidX Paging collection owned by a ViewCompose composition.
 *
 * Lifecycle-gated policies retain the last coherent presentation while inactive. Restarting
 * collection follows the upstream [kotlinx.coroutines.flow.Flow] contract; applications that need
 * replay across restarts continue to own `cachedIn` and its scope.
 */
enum class PagingLifecyclePolicy {
    /** Collects while the nearest AndroidX lifecycle is at least `STARTED`. */
    Visible,

    /** Collects while the nearest AndroidX lifecycle is at least `CREATED`. */
    Retained,

    /** Collects from composition commit until the collecting call leaves composition. */
    Composition,
}
