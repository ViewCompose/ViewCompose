package com.viewcompose.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceBenchmarkQualityParityTest {
    @Test
    fun `multiple online devices preserve explicit selection failure`() {
        val result = AndroidDevicePreflight.selectDevice(
            devicesOutput =
                "List of devices attached\n" +
                    "pixel\tdevice product:pixel\n" +
                    "xiaomi\tdevice product:xiaomi\n",
            requestedSerial = null,
        )

        assertNull(result.serial)
        assertEquals(
            "Android connected-test preflight failed: multiple online devices are attached " +
                "(pixel, xiaomi). Set ANDROID_SERIAL explicitly.",
            result.failure,
        )
    }

    @Test
    fun `requested online device wins when multiple devices are attached`() {
        val result = AndroidDevicePreflight.selectDevice(
            devicesOutput =
                "List of devices attached\n" +
                    "pixel\tdevice product:pixel\n" +
                    "xiaomi\tdevice product:xiaomi\n",
            requestedSerial = " pixel ",
        )

        assertEquals("pixel", result.serial)
        assertNull(result.failure)
    }

    @Test
    fun `requested offline device preserves selected-state diagnostic`() {
        val result = AndroidDevicePreflight.selectDevice(
            devicesOutput = "List of devices attached\npixel\toffline\n",
            requestedSerial = "pixel",
        )

        assertNull(result.serial)
        assertEquals(
            "Android connected-test preflight failed: ANDROID_SERIAL 'pixel' is offline. " +
                "Run `adb devices` and select an online device.",
            result.failure,
        )
    }

    @Test
    fun `authoritative unlocked field overrides stale Android 7 fallback`() {
        val failure = AndroidDevicePreflight.validateSelectedDevice(
            serial = "legacy",
            bootCompleted = "1",
            powerState = "mWakefulness=Awake\n",
            windowPolicy = "mIsShowing=false\nshowingAndNotOccluded=true\n",
        )

        assertNull(failure)
    }

    @Test
    fun `boot display and keyguard failures preserve ordering and guidance`() {
        val failure = AndroidDevicePreflight.validateSelectedDevice(
            serial = "pixel",
            bootCompleted = "0",
            powerState = "mWakefulness=Asleep\n",
            windowPolicy = "mIsShowing=true\n",
        )

        assertEquals(
            "Android connected-test preflight failed for 'pixel': Android has not completed " +
                "booting, the display is not awake, the keyguard is showing. Wake and unlock the " +
                "selected device, keep its screen on, then rerun the task. The gate deliberately " +
                "does not bypass a secure keyguard.",
            failure,
        )
    }
}
