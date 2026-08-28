package com.viewcompose.runtime.samples

import com.viewcompose.runtime.Snapshot
import com.viewcompose.runtime.SnapshotApplyResult
import com.viewcompose.runtime.SnapshotMutationPolicy
import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.runtime.composition.CompositionTimingCollector
import com.viewcompose.runtime.composition.CompositionTimingSpan
import com.viewcompose.runtime.composition.RememberObserver
import com.viewcompose.runtime.derivedStateOf
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.runtime.observation.RuntimeObservation
import com.viewcompose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow

fun mutableStateSample() {
    val count = mutableStateOf(0)

    count.value += 1

    check(count.value == 1)
}

fun derivedStateSample() {
    val firstName = mutableStateOf("Ada")
    val lastName = mutableStateOf("Lovelace")
    val displayName = derivedStateOf { "${firstName.value} ${lastName.value}" }

    check(displayName.value == "Ada Lovelace")
    firstName.value = "Augusta"
    check(displayName.value == "Augusta Lovelace")
}

fun mutableSnapshotSample() {
    val count = mutableStateOf(0)
    val enabled = mutableStateOf(false)

    Snapshot.withMutableSnapshot {
        count.value = 1
        enabled.value = true
    }

    check(count.value == 1 && enabled.value)
}

fun snapshotMutationPolicySample() {
    val additivePolicy = object : SnapshotMutationPolicy<Int> {
        override fun equivalent(a: Int, b: Int): Boolean = a == b

        override fun merge(previous: Int, current: Int, applied: Int): Int {
            val localDelta = current - previous
            return applied + localDelta
        }
    }
    val count = mutableStateOf(0, additivePolicy)
    val first = Snapshot.takeMutableSnapshot()
    val second = Snapshot.takeMutableSnapshot()
    try {
        first.enter { count.value = 1 }
        second.enter { count.value = 2 }

        check(first.apply() == SnapshotApplyResult.Success)
        check(second.apply() == SnapshotApplyResult.Success)
        check(count.value == 3)
    } finally {
        first.dispose()
        second.dispose()
    }
}

fun runtimeObservationSample() {
    val count = mutableStateOf(0)
    val enabled = mutableStateOf(false)
    var invalidations = 0
    val (_, observation) = RuntimeObservation.observeReads(
        onInvalidated = { invalidations += 1 },
    ) {
        count.value to enabled.value
    }

    Snapshot.withMutableSnapshot {
        count.value = 1
        enabled.value = true
    }
    check(invalidations == 1)
    observation.dispose()
}

fun observationReplacementSample() {
    val selected = mutableStateOf(true)
    val first = mutableStateOf("first")
    val second = mutableStateOf("second")
    val (_, observation) = RuntimeObservation.observeReads(onInvalidated = {}) {
        if (selected.value) first.value else second.value
    }

    selected.value = false
    val (candidate, replacement) = RuntimeObservation.prepareReplacement(observation) {
        if (selected.value) first.value else second.value
    }
    check(candidate == "second")
    replacement.commit()
    observation.dispose()
}

/** Observes a derived snapshot query without exposing its mutable inputs. */
fun snapshotFlowSample(): Flow<String> {
    val firstName = mutableStateOf("Ada")
    val lastName = mutableStateOf("Lovelace")
    return snapshotFlow {
        "${firstName.value} ${lastName.value}"
    }
}

fun composerLiteSample() {
    val count = mutableStateOf(0)
    val committedValues = mutableListOf<String>()
    val composer = ComposerLite()

    fun compose(): String = composer.composeRoot {
        composer.runGroup(signature = "counter") { _ ->
            val prefix = composer.remember(keys = emptyList()) { "Count" }
            "$prefix: ${count.value}".also { value ->
                composer.sideEffect { committedValues += value }
            }
        }
    }

    check(compose() == "Count: 0")
    composer.commitSideEffects()
    count.value = 1
    check(composer.hasPendingInvalidations())
    check(compose() == "Count: 1")
    composer.commitSideEffects()
    check(committedValues == listOf("Count: 0", "Count: 1"))

    composer.dispose()
}

fun compositionTimingCollectorSample() {
    val composer = ComposerLite()
    val visitedPaths = mutableListOf<String>()
    val prepared = composer.prepareRootWithTiming(
        collector = CompositionTimingCollector { scope ->
            visitedPaths += scope.path
            CompositionTimingSpan { }
        },
    ) {
        composer.runGroup(signature = "content") { "Hello" }
    }

    check(prepared.value == "Hello")
    prepared.commit()
    check(visitedPaths.isNotEmpty())
}

fun rememberObserverRetrySample() {
    val composer = ComposerLite()
    var attempts = 0
    val observer = object : RememberObserver {
        override fun onRemembered() {
            attempts += 1
            if (attempts == 1) error("temporary activation failure")
        }

        override fun onForgotten() = Unit

        override fun onAbandoned() = Unit
    }

    val firstFailure = runCatching {
        composer.composeRoot {
            composer.remember<RememberObserver>(keys = listOf("resource")) { observer }
        }
    }.exceptionOrNull()
    check(firstFailure != null)

    composer.requestRootRecompose()
    composer.composeRoot {
        composer.remember<RememberObserver>(keys = listOf("resource")) { observer }
    }
    check(attempts == 2)
    composer.dispose()
}

fun reusableContentOwnerSample() {
    val composer = ComposerLite()
    var owner = "account-A"
    var revision = 0L

    fun compose(replaceOwner: Boolean): Any {
        composer.requestRootRecompose()
        return composer.composeRoot {
            composer.runGroup(
                signature = "reusable-host",
                inputs = revision,
            ) {
                composer.withReusableContent(owner, replaceOwner) {
                    composer.runGroup(signature = "content") {
                        composer.remember(emptyList()) { Any() }
                    }
                }
            }
        }
    }

    val firstOwnerState = compose(replaceOwner = false)
    owner = "account-B"
    revision += 1L
    val secondOwnerState = compose(replaceOwner = true)

    check(firstOwnerState !== secondOwnerState)
    composer.dispose()
}

fun keyedGroupMovementSample() {
    val composer = ComposerLite()

    fun compose(order: List<String>): Map<String, Any> {
        composer.requestRootRecompose()
        return composer.composeRoot {
            order.associateWith { itemId ->
                composer.withKeys(listOf(itemId)) {
                    composer.runGroup(signature = "item") {
                        composer.remember(emptyList()) { Any() }
                    }
                }
            }
        }
    }

    val initial = compose(listOf("A", "B", "C"))
    val reordered = compose(listOf("C", "A", "B"))
    check(initial.all { (key, value) -> reordered.getValue(key) === value })
    composer.dispose()
}

fun scopedExplicitSaveableKeySample() {
    val composer = ComposerLite()

    val registryKey = composer.composeRoot {
        composer.withKeys(listOf("account-42")) {
            composer.runGroup(signature = "profile") {
                composer.scopedExplicitSaveableKey("display-name")
            }
        }
    }

    check(registryKey.endsWith(":display-name"))
    composer.dispose()
}
