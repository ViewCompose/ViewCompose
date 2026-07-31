package com.viewcompose.studio.preview

import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.nio.file.Path
import javax.swing.AbstractAction
import javax.swing.Box
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.math.roundToInt

internal class ViewComposePreviewToolWindowPanel(
    detection: ViewComposeProjectDetection,
    projectRoot: Path?,
    initialLanguage: PreviewUiLanguage,
    private val onVariantSelected: (String) -> Unit,
    private val onNavigateToSource: (StudioPreviewSourceLocation) -> Unit,
    private val onNavigateToRuntimeSource: (List<StudioPreviewSourceCallSite>) -> Unit,
) : SimpleToolWindowPanel(true, true) {
    private val contentPanel = JPanel(BorderLayout())
    private val detectionEvidence = detection.evidencePath
    private val projectRoot = projectRoot?.toAbsolutePath()?.normalize()
    private var language = initialLanguage
    private var currentState: ViewComposePreviewPanelState = ViewComposePreviewPanelState.Empty
    private var previewZoomOption: PreviewZoomOption = PreviewZoomOption.Fit
    private var selectedDiagnosticsTabIndex: Int = 0
    private var showLayoutBounds: Boolean = false
    private val messages: PreviewUiMessages
        get() = PreviewUiMessages.forLanguage(language)

    val preferredFocusComponent: JComponent
        get() = contentPanel

    init {
        setContent(contentPanel)
        showState(ViewComposePreviewPanelState.Empty)
    }

    fun showState(state: ViewComposePreviewPanelState) {
        currentState = state
        contentPanel.removeAll()
        when (state) {
            ViewComposePreviewPanelState.Empty -> showEmptyState()
            is ViewComposePreviewPanelState.Loading -> showLoadingState(state)
            is ViewComposePreviewPanelState.Rendered -> showRenderedState(state.result)
            is ViewComposePreviewPanelState.Failed -> showFailureState(state.result)
        }
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    fun setLanguage(language: PreviewUiLanguage) {
        if (this.language == language) return
        this.language = language
        showState(currentState)
    }

    private fun showEmptyState() {
        contentPanel.border = JBUI.Borders.empty(24)
        contentPanel.add(
            header(
                title = messages.text("empty.title"),
                description = messages.text("empty.description"),
                selection = null,
                includeDetectionEvidence = true,
            ),
            BorderLayout.NORTH,
        )
    }

    private fun showLoadingState(state: ViewComposePreviewPanelState.Loading) {
        val previousResult = state.previousResult
        contentPanel.border = if (previousResult == null) {
            JBUI.Borders.empty(24)
        } else {
            JBUI.Borders.empty(12)
        }
        contentPanel.add(
            header(
                title = state.selection.symbolName,
                description = if (previousResult == null) {
                    messages.loadingMessage(state.message)
                } else {
                    messages.text(
                        "loading.showingPrevious",
                        messages.loadingMessage(state.message),
                    )
                },
                selection = state.selection,
            ),
            BorderLayout.NORTH,
        )
        previousResult?.let { result ->
            contentPanel.add(renderedContent(result), BorderLayout.CENTER)
        }
    }

    private fun showRenderedState(result: PreviewRenderOutcome.Success) {
        contentPanel.border = JBUI.Borders.empty(12)
        val duration = result.durationMillis?.let { millis -> " · ${millis} ms" }.orEmpty()
        val cache = if (result.cacheHit) " · ${messages.text("render.cache")}" else ""
        val selector = if (result.variants.size > 1) variantSelector(result) else null
        val renderedHeader = header(
            title = result.descriptorName,
            description = "${result.variantName}$duration$cache",
            selection = result.selection,
            trailing = selector,
        )
        contentPanel.add(
            renderedHeader,
            BorderLayout.NORTH,
        )
        contentPanel.add(renderedContent(result), BorderLayout.CENTER)
        if (result.diagnostics.isNotEmpty()) {
            contentPanel.add(
                diagnosticsPanel(result.diagnostics),
                BorderLayout.SOUTH,
            )
        }
    }

    private fun renderedContent(result: PreviewRenderOutcome.Success): JComponent {
        val snapshot = result.renderSnapshot
        val previewPanel = previewImagePanel(
            image = result.image,
            nativeViews = snapshot?.nativeViewTree.orEmpty(),
        )
        val renderedContent = if (snapshot == null) {
            previewPanel
        } else {
            JTabbedPane().apply {
                border = JBUI.Borders.emptyTop(8)
                addTab(messages.text("tab.preview"), previewPanel)
                addTab(messages.text("tab.structure"), renderStructurePanel(snapshot))
                addTab(messages.text("tab.views"), nativeViewsPanel(snapshot.nativeViewTree))
                addTab(messages.text("tab.composition"), compositionPanel(snapshot.composition))
                addTab(messages.text("tab.patches"), patchesPanel(snapshot.patches))
                selectedIndex = selectedDiagnosticsTabIndex.coerceIn(0, tabCount - 1)
                addChangeListener {
                    selectedDiagnosticsTabIndex = selectedIndex
                }
            }
        }
        return renderedContent
    }

    private fun variantSelector(result: PreviewRenderOutcome.Success): JComponent {
        val choices = result.variants.map { variant ->
            PreviewVariantChoice(
                id = variant.id,
                displayName = variant.displayName,
            )
        }
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyLeft(16)
            add(
                JComboBox(choices.toTypedArray()).apply {
                    toolTipText = messages.text("configuration")
                    accessibleContext.accessibleName = messages.text("configuration")
                    val boundedWidth = preferredSize.width
                        .coerceAtLeast(JBUI.scale(180))
                        .coerceAtMost(JBUI.scale(280))
                    preferredSize = Dimension(boundedWidth, preferredSize.height)
                    selectedItem = choices.first { choice ->
                        choice.id == result.selectedVariantId
                    }
                    addActionListener {
                        val choice = selectedItem as? PreviewVariantChoice
                            ?: return@addActionListener
                        if (choice.id != result.selectedVariantId) {
                            onVariantSelected(choice.id)
                        }
                    }
                },
                BorderLayout.NORTH,
            )
        }
    }

    private fun renderStructurePanel(snapshot: StudioPreviewRenderSnapshot): JComponent {
        val structure = snapshot.structure
        val stats = snapshot.stats
        val summary = messages.text(
            "structure.summary",
            structure.vnodeCount,
            structure.mountedNodeCount,
            structure.maxVNodeDepth,
            structure.maxMountedDepth,
            stats.inserts,
            stats.reuses,
            stats.removals,
            stats.reboundNodes,
            stats.patchedNodes,
            stats.skippedBindings,
            stats.skippedSubtrees,
        )
        val root = DefaultMutableTreeNode(messages.text("tree.vnode"))
        snapshot.tree.forEach { node ->
            root.add(node.toSwingTreeNode())
        }
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(readOnlyText(summary), BorderLayout.NORTH)
            add(
                JBScrollPane(
                    sourceNavigableTree(root).apply {
                        isRootVisible = false
                        showsRootHandles = true
                    },
                ).apply {
                    border = JBUI.Borders.emptyTop(8)
                },
                BorderLayout.CENTER,
            )
        }
    }

    private fun compositionPanel(snapshot: StudioPreviewCompositionSnapshot): JComponent {
        val text = buildString {
            appendLine(
                messages.text(
                    "composition.summary",
                    snapshot.invalidatedScopeCount,
                    snapshot.recomposedScopeCount,
                    snapshot.skippedScopeCount,
                ),
            )
            snapshot.scopes.forEach { scope ->
                appendLine()
                append(
                    when {
                        scope.recomposed -> messages.text("composition.recomposed")
                        scope.skipped -> messages.text("composition.skipped")
                        else -> messages.text("composition.clean")
                    },
                )
                append(" · ${scope.path}")
                appendLine()
                append("  ${messages.text("composition.signature")}: ${scope.signature}")
                if (scope.reasons.isNotEmpty()) {
                    appendLine()
                    append("  ${messages.text("composition.reasons")}: ${scope.reasons.joinToString()}")
                }
                scope.locals.forEach { local ->
                    appendLine()
                    append("  ${local.name} = ${local.value}")
                }
                appendLine()
            }
        }.trim()
        return JBScrollPane(
            readOnlyText(text.ifBlank { messages.text("composition.empty") }),
        ).apply {
            border = JBUI.Borders.empty(8)
        }
    }

    private fun nativeViewsPanel(views: List<StudioPreviewNativeViewNode>): JComponent {
        val root = DefaultMutableTreeNode(messages.text("tree.androidView"))
        views.forEach { view ->
            root.add(view.toSwingTreeNode())
        }
        val tree = sourceNavigableTree(root).apply {
            isRootVisible = false
            showsRootHandles = true
        }
        repeat(tree.rowCount) { row ->
            tree.expandRow(row)
        }
        return JBScrollPane(tree).apply {
            border = JBUI.Borders.empty(8)
        }
    }

    private fun previewImagePanel(
        image: BufferedImage,
        nativeViews: List<StudioPreviewNativeViewNode>,
    ): JComponent {
        val canvas = PreviewImageCanvas(
            image = image,
            nativeViews = nativeViews,
            initialZoomOption = previewZoomOption,
            sourceNavigationHint = messages.text("source.navigationHint"),
            onNavigateToSource = onNavigateToRuntimeSource,
        ).apply {
            showLayoutBounds = this@ViewComposePreviewToolWindowPanel.showLayoutBounds
        }
        val imageScrollPane = JBScrollPane(canvas).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(JBUI.scale(360), JBUI.scale(600))
        }
        fun updateCanvasScale() {
            canvas.updateViewportSize(imageScrollPane.viewport.extentSize)
        }
        imageScrollPane.viewport.addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent) {
                    updateCanvasScale()
                }
            },
        )
        SwingUtilities.invokeLater(::updateCanvasScale)

        val zoomChoices = PreviewZoomOption.entries.map { option ->
            PreviewZoomChoice(
                option = option,
                displayName = when (option) {
                    PreviewZoomOption.Fit -> messages.text("preview.zoom.fit")
                    else -> messages.text(
                        "preview.zoom.percent",
                        checkNotNull(option.fixedScale).times(100).roundToInt(),
                    )
                },
            )
        }
        val zoomSelector = JComboBox(zoomChoices.toTypedArray()).apply {
            toolTipText = messages.text("preview.zoom")
            accessibleContext.accessibleName = messages.text("preview.zoom")
            selectedItem = zoomChoices.first { choice ->
                choice.option == previewZoomOption
            }
            addActionListener {
                val choice = selectedItem as? PreviewZoomChoice
                    ?: return@addActionListener
                if (choice.option != previewZoomOption) {
                    previewZoomOption = choice.option
                    canvas.zoomOption = choice.option
                    SwingUtilities.invokeLater(::updateCanvasScale)
                }
            }
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    border = JBUI.Borders.empty(8, 4, 4, 4)
                    if (nativeViews.isNotEmpty()) {
                        add(
                            JCheckBox(messages.text("preview.showLayoutBounds")).apply {
                                isOpaque = false
                                isSelected = showLayoutBounds
                                addActionListener {
                                    canvas.showLayoutBounds = isSelected
                                    showLayoutBounds = isSelected
                                }
                            },
                            BorderLayout.WEST,
                        )
                    }
                    add(
                        JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0)).apply {
                            isOpaque = false
                            if (nativeViews.isNotEmpty()) {
                                add(
                                    JBLabel(
                                        messages.text(
                                            "preview.viewCount",
                                            nativeViews.sumOf(
                                                StudioPreviewNativeViewNode::nodeCount,
                                            ),
                                        ),
                                    ),
                                )
                            }
                            add(JBLabel(messages.text("preview.zoom")))
                            add(zoomSelector)
                        },
                        BorderLayout.EAST,
                    )
                },
                BorderLayout.NORTH,
            )
            add(imageScrollPane, BorderLayout.CENTER)
        }
    }

    private fun patchesPanel(patches: List<StudioPreviewPatchRecord>): JComponent {
        val text = patches.joinToString("\n") { patch ->
            buildString {
                append(patch.operation)
                append(" · ")
                append(patch.type)
                patch.key?.let { key -> append(" · key=$key") }
                patch.parentKey?.let { key -> append(" · parent=$key") }
                append(" · index=${patch.index}")
                if (patch.moved) append(" · moved")
                patch.detail?.let { detail -> append(" · $detail") }
            }
        }.ifBlank { messages.text("patch.empty") }
        return JBScrollPane(readOnlyText(text)).apply {
            border = JBUI.Borders.empty(8)
        }
    }

    private fun readOnlyText(text: String): JBTextArea {
        return JBTextArea(text).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            background = contentPanel.background
            border = JBUI.Borders.empty(8)
        }
    }

    private fun sourceNavigableTree(root: DefaultMutableTreeNode): JTree {
        return JTree(root).apply {
            toolTipText = messages.text("source.navigationHint")
            addMouseListener(
                object : MouseAdapter() {
                    override fun mouseClicked(event: MouseEvent) {
                        if (event.clickCount < 2) return
                        val path = getPathForLocation(event.x, event.y) ?: return
                        selectionPath = path
                        path.sourceCallSites()?.let(onNavigateToRuntimeSource)
                    }
                },
            )
            inputMap.put(
                KeyStroke.getKeyStroke("ENTER"),
                SOURCE_NAVIGATION_ACTION,
            )
            actionMap.put(
                SOURCE_NAVIGATION_ACTION,
                object : AbstractAction() {
                    override fun actionPerformed(event: java.awt.event.ActionEvent?) {
                        selectionPath?.sourceCallSites()?.let(onNavigateToRuntimeSource)
                    }
                },
            )
        }
    }

    private fun showFailureState(result: PreviewRenderOutcome.Failure) {
        contentPanel.border = JBUI.Borders.empty(24)
        contentPanel.add(
            header(
                title = messages.failureTitle(result.title),
                description = result.selection.symbolName,
                selection = result.selection,
            ),
            BorderLayout.NORTH,
        )
        val diagnostics = buildString {
            result.diagnostics.forEach { diagnostic ->
                append("[${diagnostic.phase}] ${diagnostic.message}")
                diagnostic.sourceLocation?.let { source ->
                    append("\n${source.filePath}:${source.line}:${source.column}")
                }
                diagnostic.details?.let { details ->
                    append("\n$details")
                }
                append("\n\n")
            }
            result.details?.let(::append)
        }.trim()
        val textArea = JBTextArea(diagnostics.ifBlank { messages.text("diagnostic.empty") }).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            border = JBUI.Borders.empty(12, 0, 0, 0)
            background = contentPanel.background
        }
        contentPanel.add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                diagnosticSourceLinks(result.diagnostics)?.let { links ->
                    add(links, BorderLayout.NORTH)
                }
                add(
                    JBScrollPane(textArea).apply {
                        border = JBUI.Borders.empty()
                    },
                    BorderLayout.CENTER,
                )
            },
            BorderLayout.CENTER,
        )
    }

    private fun header(
        title: String,
        description: String,
        selection: PreviewSourceSelection?,
        includeDetectionEvidence: Boolean = false,
        trailing: JComponent? = null,
    ): JComponent {
        val primaryInfo = Box.createVerticalBox().apply {
            add(JBLabel(title).apply {
                alignmentX = LEFT_ALIGNMENT
                font = font.deriveFont(Font.BOLD)
            })
            add(Box.createVerticalStrut(JBUI.scale(5)))
            add(JBLabel(description).apply {
                alignmentX = LEFT_ALIGNMENT
            })
            if (includeDetectionEvidence) {
                detectionEvidence?.let { evidencePath ->
                    add(Box.createVerticalStrut(JBUI.scale(12)))
                    add(
                        JBLabel(
                            messages.text(
                                "project.detectedFrom",
                                presentableProjectPath(projectRoot, evidencePath.toString()),
                            ),
                        ).apply {
                            alignmentX = LEFT_ALIGNMENT
                            toolTipText = evidencePath.toString()
                        },
                    )
                }
            }
        }
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(primaryInfo, BorderLayout.CENTER)
            trailing?.let { component ->
                add(component, BorderLayout.EAST)
            }
            selection?.let { source ->
                add(
                    JPanel(BorderLayout()).apply {
                        isOpaque = false
                        border = JBUI.Borders.emptyTop(6)
                        add(
                            ActionLink(source.presentablePathText(projectRoot)) {
                                onNavigateToSource(source.toStudioSourceLocation())
                            }.apply {
                                toolTipText = source.filePath
                            },
                            BorderLayout.WEST,
                        )
                    },
                    BorderLayout.SOUTH,
                )
            }
        }
    }

    private fun diagnosticsPanel(
        diagnostics: List<StudioPreviewDiagnostic>,
    ): JComponent {
        return Box.createVerticalBox().apply {
            border = JBUI.Borders.emptyTop(8)
            diagnostics.forEachIndexed { index, diagnostic ->
                if (index > 0) {
                    add(Box.createVerticalStrut(JBUI.scale(4)))
                }
                add(
                    JBLabel("[${diagnostic.severity}] ${diagnostic.message}").apply {
                        alignmentX = LEFT_ALIGNMENT
                    },
                )
                diagnostic.sourceLocation?.let { source ->
                    add(
                        ActionLink(source.presentableLinkText(projectRoot, messages)) {
                            onNavigateToSource(source)
                        }.apply {
                            alignmentX = LEFT_ALIGNMENT
                        },
                    )
                }
            }
        }
    }

    private fun diagnosticSourceLinks(
        diagnostics: List<StudioPreviewDiagnostic>,
    ): JComponent? {
        val sources = diagnostics.mapNotNull(StudioPreviewDiagnostic::sourceLocation).distinct()
        if (sources.isEmpty()) return null
        return Box.createVerticalBox().apply {
            border = JBUI.Borders.empty(12, 0, 4, 0)
            sources.forEach { source ->
                add(
                    ActionLink(source.presentableLinkText(projectRoot, messages)) {
                        onNavigateToSource(source)
                    }.apply {
                        alignmentX = LEFT_ALIGNMENT
                    },
                )
            }
        }
    }
}

private fun StudioPreviewRenderTreeNode.toSwingTreeNode(): DefaultMutableTreeNode {
    val label = buildString {
        append(type)
        key?.let { value -> append(" · key=$value") }
    }
    val swingNode = DefaultMutableTreeNode(
        PreviewTreeEntry(
            label = label,
            sourceCallSites = sourceCallSites,
        ),
    )
    children.forEach { child ->
        swingNode.add(child.toSwingTreeNode())
    }
    return swingNode
}

private fun StudioPreviewNativeViewNode.toSwingTreeNode(): DefaultMutableTreeNode {
    val simpleClassName = className.substringAfterLast('.')
    val label = buildString {
        append(simpleClassName)
        append(" · ")
        append(bounds.width)
        append('×')
        append(bounds.height)
        append(" @ ")
        append(bounds.left)
        append(',')
        append(bounds.top)
        if (measuredWidth != bounds.width || measuredHeight != bounds.height) {
            append(" · measured=")
            append(measuredWidth)
            append('×')
            append(measuredHeight)
        }
        if (visibility != "VISIBLE") {
            append(" · ")
            append(visibility)
        }
    }
    val swingNode = DefaultMutableTreeNode(
        PreviewTreeEntry(
            label = label,
            sourceCallSites = sourceCallSites,
        ),
    )
    children.forEach { child ->
        swingNode.add(child.toSwingTreeNode())
    }
    return swingNode
}

private fun StudioPreviewNativeViewNode.nodeCount(): Int {
    return 1 + children.sumOf(StudioPreviewNativeViewNode::nodeCount)
}

private class PreviewImageCanvas(
    private val image: BufferedImage,
    private val nativeViews: List<StudioPreviewNativeViewNode>,
    initialZoomOption: PreviewZoomOption,
    private val sourceNavigationHint: String,
    private val onNavigateToSource: (List<StudioPreviewSourceCallSite>) -> Unit,
) : JComponent() {
    var zoomOption: PreviewZoomOption = initialZoomOption
        set(value) {
            if (field == value) return
            field = value
            updateScale()
        }

    var showLayoutBounds: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    private var viewportSize: Dimension = Dimension(image.width, image.height)
    private var scale: Double = 1.0
    private var selectedView: StudioPreviewNativeViewNode? = null

    init {
        minimumSize = Dimension(1, 1)
        isOpaque = true
        ToolTipManager.sharedInstance().registerComponent(this)
        addMouseMotionListener(
            object : MouseMotionAdapter() {
                override fun mouseMoved(event: MouseEvent) {
                    cursor = if (mappedViewAt(event.x, event.y) == null) {
                        Cursor.getDefaultCursor()
                    } else {
                        Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    }
                }
            },
        )
        addMouseListener(
            object : MouseAdapter() {
                override fun mouseExited(event: MouseEvent) {
                    cursor = Cursor.getDefaultCursor()
                }

                override fun mouseClicked(event: MouseEvent) {
                    selectedView = mappedViewAt(event.x, event.y)
                    repaint()
                    if (event.clickCount >= 2) {
                        selectedView
                            ?.sourceCallSites
                            ?.takeIf(List<StudioPreviewSourceCallSite>::isNotEmpty)
                            ?.let(onNavigateToSource)
                    }
                }
            },
        )
        updateScale()
    }

    fun updateViewportSize(size: Dimension) {
        if (size.width <= 0 || size.height <= 0 || size == viewportSize) return
        viewportSize = Dimension(size)
        updateScale()
    }

    private fun updateScale() {
        scale = calculatePreviewScale(
            option = zoomOption,
            imageWidth = image.width,
            imageHeight = image.height,
            viewportWidth = viewportSize.width,
            viewportHeight = viewportSize.height,
        )
        val scaledWidth = (image.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (image.height * scale).roundToInt().coerceAtLeast(1)
        preferredSize = Dimension(
            scaledWidth.coerceAtLeast(viewportSize.width),
            scaledHeight.coerceAtLeast(viewportSize.height),
        )
        revalidate()
        repaint()
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val view = mappedViewAt(event.x, event.y) ?: return null
        return "$sourceNavigationHint · ${view.className.substringAfterLast('.')}"
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val placement = imagePlacement()
        val graphics2D = graphics.create() as Graphics2D
        try {
            graphics2D.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON,
            )
            graphics2D.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            graphics2D.drawImage(
                image,
                placement.left,
                placement.top,
                placement.width,
                placement.height,
                null,
            )
            if (showLayoutBounds) {
                graphics2D.stroke = BasicStroke(JBUI.scale(1).toFloat())
                nativeViews.forEach { view ->
                    graphics2D.paintViewBounds(
                        view = view,
                        imageLeft = placement.left,
                        imageTop = placement.top,
                        scale = scale,
                        depth = 0,
                    )
                }
            }
            selectedView?.let { view ->
                graphics2D.paintSelectedView(
                    view = view,
                    imageLeft = placement.left,
                    imageTop = placement.top,
                    scale = scale,
                )
            }
        } finally {
            graphics2D.dispose()
        }
    }

    private fun mappedViewAt(
        componentX: Int,
        componentY: Int,
    ): StudioPreviewNativeViewNode? {
        val placement = imagePlacement()
        if (
            componentX < placement.left ||
            componentX >= placement.left + placement.width ||
            componentY < placement.top ||
            componentY >= placement.top + placement.height
        ) {
            return null
        }
        val imageX = ((componentX - placement.left) / scale).toInt()
        val imageY = ((componentY - placement.top) / scale).toInt()
        return findMappedNativeViewAt(nativeViews, imageX, imageY)
    }

    private fun imagePlacement(): PreviewImagePlacement {
        val scaledWidth = (image.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (image.height * scale).roundToInt().coerceAtLeast(1)
        return PreviewImagePlacement(
            left = ((width - scaledWidth) / 2).coerceAtLeast(0),
            top = ((height - scaledHeight) / 2).coerceAtLeast(0),
            width = scaledWidth,
            height = scaledHeight,
        )
    }
}

private fun Graphics2D.paintSelectedView(
    view: StudioPreviewNativeViewNode,
    imageLeft: Int,
    imageTop: Int,
    scale: Double,
) {
    val bounds = view.bounds
    if (bounds.width <= 0 || bounds.height <= 0) return
    val scaledLeft = imageLeft + (bounds.left * scale).roundToInt()
    val scaledTop = imageTop + (bounds.top * scale).roundToInt()
    val scaledWidth = (bounds.width * scale).roundToInt().coerceAtLeast(1)
    val scaledHeight = (bounds.height * scale).roundToInt().coerceAtLeast(1)
    color = Color(SOURCE_SELECTION_COLOR.red, SOURCE_SELECTION_COLOR.green, SOURCE_SELECTION_COLOR.blue, 28)
    fillRect(scaledLeft, scaledTop, scaledWidth, scaledHeight)
    color = SOURCE_SELECTION_COLOR
    stroke = BasicStroke(JBUI.scale(2).toFloat())
    drawRect(
        scaledLeft,
        scaledTop,
        (scaledWidth - 1).coerceAtLeast(0),
        (scaledHeight - 1).coerceAtLeast(0),
    )
}

private fun Graphics2D.paintViewBounds(
    view: StudioPreviewNativeViewNode,
    imageLeft: Int,
    imageTop: Int,
    scale: Double,
    depth: Int,
) {
    val bounds = view.bounds
    if (depth > 0 && bounds.width > 0 && bounds.height > 0) {
        val baseColor = LAYOUT_BOUND_COLORS[(depth - 1) % LAYOUT_BOUND_COLORS.size]
        val scaledLeft = imageLeft + (bounds.left * scale).roundToInt()
        val scaledTop = imageTop + (bounds.top * scale).roundToInt()
        val scaledWidth = (bounds.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (bounds.height * scale).roundToInt().coerceAtLeast(1)
        color = Color(baseColor.red, baseColor.green, baseColor.blue, 22)
        fillRect(
            scaledLeft,
            scaledTop,
            scaledWidth,
            scaledHeight,
        )
        color = Color(baseColor.red, baseColor.green, baseColor.blue, 190)
        drawRect(
            scaledLeft,
            scaledTop,
            (scaledWidth - 1).coerceAtLeast(0),
            (scaledHeight - 1).coerceAtLeast(0),
        )
    }
    view.children.forEach { child ->
        paintViewBounds(
            view = child,
            imageLeft = imageLeft,
            imageTop = imageTop,
            scale = scale,
            depth = depth + 1,
        )
    }
}

private val LAYOUT_BOUND_COLORS = listOf(
    Color(0x2F, 0x80, 0xED),
    Color(0x27, 0xAE, 0x60),
    Color(0xF2, 0x99, 0x4A),
    Color(0x9B, 0x51, 0xE0),
)

private val SOURCE_SELECTION_COLOR = JBColor(Color(0x2F, 0x80, 0xED), Color(0x64, 0xB5, 0xF6))

private const val SOURCE_NAVIGATION_ACTION = "viewcompose.preview.navigateToRuntimeSource"

private data class PreviewTreeEntry(
    val label: String,
    val sourceCallSites: List<StudioPreviewSourceCallSite>,
) {
    override fun toString(): String = label
}

private fun javax.swing.tree.TreePath.sourceCallSites(): List<StudioPreviewSourceCallSite>? {
    val node = lastPathComponent as? DefaultMutableTreeNode ?: return null
    return (node.userObject as? PreviewTreeEntry)
        ?.sourceCallSites
        ?.takeIf(List<StudioPreviewSourceCallSite>::isNotEmpty)
}

private data class PreviewImagePlacement(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

private data class PreviewVariantChoice(
    val id: String,
    val displayName: String,
) {
    override fun toString(): String = displayName
}

private data class PreviewZoomChoice(
    val option: PreviewZoomOption,
    val displayName: String,
) {
    override fun toString(): String = displayName
}

internal fun presentableProjectPath(
    projectRoot: Path?,
    filePath: String,
): String {
    val normalizedPath = runCatching {
        Path.of(filePath).toAbsolutePath().normalize()
    }.getOrNull() ?: return filePath
    val normalizedRoot = projectRoot?.toAbsolutePath()?.normalize()
    val relativePath = if (
        normalizedRoot != null &&
        normalizedPath.startsWith(normalizedRoot)
    ) {
        normalizedRoot.relativize(normalizedPath)
    } else {
        null
    }
    return relativePath
        ?.joinToString(separator = "/") { segment -> segment.toString() }
        ?: normalizedPath.toString()
}

private fun PreviewSourceSelection.presentablePathText(projectRoot: Path?): String {
    return "${presentableProjectPath(projectRoot, filePath)}:$line"
}

private fun PreviewSourceSelection.toStudioSourceLocation(): StudioPreviewSourceLocation {
    return StudioPreviewSourceLocation(
        filePath = filePath,
        line = line,
        column = 1,
        symbolName = symbolName,
    )
}

private fun StudioPreviewSourceLocation.presentableLinkText(
    projectRoot: Path?,
    messages: PreviewUiMessages,
): String {
    return messages.text(
        "source.open",
        presentableProjectPath(projectRoot, filePath),
        line,
    )
}
