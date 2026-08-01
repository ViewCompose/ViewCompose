package com.viewcompose.preview.gradle

import com.viewcompose.preview.tooling.PreviewProtocolJson
import com.viewcompose.preview.tooling.PreviewBuildInputKind
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.ViewComposePreview
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.zip.ZipFile
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ViewComposePreviewGradlePluginFunctionalTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `android variant task exports manifest and compiled descriptors`() {
        val sdkDirectory = findAndroidSdkDirectory()
        assumeTrue("Android SDK is required for the Gradle plugin functional test.", sdkDirectory != null)
        val project = temporaryFolder.newFolder("android-project")
        val previewCoreLocation = File(
            ViewComposePreview::class.java.protectionDomain.codeSource.location.toURI(),
        )
        val previewCorePath = previewCoreLocation.absolutePath
            .replace("\\", "\\\\")
            .replace("'", "\\'")
        project.resolve("settings.gradle").writeText(
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "preview-fixture"
            include(":app")
            include(":library")
            """.trimIndent(),
        )
        project.resolve("local.properties").writeText(
            "sdk.dir=${sdkDirectory!!.absolutePath.replace("\\", "\\\\")}",
        )
        project.resolve("gradle.properties").writeText("android.useAndroidX=true\n")
        project.resolve("app/build.gradle").apply {
            parentFile.mkdirs()
            writeText(
                """
                plugins {
                    id "com.android.application" version "8.13.2"
                    id "com.viewcompose.preview"
                }

                android {
                    namespace "sample.fixture"
                    compileSdk 35
                    defaultConfig {
                        applicationId "sample.fixture"
                        minSdk 24
                        targetSdk 35
                        versionCode 1
                        versionName "1"
                    }
                }

                dependencies {
                    implementation "androidx.appcompat:appcompat-resources:1.7.0"
                    implementation project(":library")
                    compileOnly files('$previewCorePath')
                    add(
                        "viewComposePreviewWorkerHost",
                        "org.jetbrains.kotlin:kotlin-stdlib:2.0.21"
                    )
                }

                tasks.configureEach {
                    if (
                        name == "renderDebugViewComposePreview" ||
                        name == "refreshDebugViewComposePreview"
                    ) {
                        workerMainClass.set("sample.FakePreviewWorkerHost")
                    }
                }
                """.trimIndent(),
            )
        }
        project.resolve("library/build.gradle").apply {
            parentFile.mkdirs()
            writeText(
                """
                plugins {
                    id "com.android.library" version "8.13.2"
                }

                android {
                    namespace "sample.library"
                    compileSdk 35
                    defaultConfig {
                        minSdk 24
                    }
                }
                """.trimIndent(),
            )
        }
        project.resolve("library/src/main/AndroidManifest.xml").apply {
            parentFile.mkdirs()
            writeText("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" />")
        }
        project.resolve("library/src/main/res/values/strings.xml").apply {
            parentFile.mkdirs()
            writeText("<resources><string name=\"library_name\">Library</string></resources>")
        }
        project.resolve("app/src/main/AndroidManifest.xml").apply {
            parentFile.mkdirs()
            writeText("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" />")
        }
        project.resolve("app/src/main/res/values/strings.xml").apply {
            parentFile.mkdirs()
            writeText("<resources><string name=\"app_name\">Fixture</string></resources>")
        }
        project.resolve("app/src/main/assets/app.txt").apply {
            parentFile.mkdirs()
            writeText("app asset")
        }
        project.writeJava(
            "app/src/main/java/com/viewcompose/widget/core/UiTreeBuilder.java",
            """
            package com.viewcompose.widget.core;
            public final class UiTreeBuilder {}
            """,
        )
        project.writeJava(
            "app/src/main/java/sample/SamplePreviews.java",
            """
            package sample;
            import com.viewcompose.preview.tooling.PreviewLightDark;
            import com.viewcompose.preview.tooling.PreviewTheme;
            import com.viewcompose.preview.tooling.ViewComposePreview;
            import com.viewcompose.widget.core.UiTreeBuilder;

            public final class SamplePreviews {
                @ViewComposePreview(
                    name = "Dark phone",
                    group = "demo",
                    widthDp = 360,
                    heightDp = 720,
                    theme = PreviewTheme.Dark
                )
                public static void card(UiTreeBuilder builder) {}

                @PreviewLightDark
                public static void themes(UiTreeBuilder builder) {}
            }
            """,
        )
        project.writeJava(
            "app/src/main/java/sample/FakePreviewWorkerHost.java",
            """
            package sample;

            import java.nio.charset.StandardCharsets;
            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.nio.file.Paths;

            public final class FakePreviewWorkerHost {
                public static void main(String[] args) throws Exception {
                    String command = text(Paths.get(args[0]));
                    Path requestPath = Paths.get(value(command, "renderRequestPath"));
                    Path responsePath = Paths.get(value(command, "renderResponsePath"));
                    String request = text(requestPath);
                    String requestId = value(request, "requestId");
                    String previewId = value(request, "id");
                    String variantId = value(request, "variantId");
                    Path output = Paths.get(value(request, "outputDirectory"));
                    Files.createDirectories(output);
                    Path image = output.resolve("preview.png");
                    Path tree = output.resolve("render-tree.json");
                    Files.write(image, new byte[] { 1 });
                    Files.write(tree, "{}".getBytes(StandardCharsets.UTF_8));
                    String response =
                        "{\"protocolVersion\":1," +
                        "\"requestId\":\"" + json(requestId) + "\"," +
                        "\"previewId\":\"" + json(previewId) + "\"," +
                        "\"variantId\":\"" + json(variantId) + "\"," +
                        "\"status\":\"Success\"," +
                        "\"artifacts\":{" +
                        "\"imagePath\":\"" + json(image.toAbsolutePath().toString()) + "\"," +
                        "\"renderTreePath\":\"" + json(tree.toAbsolutePath().toString()) + "\"}}";
                    Files.write(responsePath, response.getBytes(StandardCharsets.UTF_8));
                }

                private static String text(Path path) throws Exception {
                    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                }

                private static String value(String json, String key) {
                    String marker = "\"" + key + "\"";
                    int markerIndex = json.indexOf(marker);
                    if (markerIndex < 0) throw new IllegalArgumentException("Missing " + key);
                    int start = json.indexOf('"', json.indexOf(':', markerIndex) + 1) + 1;
                    int end = json.indexOf('"', start);
                    return json.substring(start, end);
                }

                private static String json(String value) {
                    return value.replace("\\", "\\\\").replace("\"", "\\\"");
                }
            }
            """,
        )

        val result = GradleRunner.create()
            .withProjectDir(project)
            .withArguments(
                "--stacktrace",
                ":app:discoverDebugViewComposePreviews",
                ":app:assembleRelease",
            )
            .withPluginClasspath()
            .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":app:discoverDebugViewComposePreviews")?.outcome,
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":app:assembleRelease")?.outcome,
        )
        val output = project.resolve("app/build/viewcompose-preview/debug")
        val manifest = PreviewProtocolJson.decodeBuildManifest(
            output.resolve("build-manifest.json").readText(),
        )
        val catalog = PreviewProtocolJson.decodeDescriptorCatalog(
            output.resolve("descriptors.json").readText(),
        )
        assertEquals(":app", manifest.modulePath)
        assertEquals("debug", manifest.buildVariant)
        assertEquals("sample.fixture", manifest.namespace)
        assertEquals(35, manifest.compileSdk)
        assertTrue("sample.fixture" in manifest.resourcePackageNames)
        assertTrue(manifest.resourcePackageNames.size > 1)
        assertEquals(manifest.inputFingerprint, catalog.buildFingerprint)
        assertTrue(manifest.inputs.isNotEmpty())
        val inputKinds = manifest.inputs.map { input -> input.kind }.toSet()
        assertTrue(PreviewBuildInputKind.LocalResourceDirectory in inputKinds)
        assertTrue(PreviewBuildInputKind.LibraryResourceDirectory in inputKinds)
        assertTrue(PreviewBuildInputKind.LocalAssetDirectory in inputKinds)
        assertTrue(PreviewBuildInputKind.ResourcePackageFile in inputKinds)
        assertEquals(2, catalog.descriptors.size)
        val descriptor = catalog.descriptors.single { preview ->
            preview.entryPoint.methodName == "card"
        }
        assertEquals("card", descriptor.entryPoint.methodName)
        assertEquals("demo", descriptor.group)
        assertEquals(360, descriptor.variants.single().configuration.widthDp)
        assertEquals(720, descriptor.variants.single().configuration.heightDp)
        assertEquals(PreviewTheme.Dark, descriptor.variants.single().configuration.theme)
        val themes = catalog.descriptors.single { preview ->
            preview.entryPoint.methodName == "themes"
        }
        assertEquals(
            listOf(PreviewTheme.Light, PreviewTheme.Dark),
            themes.variants.map { variant -> variant.configuration.theme },
        )
        val releaseApk = project.resolve("app/build/outputs/apk/release/app-release-unsigned.apk")
        assertTrue(releaseApk.isFile)
        val releaseDexText = ZipFile(releaseApk).use { zip ->
            zip.entries().asSequence()
                .filter { entry -> entry.name.matches(Regex("classes[0-9]*\\.dex")) }
                .joinToString(separator = "") { entry ->
                    String(
                        zip.getInputStream(entry).use { input -> input.readBytes() },
                        StandardCharsets.ISO_8859_1,
                    )
                }
        }
        assertTrue(releaseDexText.contains("Lsample/SamplePreviews;"))
        assertFalse(
            releaseDexText.contains(
                "Lcom/viewcompose/preview/tooling/ViewComposePreview;",
            ),
        )
        assertFalse(
            releaseDexText.contains(
                "Lcom/viewcompose/preview/tooling/PreviewLightDark;",
            ),
        )

        val tasks = GradleRunner.create()
            .withProjectDir(project)
            .withArguments(":app:tasks", "--all")
            .withPluginClasspath()
            .build()
        assertTrue(tasks.output.contains("renderDebugViewComposePreview"))
        assertTrue(tasks.output.contains("refreshDebugViewComposePreview"))
        assertTrue(tasks.output.contains("discoverDebugViewComposePreviews"))
        assertFalse(tasks.output.contains("renderReleaseViewComposePreview"))
        assertFalse(tasks.output.contains("discoverReleaseViewComposePreviews"))

        val renderArguments = arrayOf(
            ":app:renderDebugViewComposePreview",
            "-P$PREVIEW_ID_PROJECT_PROPERTY=${descriptor.id}",
            "-P$PREVIEW_VARIANT_ID_PROJECT_PROPERTY=${descriptor.variants.single().id}",
            "--stacktrace",
        )
        val render = GradleRunner.create()
            .withProjectDir(project)
            .withArguments(*renderArguments)
            .withPluginClasspath()
            .build()
        assertEquals(
            TaskOutcome.SUCCESS,
            render.task(":app:renderDebugViewComposePreview")?.outcome,
        )
        assertTrue(render.output.contains("ViewCompose preview rendered:"))

        val cached = GradleRunner.create()
            .withProjectDir(project)
            .withArguments(*renderArguments)
            .withPluginClasspath()
            .build()
        assertEquals(
            TaskOutcome.SUCCESS,
            cached.task(":app:renderDebugViewComposePreview")?.outcome,
        )
        assertTrue(cached.output.contains("ViewCompose preview cache hit:"))

        val previewsSource = project.resolve("app/src/main/java/sample/SamplePreviews.java")
        previewsSource.writeText(
            previewsSource.readText().replace(
                "public static void card(UiTreeBuilder builder) {}",
                "public static void card(UiTreeBuilder builder) { int changed = 1; }",
            ),
        )
        val fastRefresh = GradleRunner.create()
            .withProjectDir(project)
            .withArguments(
                ":app:refreshDebugViewComposePreview",
                "-P$PREVIEW_ID_PROJECT_PROPERTY=${descriptor.id}",
                "-P$PREVIEW_VARIANT_ID_PROJECT_PROPERTY=${descriptor.variants.single().id}",
                "--stacktrace",
            )
            .withPluginClasspath()
            .build()
        assertEquals(
            TaskOutcome.SUCCESS,
            fastRefresh.task(":app:refreshDebugViewComposePreview")?.outcome,
        )
        assertEquals(null, fastRefresh.task(":app:discoverDebugViewComposePreviews"))
        assertTrue(fastRefresh.output.contains("ViewCompose preview rendered:"))
        val fastManifest = PreviewProtocolJson.decodeBuildManifest(
            output.resolve(FAST_BUILD_MANIFEST_FILE_NAME).readText(),
        )
        val fastCatalog = PreviewProtocolJson.decodeDescriptorCatalog(
            output.resolve(FAST_DESCRIPTOR_CATALOG_FILE_NAME).readText(),
        )
        assertEquals(fastManifest.inputFingerprint, fastCatalog.buildFingerprint)
        assertTrue(fastManifest.inputFingerprint != manifest.inputFingerprint)
        assertTrue(fastCatalog.descriptors.any { preview -> preview.id == descriptor.id })

        output.resolve("build-manifest.json").delete()
        val missingBaseline = GradleRunner.create()
            .withProjectDir(project)
            .withArguments(
                ":app:refreshDebugViewComposePreview",
                "-P$PREVIEW_ID_PROJECT_PROPERTY=${descriptor.id}",
                "-P$PREVIEW_VARIANT_ID_PROJECT_PROPERTY=${descriptor.variants.single().id}",
                "--stacktrace",
            )
            .withPluginClasspath()
            .buildAndFail()
        assertTrue(missingBaseline.output.contains(FAST_REFRESH_FALLBACK_MARKER))
    }

    private fun findAndroidSdkDirectory(): File? {
        System.getenv("ANDROID_HOME")
            ?.let(::File)
            ?.takeIf(File::isDirectory)
            ?.let { return it }
        val localProperties = generateSequence(File(System.getProperty("user.dir"))) { directory ->
            directory.parentFile
        }.map { directory -> directory.resolve("local.properties") }
            .firstOrNull(File::isFile)
            ?: return null
        val properties = Properties().apply {
            localProperties.inputStream().use(::load)
        }
        return properties.getProperty("sdk.dir")
            ?.let(::File)
            ?.takeIf(File::isDirectory)
    }

    private fun File.writeJava(
        relativePath: String,
        source: String,
    ) {
        resolve(relativePath).apply {
            parentFile.mkdirs()
            writeText(source.trimIndent())
        }
    }
}
