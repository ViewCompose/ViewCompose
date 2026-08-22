package com.viewcompose.animation.core

/*
 * 测试职责：覆盖 animation core 中的 Transition Core 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Transition Core behavior in animation core and guards the contract against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionCoreTest {
    @Test
    fun `updateTarget starts running segment and tracks states`() {
        val core = TransitionCore(initialState = false)

        core.updateTarget(true)

        assertTrue(core.isRunning)
        assertEquals(false, core.currentState)
        assertEquals(false, core.segmentInitialState)
        assertEquals(true, core.segmentTargetState)
        assertEquals(1L, core.segmentVersion)
        assertEquals(0L, core.playTimeNanos)
    }

    @Test
    fun `registerDuration keeps max duration for active segment`() {
        val core = TransitionCore(initialState = false)
        core.updateTarget(true)

        core.registerDuration(16L)
        core.registerDuration(64L)
        core.registerDuration(32L)

        assertEquals(64L, core.segmentDurationNanos)
    }

    @Test
    fun `play time reaching duration completes segment`() {
        val core = TransitionCore(initialState = false)
        core.updateTarget(true)
        core.registerDuration(48L)

        core.updatePlayTime(48L)

        assertFalse(core.isRunning)
        assertEquals(true, core.currentState)
        assertEquals(true, core.targetState)
        assertEquals(48L, core.playTimeNanos)
    }

    @Test
    fun `retarget while running uses previous target as new initial segment state`() {
        val core = TransitionCore(initialState = false)
        core.updateTarget(true)
        core.registerDuration(100L)
        core.updatePlayTime(40L)

        core.updateTarget(false)

        assertTrue(core.isRunning)
        assertEquals(true, core.segmentInitialState)
        assertEquals(false, core.segmentTargetState)
        assertEquals(2L, core.segmentVersion)
        assertEquals(0L, core.playTimeNanos)
    }

    @Test
    fun `replaceDuration can shrink a dynamic channel set and finish immediately`() {
        val core = TransitionCore(initialState = false)
        core.updateTarget(true)
        core.registerDuration(100L)
        core.updatePlayTime(60L)

        core.replaceDuration(40L)

        assertFalse(core.isRunning)
        assertEquals(true, core.currentState)
        assertEquals(40L, core.playTimeNanos)
    }

    @Test
    fun `seek can move backward from the terminal endpoint on the same segment`() {
        val core = TransitionCore(initialState = false)
        core.updateTarget(true)
        core.replaceDuration(100L)

        core.seekToPlayTime(100L)
        assertFalse(core.isRunning)
        assertEquals(true, core.currentState)

        core.seekToPlayTime(25L)
        assertTrue(core.isRunning)
        assertEquals(false, core.currentState)
        assertEquals(true, core.targetState)
        assertEquals(25L, core.playTimeNanos)
    }

    @Test
    fun `restart creates a new version only for an unfinished segment`() {
        val core = TransitionCore(initialState = false)
        core.updateTarget(true)
        core.replaceDuration(100L)
        core.seekToPlayTime(40L)

        core.restartRunningSegment()

        assertEquals(2L, core.segmentVersion)
        assertEquals(0L, core.playTimeNanos)
        assertEquals(1L, core.segmentDurationNanos)
        assertTrue(core.isRunning)

        core.replaceDuration(50L)
        core.updatePlayTime(50L)
        core.restartRunningSegment()
        assertEquals(2L, core.segmentVersion)
    }

    @Test
    fun `snap collapses running segment into one idle endpoint`() {
        val core = TransitionCore(initialState = false)
        core.updateTarget(true)
        core.replaceDuration(300L)
        core.seekToPlayTime(120L)
        val runningVersion = core.segmentVersion

        core.snapTo(false)

        assertEquals(false, core.currentState)
        assertEquals(false, core.targetState)
        assertEquals(false, core.segmentInitialState)
        assertEquals(false, core.segmentTargetState)
        assertEquals(0L, core.playTimeNanos)
        assertEquals(1L, core.segmentDurationNanos)
        assertEquals(runningVersion + 1L, core.segmentVersion)
        assertFalse(core.isRunning)

        core.snapTo(false)
        assertEquals(runningVersion + 1L, core.segmentVersion)
    }
}
