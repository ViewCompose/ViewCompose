import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.Properties

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

abstract class VerifyMarketplacePublishingConfigurationTask : DefaultTask() {
    @get:Input
    abstract val pluginVersion: Property<String>

    @get:Input
    abstract val pluginGroup: Property<String>

    @TaskAction
    fun verifyConfiguration() {
        val configuredVersion = pluginVersion.get()
        val configuredGroup = pluginGroup.get()
        check(configuredVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
            "Marketplace releases require a stable semantic version, found '$configuredVersion'."
        }
        check(configuredGroup == "com.viewcompose.studio") {
            "Unexpected Studio plugin group '$configuredGroup'."
        }
    }
}

val publishingPropertiesFile = rootProject.file("../../gradle/viewcompose-publishing.properties")
val publishingProperties = Properties().apply {
    check(publishingPropertiesFile.isFile) {
        "Missing shared publication metadata: ${publishingPropertiesFile.absolutePath}"
    }
    publishingPropertiesFile.inputStream().use(::load)
}

group = "com.viewcompose.studio"
version = providers.gradleProperty("viewComposeStudioPluginVersion")
    .orElse(
        checkNotNull(publishingProperties.getProperty("plugin.viewcompose-studio.version")) {
            "Missing plugin.viewcompose-studio.version in ${publishingPropertiesFile.absolutePath}"
        },
    )
    .get()

val expectedAndroidStudioBuild = "AI-261.25134.95.2612.15914620"
val marketplaceSinceBuild = "261.25134"
val marketplaceUntilBuild = "261.*"
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
val marketplaceToken = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
    .orElse(providers.environmentVariable("PUBLISH_TOKEN"))
    .orElse(providers.gradleProperty("viewComposeMarketplaceToken"))
val marketplaceCertificateChain = providers.environmentVariable("JETBRAINS_CERTIFICATE_CHAIN")
    .orElse(providers.environmentVariable("CERTIFICATE_CHAIN"))
val marketplacePrivateKey = providers.environmentVariable("JETBRAINS_PRIVATE_KEY")
    .orElse(providers.environmentVariable("PRIVATE_KEY"))
val marketplacePrivateKeyPassword =
    providers.environmentVariable("JETBRAINS_PRIVATE_KEY_PASSWORD")
        .orElse(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
        .orElse(providers.gradleProperty("viewComposeMarketplacePrivateKeyPassword"))
val defaultMarketplaceSigningDirectory =
    file("${System.getProperty("user.home")}/.config/viewcompose/marketplace-signing")
val marketplaceCertificateChainFile = providers
    .gradleProperty("viewComposeMarketplaceCertificateChainFile")
    .map { path -> file(path) }
    .orElse(defaultMarketplaceSigningDirectory.resolve("chain.crt"))
val marketplacePrivateKeyFile = providers
    .gradleProperty("viewComposeMarketplacePrivateKeyFile")
    .map { path -> file(path) }
    .orElse(defaultMarketplaceSigningDirectory.resolve("private.pem"))

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        local(androidStudioHome.absolutePath)
        bundledPlugin("org.jetbrains.android")
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
            latest {
                types = listOf(IntelliJPlatformType.AndroidStudio)
                channels = listOf(
                    ProductRelease.Channel.RELEASE,
                    ProductRelease.Channel.PATCH,
                )
                sinceBuild = marketplaceSinceBuild
                untilBuild = marketplaceUntilBuild
            }
            latest {
                types = listOf(IntelliJPlatformType.AndroidStudio)
                channels = listOf(
                    ProductRelease.Channel.CANARY,
                    ProductRelease.Channel.RC,
                )
                sinceBuild = marketplaceSinceBuild
                untilBuild = marketplaceUntilBuild
            }
        }
    }
    pluginConfiguration {
        id = "com.viewcompose.studio.preview"
        name = "ViewCompose Preview"
        version = project.version.toString()
        description = """
            <p>ViewCompose Preview adds static Android View previews for ViewCompose projects in
            Android Studio.</p>
            <ul>
              <li>Navigate between Kotlin DSL source and rendered View nodes.</li>
              <li>Open the ViewCompose DSL currently visible in a debuggable app on a selected
              connected Android device.</li>
              <li>Preview light and dark themes, locales, layout directions, densities, font
              scales, and device sizes.</li>
              <li>Inspect native Views, layout diagnostics, VNode structure, composition data,
              and patch activity.</li>
              <li>Use incremental source-save refresh, full rebuilds, bounded disk caching,
              zoom, pan, and an all-previews catalog.</li>
            </ul>
            <p>Rendering runs in isolated worker processes and does not load application code into
            Android Studio. The plugin does not collect telemetry or transmit project source
            code.</p>
        """.trimIndent()
        changeNotes = """
            <p>Device-to-source navigation for running ViewCompose screens.</p>
            <ul>
              <li>Locate the ViewCompose DSL currently visible on a connected debuggable device.</li>
              <li>Choose explicitly between multiple devices or equally visible page sessions.</li>
              <li>Prefer authored content over shared scaffold callers and request snapshots only
              when the action is invoked.</li>
            </ul>
        """.trimIndent()
        ideaVersion {
            sinceBuild = marketplaceSinceBuild
            untilBuild = marketplaceUntilBuild
        }
        vendor {
            name = "ViewCompose"
            url = "https://github.com/ViewCompose"
        }
    }
    publishing {
        token.set(marketplaceToken)
        channels.set(
            providers.gradleProperty("viewComposeMarketplaceChannels")
                .map { value -> value.split(',').map(String::trim).filter(String::isNotEmpty) }
                .orElse(listOf("default")),
        )
    }
    signing {
        certificateChain.set(marketplaceCertificateChain)
        privateKey.set(marketplacePrivateKey)
        password.set(marketplacePrivateKeyPassword)
        certificateChainFile.set(layout.file(marketplaceCertificateChainFile))
        privateKeyFile.set(layout.file(marketplacePrivateKeyFile))
    }
}

tasks.named<Jar>("jar") {
    from(rootProject.file("../../LICENSE")) {
        into("META-INF")
        rename { "LICENSE" }
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

val verifyMarketplacePublishingConfiguration =
    tasks.register<VerifyMarketplacePublishingConfigurationTask>(
        "verifyMarketplacePublishingConfiguration",
    ) {
        group = "verification"
        description = "Verifies the stable plugin identity and Marketplace release version."
        pluginVersion.set(project.version.toString())
        pluginGroup.set(project.group.toString())
    }

tasks.register("prepareMarketplaceRelease") {
    group = "publishing"
    description = "Tests, verifies, and packages the plugin ZIP without uploading it."
    dependsOn(
        verifyMarketplacePublishingConfiguration,
        "check",
        "verifyPlugin",
        "verifyPluginStructure",
        "buildPlugin",
    )
}

tasks.register("prepareSignedMarketplaceRelease") {
    group = "publishing"
    description = "Prepares the Marketplace ZIP, signs it, and verifies the author signature."
    dependsOn(
        "prepareMarketplaceRelease",
        "signPlugin",
        "verifyPluginSignature",
    )
}

tasks.named("verifyPluginSignature") {
    dependsOn("signPlugin")
}

tasks.named("publishPlugin") {
    dependsOn(
        verifyMarketplacePublishingConfiguration,
        "verifyPluginSignature",
    )
}

tasks.wrapper {
    gradleVersion = "9.6.1"
    distributionType = Wrapper.DistributionType.BIN
}
