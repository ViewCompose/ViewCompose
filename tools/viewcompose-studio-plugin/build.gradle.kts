import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

abstract class VerifyAndroidStudioBuildTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val productInfoFile: RegularFileProperty

    @get:Input
    abstract val expectedVersion: Property<String>

    @TaskAction
    fun verifyBuild() {
        val productInfo = productInfoFile.get().asFile.readText()
        val actualVersion = Regex(""""version"\s*:\s*"([^"]+)"""")
            .find(productInfo)
            ?.groupValues
            ?.get(1)
        check(actualVersion == expectedVersion.get()) {
            "Expected Android Studio '${expectedVersion.get()}', but found '$actualVersion' at " +
                "'${productInfoFile.get().asFile.absolutePath}'."
        }
    }
}

group = "com.viewcompose.studio"
version = "1.0.0"

val expectedAndroidStudioBuild = "AI-261.25134.95.2612.15914620"
val configuredAndroidStudioPath = providers
    .gradleProperty("viewComposeAndroidStudioPath")
    .orElse(providers.environmentVariable("ANDROID_STUDIO_HOME"))
val androidStudioHome = if (configuredAndroidStudioPath.isPresent) {
    file(configuredAndroidStudioPath.get())
} else {
    listOf(
        file("${System.getProperty("user.home")}/Applications/Android Studio.app"),
        file("/Applications/Android Studio.app"),
    ).firstOrNull(File::isDirectory)
        ?: file("${System.getProperty("user.home")}/Applications/Android Studio.app")
}
val androidStudioProductInfo = listOf(
    androidStudioHome.resolve("Contents/Resources/product-info.json"),
    androidStudioHome.resolve("Resources/product-info.json"),
).firstOrNull(File::isFile)
    ?: androidStudioHome.resolve("Contents/Resources/product-info.json")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        local(androidStudioHome.absolutePath)
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.gradle")
        bundledPlugin("org.jetbrains.kotlin")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
    }
}

intellijPlatform {
    buildSearchableOptions = false
    pluginVerification {
        ides {
            local(androidStudioHome)
        }
    }
    pluginConfiguration {
        id = "com.viewcompose.studio.preview"
        name = "ViewCompose Preview"
        version = project.version.toString()
        description = """
            Static Android View previews for ViewCompose projects.
            Rendering runs in an isolated process and never loads application code into Android Studio.
        """.trimIndent()
        ideaVersion {
            sinceBuild = "261.25134"
        }
        vendor {
            name = "ViewCompose"
        }
    }
}

val verifyTargetStudio = tasks.register<VerifyAndroidStudioBuildTask>("verifyTargetStudio") {
    group = "verification"
    description = "Verifies the exact local Android Studio build used by this plugin."
    productInfoFile.set(androidStudioProductInfo)
    expectedVersion.set(expectedAndroidStudioBuild)
}

tasks.named("check") {
    dependsOn(verifyTargetStudio)
}

tasks.matching { task ->
    task.name == "buildPlugin" ||
        task.name == "prepareSandbox" ||
        task.name == "verifyPluginProjectConfiguration"
}.configureEach {
    dependsOn(verifyTargetStudio)
}

tasks.wrapper {
    gradleVersion = "9.6.1"
    distributionType = Wrapper.DistributionType.BIN
}
