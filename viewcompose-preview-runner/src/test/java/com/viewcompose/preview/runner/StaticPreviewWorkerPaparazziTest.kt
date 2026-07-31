package com.viewcompose.preview.runner

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.android.resources.Density
import com.android.resources.LayoutDirection
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.viewcompose.preview.PreviewThemeProvider
import com.viewcompose.preview.PreviewThemeResolution
import com.viewcompose.preview.tooling.PreviewClippingState
import com.viewcompose.preview.tooling.PreviewConfiguration
import com.viewcompose.preview.tooling.PreviewDescriptor
import com.viewcompose.preview.tooling.PreviewJvmEntryPoint
import com.viewcompose.preview.tooling.PreviewLayoutDiagnosticKind
import com.viewcompose.preview.tooling.PreviewLayoutDirection
import com.viewcompose.preview.tooling.PreviewNativeViewNode
import com.viewcompose.preview.tooling.PreviewProtocolJson
import com.viewcompose.preview.tooling.PreviewRenderRequest
import com.viewcompose.preview.tooling.PreviewRenderStatus
import com.viewcompose.preview.tooling.PreviewRenderTreeNode
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.PreviewVariant
import com.viewcompose.preview.tooling.viewportHeightDp
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.width
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.AndroidDynamicColorPolicy
import com.viewcompose.widget.core.AndroidThemeBridge
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.Environment
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.UiThemeOrigin
import com.viewcompose.widget.core.UiThemeTokens
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
        val expectedWidthPx = (request.configuration.widthDp * request.configuration.density)
            .roundToInt()
        val expectedHeightPx =
            (request.configuration.viewportHeightDp * request.configuration.density).roundToInt()
        assertEquals(expectedWidthPx, pngDimensions.first)
        assertEquals(expectedHeightPx, pngDimensions.second)
        assertTrue(treeFile.isFile)

        val snapshot = PreviewProtocolJson.decodeRenderSnapshot(treeFile.readText())
        assertEquals(1, snapshot.structure.vnodeCount)
        assertEquals(1, snapshot.stats.inserts)
        assertTrue(snapshot.tree.containsNodeType("Text"))
        val renderText = checkNotNull(snapshot.tree.findNodeType("Text"))
        assertTrue(renderText.nodeId?.startsWith("node-") == true)
        assertTrue(
            renderText.sourceCallSites.any { source ->
                source.fileName == "StaticPreviewWorkerPaparazziTest.kt"
            },
        )
        assertTrue(
            renderText.sourceCallSites.none { source ->
                source.className.startsWith("com.viewcompose.runtime.") ||
                    source.className.startsWith("com.viewcompose.host.android.runtime.")
            },
        )
        val nativeRoot = snapshot.nativeViewTree.single()
        assertEquals("android.widget.FrameLayout", nativeRoot.className)
        assertEquals(expectedWidthPx, nativeRoot.bounds.right)
        assertEquals(expectedHeightPx, nativeRoot.bounds.bottom)
        val nativeText = checkNotNull(nativeRoot.findNativeView("android.widget.TextView"))
        assertTrue(nativeText.bounds.right > nativeText.bounds.left)
        assertTrue(nativeText.bounds.bottom > nativeText.bounds.top)
        assertEquals(renderText.nodeId, nativeText.nodeId)
        assertEquals(renderText.sourceCallSites, nativeText.sourceCallSites)
        assertEquals("true", nativeText.properties["enabled"])
        assertTrue(nativeText.properties["text"].orEmpty().isNotBlank())
        val insertPatch = snapshot.patches.single { patch -> patch.type == "Text" }
        assertEquals(renderText.nodeId, insertPatch.nodeId)
        assertEquals(renderText.sourceCallSites, insertPatch.sourceCallSites)
        assertNotNull(snapshot.composition)
    }

    @Test
    fun `auto height expands a fill-parent lazy column until all content is visible`() {
        val variant = PreviewVariant(
            id = "auto-height",
            displayName = "Auto height",
            configuration = PreviewConfiguration(widthDp = 240),
        )
        val autoHeightEntry = StaticPreviewEntry(
            descriptor = entry().descriptor.copy(variants = listOf(variant)),
        ) {
            LazyColumn(
                items = (0 until 24).toList(),
                key = { index -> index },
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                Text(
                    text = "Row $index",
                    modifier = Modifier.height(80.dp),
                )
            }
        }
        val autoHeightRequest = request(
            entry = autoHeightEntry,
            outputDirectory = temporaryFolder.newFolder("auto-height-preview"),
        )
        paparazzi.unsafeUpdateConfig(
            deviceConfig = autoHeightRequest.configuration.toDeviceConfig(),
        )

        val response = StaticPreviewWorker().render(
            context = paparazzi.context,
            request = autoHeightRequest,
            entry = autoHeightEntry,
        )

        assertEquals(PreviewRenderStatus.Success, response.status)
        val artifacts = checkNotNull(response.artifacts)
        val dimensions = File(checkNotNull(artifacts.imagePath)).readPngDimensions()
        assertEquals(
            (autoHeightRequest.configuration.widthDp * autoHeightRequest.configuration.density)
                .roundToInt(),
            dimensions.first,
        )
        assertTrue(
            dimensions.second >
                (autoHeightRequest.configuration.viewportHeightDp *
                    autoHeightRequest.configuration.density).roundToInt(),
        )
        val snapshot = PreviewProtocolJson.decodeRenderSnapshot(
            File(checkNotNull(artifacts.renderTreePath)).readText(),
        )
        assertEquals(dimensions.second, snapshot.nativeViewTree.single().bounds.bottom)
        assertTrue(snapshot.warnings.none { warning -> warning.contains("capture limit") })
    }

    @Test
    fun `snapshot captures effective bounds after parent clipping`() {
        val root = FrameLayout(paparazzi.context)
        val child = TextView(paparazzi.context).apply {
            text = "Partially visible"
        }
        root.addView(
            child,
            FrameLayout.LayoutParams(80, 24).apply {
                leftMargin = 60
            },
        )
        root.measure(
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 100, 100)

        val capture = PreviewNativeViewSnapshotter.capture(root)
        val capturedChild = capture.roots.single().children.single()

        assertEquals(PreviewClippingState.PartiallyClipped, capturedChild.clippingState)
        assertEquals(60, capturedChild.visibleBounds?.left)
        assertEquals(100, capturedChild.visibleBounds?.right)
        assertEquals("android.widget.FrameLayout", capturedChild.clippingAncestorClassName)
        assertEquals("Partially visible", capturedChild.properties["text"])
        assertEquals("true", capturedChild.properties["enabled"])
    }

    @Test
    fun `source aware text ellipsis is exported as a layout diagnostic`() {
        val variant = PreviewVariant(
            id = "truncated",
            displayName = "Truncated",
            configuration = PreviewConfiguration(
                widthDp = 120,
                heightDp = 80,
            ),
        )
        val truncatedEntry = StaticPreviewEntry(
            descriptor = entry().descriptor.copy(variants = listOf(variant)),
        ) {
            Text(
                text = "This preview text cannot fit on one short line",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(48.dp),
            )
        }
        val truncatedRequest = request(
            entry = truncatedEntry,
            outputDirectory = temporaryFolder.newFolder("truncated-preview"),
        )
        paparazzi.unsafeUpdateConfig(
            deviceConfig = truncatedRequest.configuration.toDeviceConfig(),
        )

        val mount = StaticPreviewRenderer.mount(
            context = paparazzi.context,
            request = truncatedRequest,
            entry = truncatedEntry,
        )

        assertTrue(mount is StaticPreviewMountResult.Success)
        (mount as StaticPreviewMountResult.Success).frame.use { frame ->
            val diagnostic = frame.snapshot.layoutDiagnostics.single { candidate ->
                candidate.kind == PreviewLayoutDiagnosticKind.TextEllipsized
            }
            assertTrue(diagnostic.nodeId?.startsWith("node-") == true)
            assertTrue(
                diagnostic.sourceCallSites.any { source ->
                    source.fileName == "StaticPreviewWorkerPaparazziTest.kt"
                },
            )
            assertTrue(diagnostic.metrics.getValue("ellipsizedLineCount") > 0)
        }
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
    fun `resolver installs the application theme provider for root and DSL content`() {
        resolvedProviderEntryTokens = null
        PaparazziTestPreviewThemeProvider.lastTokens = null
        val original = entry()
        val themedEntry = original.copy(
            descriptor = original.descriptor.copy(
                entryPoint = PreviewJvmEntryPoint(
                    ownerClassName =
                        "com.viewcompose.preview.runner.StaticPreviewWorkerPaparazziTestKt",
                    methodName = "providerResolvedPreviewEntryPoint",
                    methodDescriptor = "(Lcom/viewcompose/widget/core/UiTreeBuilder;)V",
                ),
                themeProviderClassName = PaparazziTestPreviewThemeProvider::class.java.name,
            ),
        )
        val request = request(
            entry = themedEntry,
            outputDirectory = temporaryFolder.newFolder("provider-themed-preview"),
        )
        var rootBackground: Int? = null
        val worker = StaticPreviewWorker { view, outputFile ->
            rootBackground = (view.background as ColorDrawable).color
            AndroidBitmapCaptureBackend.capture(view, outputFile)
        }
        paparazzi.unsafeUpdateConfig(deviceConfig = request.configuration.toDeviceConfig())

        val response = worker.render(
            context = paparazzi.context,
            request = request,
            classLoader = checkNotNull(javaClass.classLoader),
        )

        assertEquals(PreviewRenderStatus.Success, response.status)
        val providedTokens = checkNotNull(PaparazziTestPreviewThemeProvider.lastTokens)
        assertEquals(PROVIDER_BACKGROUND_COLOR, rootBackground)
        assertEquals(providedTokens, resolvedProviderEntryTokens)
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
        var observedTheme: UiThemeTokens? = null
        val configuredEntry = StaticPreviewEntry(descriptor) {
            observedTheme = Theme.current
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
        assertEquals(UiThemeOrigin.AndroidTheme, observedTheme?.metadata?.origin)
    }

    @Test
    fun `preview canvas paints the configured application theme background`() {
        PreviewTheme.entries.forEachIndexed { index, theme ->
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
            val configuredContext = PreviewAndroidContextFactory.create(
                base = paparazzi.context,
                preview = themedRequest.configuration,
            )
            val expectedBackground = AndroidThemeBridge.fromContext(
                context = configuredContext,
                dynamicColorPolicy = AndroidDynamicColorPolicy.Disabled,
            ).colors.background

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

    private fun List<PreviewRenderTreeNode>.findNodeType(type: String): PreviewRenderTreeNode? {
        return firstNotNullOfOrNull { node ->
            node.takeIf { it.type == type } ?: node.children.findNodeType(type)
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

private var resolvedProviderEntryTokens: UiThemeTokens? = null

fun UiTreeBuilder.providerResolvedPreviewEntryPoint() {
    resolvedProviderEntryTokens = Theme.current
    Text("Provider themed preview")
}

class PaparazziTestPreviewThemeProvider : PreviewThemeProvider {
    override fun resolve(
        context: Context,
        theme: PreviewTheme,
    ): PreviewThemeResolution {
        val tokens = AndroidThemeBridge.fromContext(
            context = context,
            dynamicColorPolicy = AndroidDynamicColorPolicy.Disabled,
        ).let { base ->
            base.copy(
                colors = base.colors.copy(background = PROVIDER_BACKGROUND_COLOR),
            )
        }
        lastTokens = tokens
        return PreviewThemeResolution(
            context = context,
            tokens = tokens,
        )
    }

    companion object {
        var lastTokens: UiThemeTokens? = null
    }
}

private fun PreviewConfiguration.toDeviceConfig(): DeviceConfig {
    val densityDpi = (density * DENSITY_DEFAULT).roundToInt().coerceAtLeast(1)
    val resolvedHeightDp = viewportHeightDp
    return DeviceConfig.PIXEL_5.copy(
        screenWidth = (widthDp * density).roundToInt(),
        screenHeight = (resolvedHeightDp * density).roundToInt(),
        xdpi = densityDpi,
        ydpi = densityDpi,
        orientation = if (widthDp > resolvedHeightDp) {
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
private const val PROVIDER_BACKGROUND_COLOR: Int = 0xFF123456.toInt()
