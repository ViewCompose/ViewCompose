package com.viewcompose.runtime.samples

import org.junit.Test

class RuntimeSamplesTest {
    @Test
    fun documentedSamplesExecuteSuccessfully() {
        mutableStateSample()
        derivedStateSample()
        mutableSnapshotSample()
        snapshotMutationPolicySample()
        runtimeObservationSample()
        composerLiteSample()
    }
}
