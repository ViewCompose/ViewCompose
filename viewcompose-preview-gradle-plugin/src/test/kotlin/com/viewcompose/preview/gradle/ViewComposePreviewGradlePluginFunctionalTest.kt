package com.viewcompose.preview.gradle

import com.viewcompose.preview.tooling.PreviewProtocolJson
import com.viewcompose.preview.tooling.PreviewTheme
import java.io.File
import java.util.Properties
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
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
            """.trimIndent(),
        )
        project.resolve("local.properties").writeText(
            "sdk.dir=${sdkDirectory!!.absolutePath.replace("\\", "\\\\")}",
        )
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
                """.trimIndent(),
            )
        }
        project.resolve("app/src/main/AndroidManifest.xml").apply {
            parentFile.mkdirs()
            writeText("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" />")
        }
        project.writeJava(
            "app/src/main/java/com/viewcompose/widget/core/UiTreeBuilder.java",
            """
            package com.viewcompose.widget.core;
            public final class UiTreeBuilder {}
            """,
        )
        project.writeJava(
            "app/src/main/java/com/viewcompose/preview/tooling/PreviewTheme.java",
            """
            package com.viewcompose.preview.tooling;
            public enum PreviewTheme { Light, Dark }
            """,
        )
        project.writeJava(
            "app/src/main/java/com/viewcompose/preview/tooling/PreviewLayoutDirection.java",
            """
            package com.viewcompose.preview.tooling;
            public enum PreviewLayoutDirection { Ltr, Rtl }
            """,
        )
        project.writeJava(
            "app/src/main/java/com/viewcompose/preview/tooling/ViewComposePreview.java",
            """
            package com.viewcompose.preview.tooling;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Target(ElementType.METHOD)
            @Retention(RetentionPolicy.RUNTIME)
            public @interface ViewComposePreview {
                String name() default "";
                String group() default "";
                int widthDp() default 411;
                int heightDp() default 891;
                float density() default 1f;
                float fontScale() default 1f;
                String localeTag() default "en-US";
                PreviewLayoutDirection layoutDirection() default PreviewLayoutDirection.Ltr;
                PreviewTheme theme() default PreviewTheme.Light;
                int apiLevel() default -1;
            }
            """,
        )
        project.writeJava(
            "app/src/main/java/sample/SamplePreviews.java",
            """
            package sample;
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
            }
            """,
        )

        val result = GradleRunner.create()
            .withProjectDir(project)
            .withArguments(
                "--stacktrace",
                ":app:discoverDebugViewComposePreviews",
            )
            .withPluginClasspath()
            .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":app:discoverDebugViewComposePreviews")?.outcome,
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
        assertEquals(manifest.inputFingerprint, catalog.buildFingerprint)
        assertTrue(manifest.inputs.isNotEmpty())
        assertEquals(1, catalog.descriptors.size)
        val descriptor = catalog.descriptors.single()
        assertEquals("card", descriptor.entryPoint.methodName)
        assertEquals("demo", descriptor.group)
        assertEquals(360, descriptor.variants.single().configuration.widthDp)
        assertEquals(720, descriptor.variants.single().configuration.heightDp)
        assertEquals(PreviewTheme.Dark, descriptor.variants.single().configuration.theme)
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
