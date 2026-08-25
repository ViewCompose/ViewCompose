package com.viewcompose.quality

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/** Fails before connected tests unless the selected Android device is online and interactive. */
abstract class VerifyConnectedAndroidDeviceReadyTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val requestedSerial: Property<String>

    @TaskAction
    fun verifyDevice() {
        val adb = resolveAdbExecutable()
        val selection = AndroidDevicePreflight.selectDevice(
            devicesOutput = runAdb(adb, "devices"),
            requestedSerial = requestedSerial.orNull,
        )
        selection.failure?.let { failure -> throw GradleException(failure) }
        val serial = checkNotNull(selection.serial)
        val stateFailure = AndroidDevicePreflight.validateSelectedDevice(
            serial = serial,
            bootCompleted = runAdb(
                adb,
                "-s",
                serial,
                "shell",
                "getprop",
                "sys.boot_completed",
            ),
            powerState = runAdb(adb, "-s", serial, "shell", "dumpsys", "power"),
            windowPolicy = runAdb(
                adb,
                "-s",
                serial,
                "shell",
                "dumpsys",
                "window",
                "policy",
            ),
        )
        stateFailure?.let { failure -> throw GradleException(failure) }
        logger.lifecycle(
            "Android connected-test device '$serial' is online, booted, awake, and unlocked.",
        )
    }

    private fun resolveAdbExecutable(): String {
        val executable = if (System.getProperty("os.name").startsWith("Windows")) {
            "adb.exe"
        } else {
            "adb"
        }
        val sdkRoot = System.getenv("ANDROID_SDK_ROOT")
            ?.takeIf(String::isNotBlank)
            ?: System.getenv("ANDROID_HOME")?.takeIf(String::isNotBlank)
        return sdkRoot
            ?.let { root -> File(root).resolve("platform-tools/$executable") }
            ?.takeIf(File::isFile)
            ?.absolutePath
            ?: executable
    }

    private fun runAdb(adb: String, vararg arguments: String): String {
        val command = listOf(adb) + arguments
        val process = try {
            ProcessBuilder(command)
                .directory(repositoryDirectory.get().asFile)
                .redirectErrorStream(true)
                .start()
        } catch (error: Exception) {
            throw GradleException(
                "Android connected-test preflight could not start adb. Install Android SDK " +
                    "platform-tools or set ANDROID_SDK_ROOT.",
                error,
            )
        }
        val output = process.inputStream.bufferedReader().use { reader -> reader.readText() }.trim()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException(
                "Android connected-test preflight failed: ${command.joinToString(" ")} " +
                    "exited with $exitCode.\n$output",
            )
        }
        return output
    }
}

internal data class AndroidDeviceSelection(
    val serial: String? = null,
    val failure: String? = null,
)

internal object AndroidDevicePreflight {
    fun selectDevice(
        devicesOutput: String,
        requestedSerial: String?,
    ): AndroidDeviceSelection {
        val deviceRows = devicesOutput
            .lineSequence()
            .drop(1)
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { row -> row.split(Regex("\\s+"), limit = 3) }
            .filter { fields -> fields.size >= 2 }
            .associate { fields -> fields[0] to fields[1] }
        val normalizedRequest = requestedSerial?.trim()?.takeIf(String::isNotEmpty)
        val onlineSerials = deviceRows.filterValues { state -> state == "device" }.keys.sorted()
        return when {
            normalizedRequest != null && deviceRows[normalizedRequest] == "device" ->
                AndroidDeviceSelection(serial = normalizedRequest)
            normalizedRequest != null -> AndroidDeviceSelection(
                failure =
                    "Android connected-test preflight failed: ANDROID_SERIAL " +
                        "'$normalizedRequest' is ${deviceRows[normalizedRequest] ?: "not attached"}. " +
                        "Run `adb devices` and select an online device.",
            )
            onlineSerials.size == 1 -> AndroidDeviceSelection(serial = onlineSerials.single())
            onlineSerials.isEmpty() -> AndroidDeviceSelection(
                failure =
                    "Android connected-test preflight failed: no online device is available. " +
                        "Run `adb devices`, authorize the device, and retry.",
            )
            else -> AndroidDeviceSelection(
                failure =
                    "Android connected-test preflight failed: multiple online devices are " +
                        "attached (${onlineSerials.joinToString()}). Set ANDROID_SERIAL explicitly.",
            )
        }
    }

    fun validateSelectedDevice(
        serial: String,
        bootCompleted: String,
        powerState: String,
        windowPolicy: String,
    ): String? {
        val isBooted = bootCompleted == "1"
        val isAwake = Regex("(?m)^\\s*mWakefulness=Awake\\s*$").containsMatchIn(powerState) ||
            Regex("(?m)^\\s*mInteractive=true\\s*$").containsMatchIn(powerState)
        fun readBooleanPolicyField(name: String): Boolean? =
            Regex("(?m)^\\s*${Regex.escape(name)}=(true|false)\\s*$")
                .find(windowPolicy)
                ?.groupValues
                ?.get(1)
                ?.toBooleanStrict()

        // Android 7.0 can leave showingAndNotOccluded=true after the launcher is visible while
        // reporting the authoritative mIsShowing=false. Prefer explicit keyguard state and use
        // older/version-specific fields only when the stronger signal is absent.
        val isKeyguardShowing = readBooleanPolicyField("mIsShowing")
            ?: readBooleanPolicyField("mKeyguardShowing")
            ?: readBooleanPolicyField("showingAndNotOccluded")
            ?: false
        val failures = buildList {
            if (!isBooted) add("Android has not completed booting")
            if (!isAwake) add("the display is not awake")
            if (isKeyguardShowing) add("the keyguard is showing")
        }
        return if (failures.isEmpty()) {
            null
        } else {
            "Android connected-test preflight failed for '$serial': ${failures.joinToString()}. " +
                "Wake and unlock the selected device, keep its screen on, then rerun the task. " +
                "The gate deliberately does not bypass a secure keyguard."
        }
    }
}
