package com.viewcompose.preview.runner

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.android.resources.Density
import com.android.resources.LayoutDirection
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.viewcompose.preview.tooling.PreviewConfiguration
import com.viewcompose.preview.tooling.PreviewDescriptor
import com.viewcompose.preview.tooling.PreviewJvmEntryPoint
import com.viewcompose.preview.tooling.PreviewNativeViewNode
import com.viewcompose.preview.tooling.PreviewProtocolJson
import com.viewcompose.preview.tooling.PreviewRenderRequest
import com.viewcompose.preview.tooling.PreviewRenderStatus
import com.viewcompose.preview.tooling.PreviewRenderTreeNode
import com.viewcompose.preview.tooling.PreviewLayoutDirection
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.PreviewVariant
import com.viewcompose.widget.core.Environment
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.UiThemeDefaults
import com.viewcompose.widget.core.UiTreeBuilder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import kotlin.math.roundToInt

class StaticPreviewWorkerPaparazziTest {
    private val runtimeRootFallbackRule = TestRule { base: Statement, _: Description ->
        object : Statement() {
            override fun evaluate() {
                val key = "paparazzi.layoutlib.runtime.root"
                val original = System.getProperty(key)
                val patched = original?.replace("android-36", "android-35")
                if (patched != null && patched != original) {
                    System.setProperty(key, patched)
                }
                try {
                    base.evaluate()
                } finally {
                    if (original == null) {
                        System.clearProperty(key)
                    } else {
                        System.setProperty(key, original)
                    }
                }
            }
        }
    }
    private val temporaryFolder = TemporaryFolder()
    private val paparazzi = Paparazzi(
        environment = detectEnvironment().copy(compileSdkVersion = 35),
        deviceConfig = PreviewConfiguration().toDeviceConfig(),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(runtimeRootFallbackRule)
        .around(temporaryFolder)
        .around(paparazzi)

    @Test
    fun `worker renders native view image and structured diagnostics without compose`() {
        val outputDirectory = temporaryFolder.newFolder("static-preview")
        val entry = entry()
        val request = request(entry, outputDirectory)
        paparazzi.unsafeUpdateConfig(deviceConfig = request.configuration.toDeviceConfig())

        val response = StaticPreviewWorker().render(
            context = paparazzi.context,
            request = request,
            entry = entry,
        )

        assertEquals(PreviewRenderStatus.Success, response.status)
        val artifacts = checkNotNull(response.artifacts)
        val imageFile = File(checkNotNull(artifacts.imagePath))
        val treeFile = File(checkNotNull(artifacts.renderTreePath))
        assertTrue(imageFile.isFile)
        assertTrue(imageFile.length() > 0L)
        val pngDimensions = imageFile.readPngDimensions()
        assertEquals(request.configuration.widthDp, pngDimensions.first)
        assertEquals(request.configuration.heightDp, pngDimensions.second)
        assertTrue(treeFile.isFile)

        val snapshot = PreviewProtocolJson.decodeRenderSnapshot(treeFile.readText())
        assertEquals(1, snapshot.structure.vnodeCount)
        assertEquals(1, snapshot.stats.inserts)
        assertTrue(snapshot.tree.containsNodeType("Text"))
        val nativeRoot = snapshot.nativeViewTree.single()
        assertEquals("android.widget.FrameLayout", nativeRoot.className)
        assertEquals(request.configuration.widthDp, nativeRoot.bounds.right)
        assertEquals(request.configuration.heightDp, nativeRoot.bounds.bottom)
        val nativeText = checkNotNull(nativeRoot.findNativeView("android.widget.TextView"))
        assertTrue(nativeText.bounds.right > nativeText.bounds.left)
        assertTrue(nativeText.bounds.bottom > nativeText.bounds.top)
        assertNotNull(snapshot.composition)
    }

    @Test
    fun `missing compiled entry point returns structured diagnostic`() {
        val entry = entry()
        val request = request(
            entry = entry.copy(
                descriptor = entry.descriptor.copy(
                    entryPoint = PreviewJvmEntryPoint(
                        ownerClassName = "missing.PreviewKt",
                        methodName = "MissingPreview",
                    ),
                ),
            ),
            outputDirectory = temporaryFolder.newFolder("missing-preview"),
        )
        paparazzi.unsafeUpdateConfig(deviceConfig = request.configuration.toDeviceConfig())

        val response = StaticPreviewWorker().render(
            context = paparazzi.context,
            request = request,
            classLoader = checkNotNull(javaClass.classLoader),
        )

        assertEquals(PreviewRenderStatus.RenderFailure, response.status)
        assertEquals("entry-resolution", response.diagnostics.single().phase)
        assertTrue(response.artifacts == null)
    }

    @Test
    fun `resolver invokes public UiTreeBuilder extension entry point`() {
        val entry = entry().let { original ->
            original.copy(
                descriptor = original.descriptor.copy(
                    entryPoint = PreviewJvmEntryPoint(
                        ownerClassName =
                            "com.viewcompose.preview.runner.StaticPreviewWorkerPaparazziTestKt",
                        methodName = "resolvedStaticPreviewEntryPoint",
                        methodDescriptor =
                            "(Lcom/viewcompose/widget/core/UiTreeBuilder;)V",
                    ),
                ),
            )
        }
        val request = request(
            entry = entry,
            outputDirectory = temporaryFolder.newFolder("resolved-preview"),
        )
        paparazzi.unsafeUpdateConfig(deviceConfig = request.configuration.toDeviceConfig())

        val response = StaticPreviewWorker().render(
            context = paparazzi.context,
            request = request,
            classLoader = checkNotNull(javaClass.classLoader),
        )

        assertEquals(PreviewRenderStatus.Success, response.status)
    }

    @Test
    fun `explicit configuration reaches DSL and native canvas deterministically`() {
        val configuration = PreviewConfiguration(
            widthDp = 200,
            heightDp = 300,
            density = 1.25f,
            fontScale = 1.3f,
            localeTags = listOf("ar-EG", "en-US"),
            layoutDirection = PreviewLayoutDirection.Rtl,
            theme = PreviewTheme.Dark,
            apiLevel = Build.VERSION.SDK_INT,
        )
        val variant = PreviewVariant(
            id = "dark-rtl-accessibility",
            displayName = "Dark RTL accessibility",
            configuration = configuration,
        )
        val descriptor = entry().descriptor.copy(variants = listOf(variant))
        val configuredEntry = StaticPreviewEntry(descriptor) {
            Text(
                "${Environment.density.density}|" +
                    "${Environment.density.fontScale}|" +
                    "${Environment.localeTags.joinToString()}|" +
                    "${Environment.layoutDirection}|" +
                    Theme.current.metadata.isDark,
            )
        }
        val request = request(
            entry = configuredEntry,
            outputDirectory = temporaryFolder.newFolder("configured-preview"),
        )
        var observedText: String? = null
        var observedWidth = 0
        var observedHeight = 0
        var observedResourceLayoutDirection = View.LAYOUT_DIRECTION_LTR
        var observedResourceDensity = 0f
        var observedResourceFontScale = 0f
        var observedNightMode = Configuration.UI_MODE_NIGHT_UNDEFINED
        val worker = StaticPreviewWorker { view, outputFile ->
            observedText = view.findFirstText()
            observedWidth = view.width
            observedHeight = view.height
            observedResourceDensity = view.resources.displayMetrics.density
            observedResourceFontScale = view.resources.configuration.fontScale
            observedResourceLayoutDirection = view.resources.configuration.layoutDirection
            observedNightMode =
                view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            AndroidBitmapCaptureBackend.capture(view, outputFile)
        }
        paparazzi.unsafeUpdateConfig(deviceConfig = request.configuration.toDeviceConfig())

        val response = worker.render(
            context = paparazzi.context,
            request = request,
            entry = configuredEntry,
        )

        assertEquals(PreviewRenderStatus.Success, response.status)
        assertEquals("1.25|1.3|ar-EG, en-US|Rtl|true", observedText)
        assertEquals(250, observedWidth)
        assertEquals(375, observedHeight)
        assertEquals(1.25f, observedResourceDensity)
        assertEquals(1.3f, observedResourceFontScale)
        // Layoutlib keeps locale resource matching in the render-session qualifier even though
        // its mocked Configuration reports "und". Verify the worker-host contract directly.
        assertEquals("ar-rEG", configuration.toDeviceConfig().locale)
        assertEquals(View.LAYOUT_DIRECTION_RTL, observedResourceLayoutDirection)
        assertEquals(Configuration.UI_MODE_NIGHT_YES, observedNightMode)
    }

    @Test
    fun `preview canvas paints the requested theme background`() {
        listOf(
            PreviewTheme.Light to UiThemeDefaults.light().colors.background,
            PreviewTheme.Dark to UiThemeDefaults.dark().colors.background,
        ).forEachIndexed { index, (theme, expectedBackground) ->
            val variant = PreviewVariant(
                id = theme.name.lowercase(),
                displayName = theme.name,
                configuration = PreviewConfiguration(
                    theme = theme,
                    apiLevel = Build.VERSION.SDK_INT,
                ),
            )
            val themedEntry = entry().let { original ->
                original.copy(
                    descriptor = original.descriptor.copy(variants = listOf(variant)),
                )
            }
            val themedRequest = request(
                entry = themedEntry,
                outputDirectory = temporaryFolder.newFolder("theme-canvas-$index"),
            )
            paparazzi.unsafeUpdateConfig(
                deviceConfig = themedRequest.configuration.toDeviceConfig(),
            )

            val mount = StaticPreviewRenderer.mount(
                context = paparazzi.context,
                request = themedRequest,
                entry = themedEntry,
            )

            assertTrue(mount is StaticPreviewMountResult.Success)
            (mount as StaticPreviewMountResult.Success).frame.use { frame ->
                val background = frame.rootView.background as ColorDrawable
                assertEquals(expectedBackground, background.color)
            }
        }
    }

    @Test
    fun `API mismatch fails before mounting the preview`() {
        val mismatchedVariant = PreviewVariant(
            id = "wrong-api",
            displayName = "Wrong API",
            configuration = PreviewConfiguration(apiLevel = Build.VERSION.SDK_INT + 1),
        )
        val mismatchedEntry = entry().let { original ->
            original.copy(
                descriptor = original.descriptor.copy(variants = listOf(mismatchedVariant)),
            )
        }
        val response = StaticPreviewWorker().render(
            context = paparazzi.context,
            request = request(
                entry = mismatchedEntry,
                outputDirectory = temporaryFolder.newFolder("wrong-api"),
            ),
            entry = mismatchedEntry,
        )

        assertEquals(PreviewRenderStatus.RenderFailure, response.status)
        assertEquals("environment", response.diagnostics.single().phase)
    }

    private fun entry(): StaticPreviewEntry {
        val variant = PreviewVariant(
            id = "phone-light",
            displayName = "Phone Light",
            configuration = PreviewConfiguration(),
        )
        return StaticPreviewEntry(
            descriptor = PreviewDescriptor(
                id = "sample-static-preview",
                displayName = "Sample static preview",
                entryPoint = PreviewJvmEntryPoint(
                    ownerClassName = "sample.SamplePreviewKt",
                    methodName = "SamplePreview",
                ),
                variants = listOf(variant),
            ),
        ) {
            Text("Static preview")
        }
    }

    private fun request(
        entry: StaticPreviewEntry,
        outputDirectory: File,
    ): PreviewRenderRequest {
        return PreviewRenderRequest(
            requestId = "request-static-preview",
            descriptor = entry.descriptor,
            variantId = entry.descriptor.variants.single().id,
            modulePath = ":sample",
            buildVariant = "debug",
            buildFingerprint = "a".repeat(64),
            outputDirectory = outputDirectory.absolutePath,
        )
    }

    private fun List<PreviewRenderTreeNode>.containsNodeType(type: String): Boolean {
        return any { node ->
            node.type == type || node.children.containsNodeType(type)
        }
    }

    private fun PreviewNativeViewNode.findNativeView(className: String): PreviewNativeViewNode? {
        if (this.className == className) return this
        return children.firstNotNullOfOrNull { child ->
            child.findNativeView(className)
        }
    }

    private fun View.findFirstText(): String? {
        if (this is TextView) return text.toString()
        if (this !is ViewGroup) return null
        repeat(childCount) { index ->
            getChildAt(index).findFirstText()?.let { return it }
        }
        return null
    }

    private fun File.readPngDimensions(): Pair<Int, Int> {
        val header = inputStream().use { input ->
            ByteArray(PNG_HEADER_SIZE).also { bytes ->
                check(input.read(bytes) == bytes.size) { "Incomplete PNG header." }
            }
        }
        check(header.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)) {
            "Artifact is not a PNG file."
        }
        return header.readBigEndianInt(16) to header.readBigEndianInt(20)
    }

    private fun ByteArray.readBigEndianInt(offset: Int): Int {
        return ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
    }

    private companion object {
        const val PNG_HEADER_SIZE: Int = 24
        val PNG_SIGNATURE: ByteArray = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
    }
}

fun UiTreeBuilder.resolvedStaticPreviewEntryPoint() {
    Text("Resolved static preview")
}

private fun PreviewConfiguration.toDeviceConfig(): DeviceConfig {
    val densityDpi = (density * DENSITY_DEFAULT).roundToInt().coerceAtLeast(1)
    return DeviceConfig.PIXEL_5.copy(
        screenWidth = (widthDp * density).roundToInt(),
        screenHeight = (heightDp * density).roundToInt(),
        xdpi = densityDpi,
        ydpi = densityDpi,
        orientation = if (widthDp > heightDp) {
            ScreenOrientation.LANDSCAPE
        } else {
            ScreenOrientation.PORTRAIT
        },
        nightMode = when (theme) {
            PreviewTheme.Light -> NightMode.NOTNIGHT
            PreviewTheme.Dark -> NightMode.NIGHT
        },
        density = Density.create(densityDpi),
        fontScale = fontScale,
        layoutDirection = when (layoutDirection) {
            PreviewLayoutDirection.Ltr -> LayoutDirection.LTR
            PreviewLayoutDirection.Rtl -> LayoutDirection.RTL
        },
        locale = localeTags.first().toAndroidResourceLocale(),
        softButtons = false,
    )
}

private fun String.toAndroidResourceLocale(): String {
    val parts = split('-')
    return if (parts.size >= 2) {
        "${parts[0]}-r${parts[1].uppercase()}"
    } else {
        this
    }
}

private const val DENSITY_DEFAULT: Int = 160
