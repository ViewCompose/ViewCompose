package com.viewcompose.runtime.samples

import com.viewcompose.runtime.Snapshot
import com.viewcompose.runtime.SnapshotApplyResult
import com.viewcompose.runtime.SnapshotMutationPolicy
import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.runtime.derivedStateOf
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.runtime.observation.RuntimeObservation

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
    var invalidated = false
    val (_, observation) = RuntimeObservation.observeReads(
        onInvalidated = { invalidated = true },
    ) {
        count.value
    }

    count.value = 1
    check(invalidated)
    observation.dispose()
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
