package com.viewcompose.studio.preview

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
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
import java.awt.GridLayout
import java.awt.Insets
import java.awt.Point
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseWheelEvent
import java.nio.file.Path
import java.util.LinkedHashMap
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.Icon
import javax.swing.JLayeredPane
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JTree
import javax.swing.JViewport
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.Timer
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
    private val onPresentationChanged: (String?, PreviewSourceSelection?) -> Unit = { _, _ -> },
) : SimpleToolWindowPanel(true, true) {
    private val contentPanel = JPanel(BorderLayout())
    private val detectionEvidence = detection.evidencePath
    private val projectRoot = projectRoot?.toAbsolutePath()?.normalize()
    private var language = initialLanguage
    private var currentState: ViewComposePreviewPanelState = ViewComposePreviewPanelState.Empty
    private var previewZoomOption: PreviewZoomOption = PreviewZoomOption.Fit
    private var previewCustomScale: Double? = null
    private var galleryDetailZoomOption: PreviewZoomOption = PreviewZoomOption.Fit
    private var galleryDetailCustomScale: Double? = null
    private var selectedDiagnosticsTabIndex: Int = 0
    private var showLayoutBounds: Boolean = false
    private var showLayoutDiagnostics: Boolean = true
    private var selectedRuntimeNodeId: String? = null
    private var latestCaretLocation: PreviewCaretLocation? = null
    private var nodeSelectionCoordinator: PreviewNodeSelectionCoordinator? = null
    private var galleryDetailWorker: SwingWorker<BufferedImage, Unit>? = null
    private val galleryThumbnails = mutableMapOf<Path, Icon>()
    private val galleryDetailImages = object : LinkedHashMap<Path, BufferedImage>(
        GALLERY_DETAIL_MEMORY_ENTRIES + 1,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Path, BufferedImage>?,
        ): Boolean = size > GALLERY_DETAIL_MEMORY_ENTRIES
    }
    private val messages: PreviewUiMessages
        get() = PreviewUiMessages.forLanguage(language)

    val preferredFocusComponent: JComponent
        get() = contentPanel

    init {
        setContent(contentPanel)
        showState(ViewComposePreviewPanelState.Empty)
    }

    fun showState(state: ViewComposePreviewPanelState) {
        galleryDetailWorker?.cancel(true)
        galleryDetailWorker = null
        galleryThumbnails.clear()
        val previousSource = currentState.previewPresentation().source
        val nextSource = state.previewPresentation().source
        if (nextSource != null && previousSource != nextSource) {
            previewZoomOption = PreviewZoomOption.Fit
            previewCustomScale = null
        }
        currentState = state
        val presentation = state.previewPresentation()
        onPresentationChanged(presentation.title, presentation.source)
        nodeSelectionCoordinator = null
        contentPanel.removeAll()
        when (state) {
            ViewComposePreviewPanelState.Empty -> showEmptyState()
            is ViewComposePreviewPanelState.Loading -> showLoadingState(state)
            is ViewComposePreviewPanelState.Rendered -> showRenderedState(state.result)
            is ViewComposePreviewPanelState.Failed -> showFailureState(state.result)
            is ViewComposePreviewPanelState.GalleryLoading -> showGalleryLoadingState(state)
            is ViewComposePreviewPanelState.Gallery -> showGalleryState(state.result)
            is ViewComposePreviewPanelState.GalleryFailed -> showGalleryFailureState(state.details)
        }
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    fun selectSourceLocation(
        filePath: String,
        lineCandidates: Collection<Int>,
    ) {
        latestCaretLocation = PreviewCaretLocation(filePath, lineCandidates.toList())
        nodeSelectionCoordinator?.selectSource(filePath, lineCandidates)
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
                includeDetectionEvidence = true,
            ),
            BorderLayout.NORTH,
        )
    }

    private fun showGalleryLoadingState(state: ViewComposePreviewPanelState.GalleryLoading) {
        contentPanel.border = JBUI.Borders.empty(12)
        contentPanel.add(
            header(
                title = messages.text("gallery.title"),
                description = messages.loadingMessage(state.message),
            ),
            BorderLayout.NORTH,
        )
        state.previousResult?.let { result ->
            contentPanel.add(galleryContent(result), BorderLayout.CENTER)
        }
    }

    private fun showGalleryState(result: PreviewGalleryResult) {
        contentPanel.border = JBUI.Borders.empty(12)
        contentPanel.add(
            header(
                title = messages.text("gallery.title"),
                description = messages.text(
                    "gallery.summary",
                    result.items.size,
                    result.failures.size,
                ),
            ),
            BorderLayout.NORTH,
        )
        contentPanel.add(galleryContent(result), BorderLayout.CENTER)
    }

    private fun showGalleryFailureState(details: String) {
        contentPanel.border = JBUI.Borders.empty(24)
        contentPanel.add(
            header(
                title = messages.text("gallery.title"),
                description = messages.text("gallery.failure"),
            ),
            BorderLayout.NORTH,
        )
        contentPanel.add(
            JBScrollPane(readOnlyText(details)).apply {
                border = JBUI.Borders.emptyTop(8)
            },
            BorderLayout.CENTER,
        )
    }

    private fun galleryContent(result: PreviewGalleryResult): JComponent {
        if (result.items.isEmpty()) {
            return readOnlyText(
                if (result.failures.isEmpty()) {
                    messages.text("gallery.empty")
                } else {
                    messages.text("gallery.allFailed", result.failures.size)
                },
            )
        }
        val list = JBList(result.items).apply {
            layoutOrientation = JList.HORIZONTAL_WRAP
            visibleRowCount = -1
            fixedCellWidth = JBUI.scale(GALLERY_CELL_WIDTH)
            fixedCellHeight = JBUI.scale(GALLERY_CELL_HEIGHT)
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = ListCellRenderer { _, value, _, isSelected, _ ->
                galleryCard(value, isSelected)
            }
            toolTipText = messages.text("gallery.navigationHint")
        }
        val galleryScrollPane = JBScrollPane(list).apply {
            border = JBUI.Borders.emptyTop(8)
            verticalScrollBar.unitIncrement = JBUI.scale(24)
        }
        val detailHost = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(8)
            add(readOnlyText(messages.text("gallery.detailEmpty")), BorderLayout.CENTER)
        }
        val overlay = PreviewGalleryOverlay(
            content = galleryScrollPane,
            onDismiss = {
                galleryDetailWorker?.cancel(true)
                galleryDetailWorker = null
            },
        )
        var detailGeneration = 0

        fun showDetail(item: PreviewGalleryItem) {
            galleryDetailWorker?.cancel(true)
            galleryDetailWorker = null
            detailGeneration += 1
            val generation = detailGeneration
            galleryDetailZoomOption = PreviewZoomOption.Fit
            galleryDetailCustomScale = null
            fun present(image: BufferedImage) {
                if (generation != detailGeneration || !overlay.isDetailVisible) return
                detailHost.removeAll()
                detailHost.add(galleryDetailPanel(item, image), BorderLayout.CENTER)
                detailHost.revalidate()
                detailHost.repaint()
            }

            detailHost.removeAll()
            detailHost.add(readOnlyText(messages.text("gallery.detailLoading")), BorderLayout.CENTER)
            overlay.showDetail(detailHost)
            galleryDetailImages[item.detailImagePath]?.let { cached ->
                present(cached)
                return
            }
            galleryDetailWorker = object : SwingWorker<BufferedImage, Unit>() {
                override fun doInBackground(): BufferedImage {
                    return loadBoundedPreviewImage(item.detailImagePath)
                }

                override fun done() {
                    if (isCancelled || generation != detailGeneration) return
                    galleryDetailWorker = null
                    runCatching { get() }
                        .onSuccess { image ->
                            galleryDetailImages[item.detailImagePath] = image
                            present(image)
                        }
                        .onFailure {
                            detailHost.removeAll()
                            detailHost.add(
                                readOnlyText(messages.text("gallery.detailFailure")),
                                BorderLayout.CENTER,
                            )
                            detailHost.revalidate()
                            detailHost.repaint()
                        }
                }
            }.also { worker -> worker.execute() }
        }

        val doublePressTracker = PreviewDoublePressTracker()
        var pendingDetailItem: PreviewGalleryItem? = null
        val showDetailTimer = Timer(GALLERY_DETAIL_OPEN_DELAY_MILLIS) {
            pendingDetailItem?.let(::showDetail)
            pendingDetailItem = null
        }.apply {
            isRepeats = false
        }
        list.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) {
                    if (!SwingUtilities.isLeftMouseButton(event)) return
                    val index = list.locationToIndex(event.point)
                    if (index < 0 || !list.getCellBounds(index, index).contains(event.point)) return
                    val item = list.model.getElementAt(index)
                    list.selectedIndex = index
                    val isDoublePress = doublePressTracker.register(
                        awtClickCount = event.clickCount,
                        eventMillis = event.`when`,
                        x = event.x,
                        y = event.y,
                    )
                    if (isDoublePress) {
                        showDetailTimer.stop()
                        pendingDetailItem = null
                        event.consume()
                        onNavigateToSource(item.selection.toStudioSourceLocation())
                    } else {
                        pendingDetailItem = item
                        showDetailTimer.restart()
                    }
                }
            }
        )
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "openDetail")
        list.actionMap.put(
            "openDetail",
            object : AbstractAction() {
                override fun actionPerformed(event: java.awt.event.ActionEvent?) {
                    list.selectedValue?.let(::showDetail)
                }
            },
        )
        return overlay
    }

    private fun galleryCard(
        result: PreviewGalleryItem,
        selected: Boolean,
    ): JComponent {
        val thumbnail = galleryThumbnails.getOrPut(result.thumbnailPath) {
            RetinaPreviewIcon(
                image = result.thumbnail,
                width = JBUI.scale(GALLERY_IMAGE_WIDTH),
                height = JBUI.scale(GALLERY_IMAGE_HEIGHT),
            )
        }
        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = if (selected) {
                JBColor(Color(0xE8, 0xF0, 0xFE), Color(0x35, 0x3A, 0x43))
            } else {
                contentPanel.background
            }
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                    if (selected) SOURCE_SELECTION_COLOR else PREVIEW_TOOLBAR_BORDER,
                    if (selected) JBUI.scale(2) else JBUI.scale(1),
                ),
                JBUI.Borders.empty(8),
            )
            add(
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(
                        JBLabel(result.descriptorName).apply {
                            font = font.deriveFont(Font.BOLD)
                        },
                        BorderLayout.NORTH,
                    )
                    add(
                        JBLabel(result.variantName).apply {
                            foreground = JBColor.GRAY
                        },
                        BorderLayout.SOUTH,
                    )
                },
                BorderLayout.NORTH,
            )
            add(
                JBLabel(thumbnail).apply {
                    horizontalAlignment = JBLabel.CENTER
                    verticalAlignment = JBLabel.CENTER
                    border = JBUI.Borders.emptyTop(8)
                },
                BorderLayout.CENTER,
            )
        }
    }

    private fun galleryDetailPanel(
        item: PreviewGalleryItem,
        image: BufferedImage,
    ): JComponent {
        val canvas = PreviewImageCanvas(
            image = image,
            nativeViews = emptyList(),
            layoutDiagnostics = emptyList(),
            initialZoomOption = galleryDetailZoomOption,
            initialCustomScale = galleryDetailCustomScale,
            sourceNavigationHint = messages.text("source.navigationHint"),
            onNavigateToSource = {},
            onNodeSelected = {},
            onContinuousZoomChanged = { scale -> galleryDetailCustomScale = scale },
            onBackgroundDoubleClick = {
                onNavigateToSource(item.selection.toStudioSourceLocation())
            },
        )
        val imageScrollPane = JBScrollPane(canvas).apply {
            border = JBUI.Borders.empty()
            verticalScrollBar.unitIncrement = JBUI.scale(24)
            horizontalScrollBar.unitIncrement = JBUI.scale(24)
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

        fun selectZoom(option: PreviewZoomOption) {
            galleryDetailZoomOption = option
            galleryDetailCustomScale = null
            canvas.zoomOption = option
            SwingUtilities.invokeLater(::updateCanvasScale)
        }
        val zoomToolbar = previewZoomToolbar(
            canvas = canvas,
            onZoomIn = { canvas.stepZoom(1) },
            onZoomOut = { canvas.stepZoom(-1) },
            onActualSize = { selectZoom(PreviewZoomOption.Percent100) },
            onFit = { selectZoom(PreviewZoomOption.Fit) },
        )
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(
                JPanel(GridLayout(0, 1, 0, JBUI.scale(3))).apply {
                    isOpaque = false
                    add(
                        JBLabel(item.descriptorName).apply {
                            font = font.deriveFont(Font.BOLD)
                        },
                    )
                    add(JBLabel(item.variantName).apply { foreground = JBColor.GRAY })
                    add(JBLabel(messages.text("gallery.detailHint")).apply { foreground = JBColor.GRAY })
                },
                BorderLayout.NORTH,
            )
            add(
                PreviewCanvasLayer(
                    scrollPane = imageScrollPane,
                    floatingToolbar = zoomToolbar,
                ),
                BorderLayout.CENTER,
            )
        }
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
                title = null,
                description = if (previousResult == null) {
                    messages.loadingMessage(state.message)
                } else {
                    messages.text(
                        "loading.showingPrevious",
                        messages.loadingMessage(state.message),
                    )
                },
            ),
            BorderLayout.NORTH,
        )
        previousResult?.let { result ->
            contentPanel.add(renderedContent(result), BorderLayout.CENTER)
        }
    }

    private fun showRenderedState(result: PreviewRenderOutcome.Success) {
        contentPanel.border = JBUI.Borders.empty(4)
        val selector = if (result.variants.size > 1) variantSelector(result) else null
        val metadata = buildList {
            result.durationMillis?.let { millis -> add("${millis} ms") }
            if (result.cacheHit) add(messages.text("render.cache"))
        }
        val description = buildList {
            if (selector == null) add(result.variantName)
            addAll(metadata)
        }.joinToString(" · ")
        val renderedHeader = header(
            title = null,
            description = description,
            trailing = selector,
        ).apply {
            border = JBUI.Borders.empty(0, 2, 2, 2)
        }
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
        val selectionCoordinator = snapshot?.let {
            PreviewNodeSelectionCoordinator(
                snapshot = it,
                initialNodeId = selectedRuntimeNodeId,
                onSelectionChanged = { nodeId -> selectedRuntimeNodeId = nodeId },
            ).also { coordinator ->
                latestCaretLocation?.let { caret ->
                    coordinator.selectSource(caret.filePath, caret.lineCandidates)
                }
            }
        }
        nodeSelectionCoordinator = selectionCoordinator
        val previewPanel = previewImagePanel(
            image = result.image,
            nativeViews = snapshot?.nativeViewTree.orEmpty(),
            layoutDiagnostics = snapshot?.layoutDiagnostics.orEmpty(),
            selectionCoordinator = selectionCoordinator,
        )
        val renderedContent = if (snapshot == null) {
            previewPanel
        } else {
            JTabbedPane().apply {
                border = JBUI.Borders.emptyTop(2)
                addTab(messages.text("tab.preview"), previewPanel)
                addTab(
                    messages.text("tab.views"),
                    nativeViewsPanel(snapshot.nativeViewTree, selectionCoordinator),
                )
                addTab(
                    messages.text("tab.layout"),
                    layoutDiagnosticsPanel(snapshot.layoutDiagnostics, selectionCoordinator),
                )
                addTab(
                    messages.text("tab.structure"),
                    renderStructurePanel(snapshot, selectionCoordinator),
                )
                addTab(messages.text("tab.composition"), compositionPanel(snapshot.composition))
                addTab(
                    messages.text("tab.patches"),
                    patchesPanel(snapshot.patches, selectionCoordinator),
                )
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
            border = JBUI.Borders.emptyLeft(8)
            add(
                JComboBox(choices.toTypedArray()).apply {
                    toolTipText = messages.text("configuration")
                    accessibleContext.accessibleName = messages.text("configuration")
                    val boundedWidth = preferredSize.width
                        .coerceAtLeast(JBUI.scale(160))
                        .coerceAtMost(JBUI.scale(240))
                    preferredSize = Dimension(boundedWidth, JBUI.scale(28))
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

    private fun renderStructurePanel(
        snapshot: StudioPreviewRenderSnapshot,
        selectionCoordinator: PreviewNodeSelectionCoordinator?,
    ): JComponent {
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
                    sourceNavigableTree(root, selectionCoordinator).apply {
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
        if (snapshot.scopes.isEmpty()) {
            return JBScrollPane(readOnlyText(messages.text("composition.empty"))).apply {
                border = JBUI.Borders.empty(8)
            }
        }
        val summary = messages.text(
            "composition.summary",
            snapshot.invalidatedScopeCount,
            snapshot.recomposedScopeCount,
            snapshot.skippedScopeCount,
        )
        val root = DefaultMutableTreeNode(messages.text("tree.composition"))
        snapshot.scopes.forEach { scope ->
            root.add(scope.toSwingTreeNode(messages))
        }
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(readOnlyText(summary), BorderLayout.NORTH)
            add(
                JBScrollPane(
                    sourceNavigableTree(root, selectionCoordinator = null).apply {
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

    private fun layoutDiagnosticsPanel(
        diagnostics: List<StudioPreviewLayoutDiagnostic>,
        selectionCoordinator: PreviewNodeSelectionCoordinator?,
    ): JComponent {
        if (diagnostics.isEmpty()) {
            return JBScrollPane(readOnlyText(messages.text("layout.empty"))).apply {
                border = JBUI.Borders.empty(8)
            }
        }
        val warningCount = diagnostics.count { diagnostic ->
            diagnostic.severity == StudioPreviewDiagnosticSeverity.Warning
        }
        val infoCount = diagnostics.count { diagnostic ->
            diagnostic.severity == StudioPreviewDiagnosticSeverity.Info
        }
        val root = DefaultMutableTreeNode(messages.text("tree.layoutDiagnostics"))
        diagnostics.forEach { diagnostic ->
            root.add(diagnostic.toSwingTreeNode(messages))
        }
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(
                readOnlyText(
                    messages.text(
                        "layout.summary",
                        warningCount,
                        infoCount,
                    ),
                ),
                BorderLayout.NORTH,
            )
            add(
                JBScrollPane(
                    sourceNavigableTree(root, selectionCoordinator).apply {
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

    private fun nativeViewsPanel(
        views: List<StudioPreviewNativeViewNode>,
        selectionCoordinator: PreviewNodeSelectionCoordinator?,
    ): JComponent {
        val root = DefaultMutableTreeNode(messages.text("tree.androidView"))
        views.forEach { view ->
            root.add(view.toSwingTreeNode(messages))
        }
        val tree = sourceNavigableTree(root, selectionCoordinator).apply {
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
        layoutDiagnostics: List<StudioPreviewLayoutDiagnostic>,
        selectionCoordinator: PreviewNodeSelectionCoordinator?,
    ): JComponent {
        val canvas = PreviewImageCanvas(
            image = image,
            nativeViews = nativeViews,
            layoutDiagnostics = layoutDiagnostics,
            initialZoomOption = previewZoomOption,
            initialCustomScale = previewCustomScale,
            sourceNavigationHint = messages.text("source.navigationHint"),
            onNavigateToSource = onNavigateToRuntimeSource,
            onNodeSelected = { nodeId -> selectionCoordinator?.select(nodeId) },
            onContinuousZoomChanged = { scale -> previewCustomScale = scale },
        ).apply {
            showLayoutBounds = this@ViewComposePreviewToolWindowPanel.showLayoutBounds
            showLayoutDiagnostics =
                this@ViewComposePreviewToolWindowPanel.showLayoutDiagnostics
        }
        selectionCoordinator?.register(canvas::selectNode)
        val imageScrollPane = JBScrollPane(canvas).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(JBUI.scale(360), JBUI.scale(600))
            verticalScrollBar.unitIncrement = JBUI.scale(24)
            horizontalScrollBar.unitIncrement = JBUI.scale(24)
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

        fun selectZoom(option: PreviewZoomOption) {
            previewZoomOption = option
            previewCustomScale = null
            canvas.zoomOption = option
            SwingUtilities.invokeLater(::updateCanvasScale)
        }
        val zoomToolbar = previewZoomToolbar(
            canvas = canvas,
            onZoomIn = { canvas.stepZoom(1) },
            onZoomOut = { canvas.stepZoom(-1) },
            onActualSize = { selectZoom(PreviewZoomOption.Percent100) },
            onFit = { selectZoom(PreviewZoomOption.Fit) },
        )
        val canvasLayer = PreviewCanvasLayer(
            scrollPane = imageScrollPane,
            floatingToolbar = zoomToolbar,
        )

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    border = JBUI.Borders.empty(8, 4, 4, 4)
                    if (nativeViews.isNotEmpty() || layoutDiagnostics.isNotEmpty()) {
                        add(
                            JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
                                isOpaque = false
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
                                    )
                                }
                                if (layoutDiagnostics.isNotEmpty()) {
                                    add(
                                        JCheckBox(messages.text("preview.showLayoutIssues")).apply {
                                            isOpaque = false
                                            isSelected = showLayoutDiagnostics
                                            addActionListener {
                                                canvas.showLayoutDiagnostics = isSelected
                                                showLayoutDiagnostics = isSelected
                                            }
                                        },
                                    )
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
                        },
                        BorderLayout.EAST,
                    )
                },
                BorderLayout.NORTH,
            )
            add(canvasLayer, BorderLayout.CENTER)
        }
    }

    private fun previewZoomToolbar(
        canvas: PreviewImageCanvas,
        onZoomIn: () -> Unit,
        onZoomOut: () -> Unit,
        onActualSize: () -> Unit,
        onFit: () -> Unit,
    ): JComponent {
        fun toolbarButton(
            tooltip: String,
            icon: javax.swing.Icon? = null,
            text: String? = null,
            action: () -> Unit,
        ): JButton {
            return JButton(icon).apply {
                this.text = text
                isFocusable = false
                isOpaque = false
                toolTipText = tooltip
                accessibleContext.accessibleName = tooltip
                margin = Insets(JBUI.scale(5), JBUI.scale(7), JBUI.scale(5), JBUI.scale(7))
                preferredSize = Dimension(JBUI.scale(38), JBUI.scale(34))
                addActionListener { action() }
            }
        }
        return JPanel(GridLayout(0, 1, 0, 0)).apply {
            isOpaque = true
            background = PREVIEW_TOOLBAR_BACKGROUND
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PREVIEW_TOOLBAR_BORDER),
                JBUI.Borders.empty(2),
            )
            add(
                toolbarButton(
                    tooltip = messages.text("preview.zoom.in"),
                    icon = AllIcons.General.ZoomIn,
                    action = onZoomIn,
                ),
            )
            add(
                toolbarButton(
                    tooltip = messages.text("preview.zoom.out"),
                    icon = AllIcons.General.ZoomOut,
                    action = onZoomOut,
                ),
            )
            add(
                toolbarButton(
                    tooltip = messages.text("preview.zoom.actual"),
                    text = "1:1",
                    action = onActualSize,
                ),
            )
            add(
                toolbarButton(
                    tooltip = messages.text("preview.zoom.fit"),
                    icon = AllIcons.General.FitContent,
                    action = onFit,
                ),
            )
        }
    }

    private fun patchesPanel(
        patches: List<StudioPreviewPatchRecord>,
        selectionCoordinator: PreviewNodeSelectionCoordinator?,
    ): JComponent {
        if (patches.isEmpty()) {
            return JBScrollPane(readOnlyText(messages.text("patch.empty"))).apply {
                border = JBUI.Borders.empty(8)
            }
        }
        val root = DefaultMutableTreeNode(messages.text("tree.patches"))
        patches.forEach { patch ->
            root.add(patch.toSwingTreeNode())
        }
        return JBScrollPane(
            sourceNavigableTree(root, selectionCoordinator).apply {
                isRootVisible = false
                showsRootHandles = true
            },
        ).apply {
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

    private fun sourceNavigableTree(
        root: DefaultMutableTreeNode,
        selectionCoordinator: PreviewNodeSelectionCoordinator?,
    ): JTree {
        return object : JTree(root) {
            override fun getToolTipText(event: MouseEvent): String? {
                val path = getPathForLocation(event.x, event.y)
                val node = path?.lastPathComponent as? DefaultMutableTreeNode
                val entry = node?.userObject as? PreviewTreeEntry
                return entry?.toolTip ?: messages.text("source.navigationHint")
            }
        }.apply {
            var applyingLinkedSelection = false
            toolTipText = messages.text("source.navigationHint")
            addTreeSelectionListener {
                if (!applyingLinkedSelection) {
                    selectionCoordinator?.select(selectionPath.nodeId())
                }
            }
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
            selectionCoordinator?.register { nodeId ->
                applyingLinkedSelection = true
                try {
                    val linkedPath = nodeId?.let(root::findNodePath)
                    if (linkedPath == null) {
                        clearSelection()
                    } else {
                        selectionPath = linkedPath
                        scrollPathToVisible(linkedPath)
                    }
                } finally {
                    applyingLinkedSelection = false
                }
            }
        }
    }

    private fun showFailureState(result: PreviewRenderOutcome.Failure) {
        contentPanel.border = JBUI.Borders.empty(24)
        contentPanel.add(
            header(
                title = messages.failureTitle(result.title),
                description = result.selection.symbolName,
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
        title: String?,
        description: String,
        includeDetectionEvidence: Boolean = false,
        trailing: JComponent? = null,
    ): JComponent {
        val primaryInfo = Box.createVerticalBox().apply {
            title?.let { text ->
                add(JBLabel(text).apply {
                    alignmentX = LEFT_ALIGNMENT
                    font = font.deriveFont(Font.BOLD)
                })
                add(Box.createVerticalStrut(JBUI.scale(5)))
            }
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
            nodeId = nodeId,
            sourceCallSites = sourceCallSites,
        ),
    )
    children.forEach { child ->
        swingNode.add(child.toSwingTreeNode())
    }
    return swingNode
}

private fun StudioPreviewNativeViewNode.toSwingTreeNode(
    messages: PreviewUiMessages,
): DefaultMutableTreeNode {
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
        when (clippingState) {
            StudioPreviewClippingState.PartiallyClipped -> {
                append(" · ")
                append(messages.text("layout.clipping.partial"))
            }
            StudioPreviewClippingState.FullyClipped -> {
                append(" · ")
                append(messages.text("layout.clipping.full"))
            }
            StudioPreviewClippingState.NotClipped -> Unit
        }
    }
    val swingNode = DefaultMutableTreeNode(
        PreviewTreeEntry(
            label = label,
            nodeId = nodeId,
            sourceCallSites = sourceCallSites,
            toolTip = nativeViewToolTip(this, messages),
        ),
    )
    children.forEach { child ->
        swingNode.add(child.toSwingTreeNode(messages))
    }
    return swingNode
}

private fun StudioPreviewLayoutDiagnostic.toSwingTreeNode(
    messages: PreviewUiMessages,
): DefaultMutableTreeNode {
    val simpleClassName = className.substringAfterLast('.')
    val kindLabel = messages.text(
        when (kind) {
            StudioPreviewLayoutDiagnosticKind.ZeroLayoutSize -> "layout.kind.zeroSize"
            StudioPreviewLayoutDiagnosticKind.PartiallyClipped -> "layout.kind.partialClip"
            StudioPreviewLayoutDiagnosticKind.FullyClipped -> "layout.kind.fullClip"
            StudioPreviewLayoutDiagnosticKind.TextEllipsized -> "layout.kind.textEllipsized"
            StudioPreviewLayoutDiagnosticKind.TextContentClipped -> "layout.kind.textClipped"
            StudioPreviewLayoutDiagnosticKind.Unknown -> "layout.kind.unknown"
        },
    )
    val node = DefaultMutableTreeNode(
        PreviewTreeEntry(
            label = "[${severity.name}] $kindLabel · $simpleClassName",
            nodeId = nodeId,
            sourceCallSites = sourceCallSites,
        ),
    )
    node.add(
        DefaultMutableTreeNode(
            messages.text(
                "layout.bounds",
                bounds.width,
                bounds.height,
                bounds.left,
                bounds.top,
            ),
        ),
    )
    visibleBounds?.let { visible ->
        if (visible != bounds) {
            node.add(
                DefaultMutableTreeNode(
                    messages.text(
                        "layout.visibleBounds",
                        visible.width,
                        visible.height,
                        visible.left,
                        visible.top,
                    ),
                ),
            )
        }
    }
    clippingAncestorClassName?.let { ancestor ->
        node.add(
            DefaultMutableTreeNode(
                messages.text(
                    "layout.clippedBy",
                    ancestor.substringAfterLast('.'),
                    if (clippingExpected) {
                        messages.text("layout.expectedClipSuffix")
                    } else {
                        ""
                    },
                ),
            ),
        )
    }
    if (metrics.isNotEmpty()) {
        node.add(
            DefaultMutableTreeNode(
                messages.text(
                    "layout.metrics",
                    metrics.entries.joinToString { (name, value) -> "$name=$value" },
                ),
            ),
        )
    }
    return node
}

private fun StudioPreviewPatchRecord.toSwingTreeNode(): DefaultMutableTreeNode {
    val label = buildString {
        append(operation)
        append(" · ")
        append(type)
        key?.let { key -> append(" · key=$key") }
        parentKey?.let { key -> append(" · parent=$key") }
        append(" · index=$index")
        if (moved) append(" · moved")
        detail?.let { detail -> append(" · $detail") }
    }
    return DefaultMutableTreeNode(
        PreviewTreeEntry(
            label = label,
            nodeId = nodeId,
            sourceCallSites = sourceCallSites,
        ),
    )
}

private fun StudioPreviewRecomposeScope.toSwingTreeNode(
    messages: PreviewUiMessages,
): DefaultMutableTreeNode {
    val status = when {
        recomposed -> messages.text("composition.recomposed")
        skipped -> messages.text("composition.skipped")
        else -> messages.text("composition.clean")
    }
    val node = DefaultMutableTreeNode(
        PreviewTreeEntry(
            label = "$status · $path",
            nodeId = null,
            sourceCallSites = sourceCallSites,
        ),
    )
    node.add(
        DefaultMutableTreeNode(
            "${messages.text("composition.signature")}: $signature",
        ),
    )
    if (reasons.isNotEmpty()) {
        node.add(
            DefaultMutableTreeNode(
                "${messages.text("composition.reasons")}: ${reasons.joinToString()}",
            ),
        )
    }
    locals.forEach { local ->
        node.add(DefaultMutableTreeNode("${local.name} = ${local.value}"))
    }
    return node
}

private fun StudioPreviewNativeViewNode.nodeCount(): Int {
    return 1 + children.sumOf(StudioPreviewNativeViewNode::nodeCount)
}

internal class PreviewGalleryOverlay(
    private val content: JComponent,
    private val onDismiss: () -> Unit,
) : JLayeredPane() {
    private val detailBody = JPanel(BorderLayout()).apply {
        isOpaque = false
    }
    private val detailCard = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = JBColor(Color(0xFA, 0xFA, 0xFA), Color(0x2B, 0x2D, 0x30))
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PREVIEW_TOOLBAR_BORDER),
            JBUI.Borders.empty(6),
        )
        add(
            JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                isOpaque = false
                add(
                    JButton(AllIcons.Actions.Close).apply {
                        isFocusable = false
                        isContentAreaFilled = false
                        border = JBUI.Borders.empty(4)
                        addActionListener { dismissDetail() }
                    },
                )
            },
            BorderLayout.NORTH,
        )
        add(detailBody, BorderLayout.CENTER)
    }
    private val backdrop = object : JPanel(null) {
        override fun paintComponent(graphics: Graphics) {
            super.paintComponent(graphics)
            graphics.color = Color(0, 0, 0, GALLERY_OVERLAY_ALPHA)
            graphics.fillRect(0, 0, width, height)
        }
    }.apply {
        isOpaque = false
        isVisible = false
        isFocusable = true
        add(detailCard)
        addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) {
                    if (!detailCard.bounds.contains(event.point)) {
                        dismissDetail()
                    }
                }
            },
        )
        addMouseWheelListener(MouseWheelEvent::consume)
    }

    val isDetailVisible: Boolean
        get() = backdrop.isVisible

    init {
        isOpaque = false
        add(content)
        setLayer(content, DEFAULT_LAYER)
        add(backdrop)
        setLayer(backdrop, MODAL_LAYER)
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "dismissDetail")
        actionMap.put(
            "dismissDetail",
            object : AbstractAction() {
                override fun actionPerformed(event: java.awt.event.ActionEvent?) {
                    dismissDetail()
                }
            },
        )
    }

    fun showDetail(component: JComponent) {
        detailBody.removeAll()
        detailBody.add(component, BorderLayout.CENTER)
        backdrop.isVisible = true
        revalidate()
        repaint()
        SwingUtilities.invokeLater { backdrop.requestFocusInWindow() }
    }

    private fun dismissDetail() {
        if (!backdrop.isVisible) return
        backdrop.isVisible = false
        detailBody.removeAll()
        onDismiss()
        content.requestFocusInWindow()
        revalidate()
        repaint()
    }

    override fun doLayout() {
        content.setBounds(0, 0, width, height)
        backdrop.setBounds(0, 0, width, height)
        val margin = JBUI.scale(GALLERY_OVERLAY_MARGIN)
        val cardWidth = (width - margin * 2)
            .coerceAtMost(JBUI.scale(GALLERY_OVERLAY_MAX_WIDTH))
            .coerceAtLeast(1)
        val cardHeight = (height - margin * 2)
            .coerceAtMost(JBUI.scale(GALLERY_OVERLAY_MAX_HEIGHT))
            .coerceAtLeast(1)
        detailCard.setBounds(
            ((width - cardWidth) / 2).coerceAtLeast(0),
            ((height - cardHeight) / 2).coerceAtLeast(0),
            cardWidth,
            cardHeight,
        )
        detailCard.doLayout()
    }

    override fun getPreferredSize(): Dimension = content.preferredSize

    override fun isOptimizedDrawingEnabled(): Boolean = false
}

internal class PreviewCanvasLayer(
    private val scrollPane: JComponent,
    private val floatingToolbar: JComponent,
) : JLayeredPane() {
    init {
        isOpaque = false
        add(scrollPane)
        setLayer(scrollPane, DEFAULT_LAYER)
        add(floatingToolbar)
        setLayer(floatingToolbar, DRAG_LAYER)
        moveToFront(floatingToolbar)
    }

    override fun doLayout() {
        scrollPane.setBounds(0, 0, width, height)
        val toolbarSize = floatingToolbar.preferredSize
        val margin = JBUI.scale(12)
        floatingToolbar.setBounds(
            (width - toolbarSize.width - margin).coerceAtLeast(0),
            (height - toolbarSize.height - margin).coerceAtLeast(0),
            toolbarSize.width,
            toolbarSize.height,
        )
        floatingToolbar.doLayout()
    }

    override fun getPreferredSize(): Dimension = scrollPane.preferredSize

    override fun isOptimizedDrawingEnabled(): Boolean = false
}

private class PreviewImageCanvas(
    private val image: BufferedImage,
    private val nativeViews: List<StudioPreviewNativeViewNode>,
    private val layoutDiagnostics: List<StudioPreviewLayoutDiagnostic>,
    initialZoomOption: PreviewZoomOption,
    initialCustomScale: Double?,
    private val sourceNavigationHint: String,
    private val onNavigateToSource: (List<StudioPreviewSourceCallSite>) -> Unit,
    private val onNodeSelected: (String?) -> Unit,
    private val onContinuousZoomChanged: (Double) -> Unit,
    private val onBackgroundDoubleClick: (() -> Unit)? = null,
) : JComponent() {
    var zoomOption: PreviewZoomOption = initialZoomOption
        set(value) {
            if (field == value && customScale == null) return
            field = value
            customScale = null
            updateScale()
        }

    var showLayoutBounds: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    var showLayoutDiagnostics: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    private var viewportSize: Dimension = Dimension(image.width, image.height)
    private var scale: Double = 1.0
    private var customScale: Double? = initialCustomScale?.let(::clampPreviewScale)
    private var selectedView: StudioPreviewNativeViewNode? = null
    private var nativeMagnificationRegistration: AutoCloseable? = null
    private val trackpadAxisLock = PreviewTrackpadAxisLock()
    private val doublePressTracker = PreviewDoublePressTracker()

    init {
        minimumSize = Dimension(1, 1)
        isOpaque = true
        isFocusable = true
        ToolTipManager.sharedInstance().registerComponent(this)
        addMouseWheelListener { event ->
            if (event.isControlDown) {
                trackpadAxisLock.reset()
                event.consume()
                applyContinuousScale(
                    nextScale = calculateWheelPreviewScale(
                        currentScale = scale,
                        preciseWheelRotation = event.preciseWheelRotation,
                    ),
                    anchorPoint = event.point,
                )
            } else {
                applyTrackpadScroll(event)
            }
        }
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

                override fun mousePressed(event: MouseEvent) {
                    requestFocusInWindow()
                    val isPrimaryButton = SwingUtilities.isLeftMouseButton(event)
                    val isDoublePress = isPrimaryButton && doublePressTracker.register(
                        awtClickCount = event.clickCount,
                        eventMillis = event.`when`,
                        x = event.x,
                        y = event.y,
                    )
                    if (isDoublePress) {
                        event.consume()
                        navigateAt(event.point)
                        return
                    }
                }

                override fun mouseClicked(event: MouseEvent) {
                    if (event.clickCount != 1) return
                    val mappedView = mappedViewAt(event.x, event.y)
                    onNodeSelected(mappedView?.nodeId)
                }
            },
        )
        updateScale()
    }

    override fun addNotify() {
        super.addNotify()
        if (nativeMagnificationRegistration == null) {
            nativeMagnificationRegistration = installNativePreviewMagnificationListener(this) {
                magnification ->
                val zoom = {
                    applyContinuousScale(
                        nextScale = calculateMagnifiedPreviewScale(
                            currentScale = scale,
                            magnification = magnification,
                        ),
                        anchorPoint = null,
                    )
                }
                if (SwingUtilities.isEventDispatchThread()) {
                    zoom()
                } else {
                    SwingUtilities.invokeLater(zoom)
                }
            }
        }
    }

    override fun removeNotify() {
        nativeMagnificationRegistration?.close()
        nativeMagnificationRegistration = null
        super.removeNotify()
    }

    private fun previewViewport(): JViewport? {
        return SwingUtilities.getAncestorOfClass(JViewport::class.java, this) as? JViewport
    }

    private fun applyTrackpadScroll(event: MouseWheelEvent) {
        val viewport = previewViewport() ?: return
        val oldPosition = viewport.viewPosition
        val maximumX = (viewport.viewSize.width - viewport.extentSize.width).coerceAtLeast(0)
        val maximumY = (viewport.viewSize.height - viewport.extentSize.height).coerceAtLeast(0)
        if (maximumX == 0 && maximumY == 0) return
        event.consume()
        val horizontalRotation = if (event.isShiftDown) event.preciseWheelRotation else 0.0
        val verticalRotation = if (event.isShiftDown) 0.0 else event.preciseWheelRotation
        val axis = trackpadAxisLock.resolve(
            horizontalRotation = horizontalRotation,
            verticalRotation = verticalRotation,
            eventMillis = event.`when`,
        ) ?: return
        val nextPosition = if (axis == PreviewScrollAxis.Horizontal) {
            if (horizontalRotation == 0.0) return
            Point(
                calculatePreviewScrollPosition(
                    currentPosition = oldPosition.x,
                    maximumPosition = maximumX,
                    preciseWheelRotation = horizontalRotation,
                ),
                oldPosition.y,
            )
        } else {
            if (verticalRotation == 0.0) return
            Point(
                oldPosition.x,
                calculatePreviewScrollPosition(
                    currentPosition = oldPosition.y,
                    maximumPosition = maximumY,
                    preciseWheelRotation = verticalRotation,
                ),
            )
        }
        if (nextPosition != oldPosition) {
            viewport.viewPosition = nextPosition
        }
    }

    private fun navigateAt(point: Point) {
        val sourceCallSites = mappedViewAt(point.x, point.y)
            ?.sourceCallSites
            ?.takeIf(List<StudioPreviewSourceCallSite>::isNotEmpty)
        if (sourceCallSites != null) {
            onNavigateToSource(sourceCallSites)
        } else {
            onBackgroundDoubleClick?.invoke()
        }
    }

    fun selectNode(nodeId: String?) {
        val next = nodeId?.let { id -> findNativeViewByNodeId(nativeViews, id) }
        if (next == selectedView) return
        selectedView = next
        repaint()
    }

    fun updateViewportSize(size: Dimension) {
        if (size.width <= 0 || size.height <= 0 || size == viewportSize) return
        viewportSize = Dimension(size)
        updateScale()
    }

    fun stepZoom(direction: Int) {
        applyContinuousScale(
            nextScale = calculateButtonPreviewScale(scale, direction),
            anchorPoint = null,
        )
    }

    private fun updateScale() {
        scale = customScale ?: calculatePreviewScale(
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

    private fun applyContinuousScale(
        nextScale: Double,
        anchorPoint: Point?,
    ) {
        val resolvedScale = clampPreviewScale(nextScale)
        if (kotlin.math.abs(resolvedScale - scale) < ZOOM_EPSILON) return
        val viewport = previewViewport()
        val oldPlacement = imagePlacement()
        val oldScale = scale
        val oldViewPosition = viewport?.viewPosition ?: Point()
        val oldExtent = viewport?.extentSize ?: viewportSize
        val anchorCanvas = anchorPoint ?: Point(
            oldViewPosition.x + oldExtent.width / 2,
            oldViewPosition.y + oldExtent.height / 2,
        )
        val anchorViewportOffset = Point(
            anchorCanvas.x - oldViewPosition.x,
            anchorCanvas.y - oldViewPosition.y,
        )
        val imageAnchorX = (anchorCanvas.x - oldPlacement.left) / oldScale
        val imageAnchorY = (anchorCanvas.y - oldPlacement.top) / oldScale

        customScale = resolvedScale
        onContinuousZoomChanged(resolvedScale)
        updateScale()
        if (viewport != null) {
            SwingUtilities.invokeLater {
                val placement = imagePlacement()
                val maxX = (viewport.viewSize.width - viewport.extentSize.width).coerceAtLeast(0)
                val maxY = (viewport.viewSize.height - viewport.extentSize.height).coerceAtLeast(0)
                viewport.viewPosition = Point(
                    (placement.left + imageAnchorX * scale - anchorViewportOffset.x)
                        .roundToInt()
                        .coerceIn(0, maxX),
                    (placement.top + imageAnchorY * scale - anchorViewportOffset.y)
                        .roundToInt()
                        .coerceIn(0, maxY),
                )
            }
        }
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val view = mappedViewAt(event.x, event.y) ?: return null
        return nativeViewToolTip(
            view = view,
            messages = null,
            sourceNavigationHint = sourceNavigationHint,
        )
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
                if (scale < 1.0) {
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
                } else {
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
                },
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
            if (showLayoutDiagnostics) {
                layoutDiagnostics.forEach { diagnostic ->
                    graphics2D.paintLayoutDiagnostic(
                        diagnostic = diagnostic,
                        imageLeft = placement.left,
                        imageTop = placement.top,
                        scale = scale,
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

private fun Graphics2D.paintLayoutDiagnostic(
    diagnostic: StudioPreviewLayoutDiagnostic,
    imageLeft: Int,
    imageTop: Int,
    scale: Double,
) {
    val bounds = diagnostic.bounds
    if (bounds.width <= 0 || bounds.height <= 0) return
    val scaledLeft = imageLeft + (bounds.left * scale).roundToInt()
    val scaledTop = imageTop + (bounds.top * scale).roundToInt()
    val scaledWidth = (bounds.width * scale).roundToInt().coerceAtLeast(1)
    val scaledHeight = (bounds.height * scale).roundToInt().coerceAtLeast(1)
    val issueColor = when (diagnostic.severity) {
        StudioPreviewDiagnosticSeverity.Error -> LAYOUT_DIAGNOSTIC_ERROR_COLOR
        StudioPreviewDiagnosticSeverity.Warning -> LAYOUT_DIAGNOSTIC_WARNING_COLOR
        StudioPreviewDiagnosticSeverity.Info -> LAYOUT_DIAGNOSTIC_INFO_COLOR
    }
    color = Color(issueColor.red, issueColor.green, issueColor.blue, 22)
    fillRect(scaledLeft, scaledTop, scaledWidth, scaledHeight)
    color = Color(issueColor.red, issueColor.green, issueColor.blue, 220)
    stroke = BasicStroke(
        JBUI.scale(2).toFloat(),
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER,
        10f,
        floatArrayOf(JBUI.scale(5).toFloat(), JBUI.scale(3).toFloat()),
        0f,
    )
    drawRect(
        scaledLeft,
        scaledTop,
        (scaledWidth - 1).coerceAtLeast(0),
        (scaledHeight - 1).coerceAtLeast(0),
    )
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

internal fun nativeViewToolTip(
    view: StudioPreviewNativeViewNode,
    messages: PreviewUiMessages?,
    sourceNavigationHint: String? = null,
): String = buildString {
    append("<html>")
    sourceNavigationHint?.let { hint ->
        append(hint.escapeHtml())
        append("<br><br>")
    }
    append("<b>")
    append(view.className.substringAfterLast('.').escapeHtml())
    append("</b><br>")
    append((messages?.text("view.tooltip.bounds") ?: "Bounds").escapeHtml())
    append(": ")
    append("${view.bounds.width} × ${view.bounds.height} @ ${view.bounds.left}, ${view.bounds.top}")
    append("<br>")
    append((messages?.text("view.tooltip.measured") ?: "Measured").escapeHtml())
    append(": ${view.measuredWidth} × ${view.measuredHeight}<br>")
    append((messages?.text("view.tooltip.visibility") ?: "Visibility").escapeHtml())
    append(": ")
    append(view.visibility.escapeHtml())
    if (view.properties.isNotEmpty()) {
        append("<br><br><b>")
        append((messages?.text("view.tooltip.properties") ?: "Properties").escapeHtml())
        append("</b>")
        view.properties.forEach { (name, value) ->
            append("<br>")
            append(name.escapeHtml())
            append(" = ")
            append(value.escapeHtml())
        }
    }
    append("</html>")
}

private fun String.escapeHtml(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

private val SOURCE_SELECTION_COLOR = JBColor(Color(0x2F, 0x80, 0xED), Color(0x64, 0xB5, 0xF6))
private val LAYOUT_DIAGNOSTIC_ERROR_COLOR = Color(0xC6, 0x28, 0x28)
private val LAYOUT_DIAGNOSTIC_WARNING_COLOR = Color(0xEF, 0x6C, 0x00)
private val LAYOUT_DIAGNOSTIC_INFO_COLOR = Color(0xF9, 0xA8, 0x25)
private val PREVIEW_TOOLBAR_BACKGROUND = JBColor(Color(0xF5, 0xF5, 0xF5), Color(0x2B, 0x2D, 0x30))
private val PREVIEW_TOOLBAR_BORDER = JBColor(Color(0xC9, 0xC9, 0xC9), Color(0x4A, 0x4D, 0x52))

private const val SOURCE_NAVIGATION_ACTION = "viewcompose.preview.navigateToRuntimeSource"
private const val ZOOM_EPSILON = 0.001

private data class PreviewTreeEntry(
    val label: String,
    val nodeId: String?,
    val sourceCallSites: List<StudioPreviewSourceCallSite>,
    val toolTip: String? = null,
) {
    override fun toString(): String = label
}

private fun javax.swing.tree.TreePath.sourceCallSites(): List<StudioPreviewSourceCallSite>? {
    val node = lastPathComponent as? DefaultMutableTreeNode ?: return null
    return (node.userObject as? PreviewTreeEntry)
        ?.sourceCallSites
        ?.takeIf(List<StudioPreviewSourceCallSite>::isNotEmpty)
}

private fun javax.swing.tree.TreePath?.nodeId(): String? {
    val node = this?.lastPathComponent as? DefaultMutableTreeNode ?: return null
    return (node.userObject as? PreviewTreeEntry)?.nodeId
}

private fun DefaultMutableTreeNode.findNodePath(nodeId: String): javax.swing.tree.TreePath? {
    val nodes = depthFirstEnumeration()
    while (nodes.hasMoreElements()) {
        val node = nodes.nextElement() as? DefaultMutableTreeNode ?: continue
        val entry = node.userObject as? PreviewTreeEntry ?: continue
        if (entry.nodeId == nodeId) {
            return javax.swing.tree.TreePath(node.path)
        }
    }
    return null
}

private data class PreviewCaretLocation(
    val filePath: String,
    val lineCandidates: List<Int>,
)

private data class PreviewImagePlacement(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

private class RetinaPreviewIcon(
    private val image: BufferedImage,
    private val width: Int,
    private val height: Int,
) : Icon {
    override fun getIconWidth(): Int = width

    override fun getIconHeight(): Int = height

    override fun paintIcon(
        component: java.awt.Component?,
        graphics: Graphics,
        x: Int,
        y: Int,
    ) {
        val scale = minOf(
            width.toDouble() / image.width,
            height.toDouble() / image.height,
        )
        val targetWidth = (image.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (image.height * scale).roundToInt().coerceAtLeast(1)
        val left = x + (width - targetWidth) / 2
        val top = y + (height - targetHeight) / 2
        val graphics2D = graphics.create() as Graphics2D
        try {
            graphics2D.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC,
            )
            graphics2D.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY,
            )
            graphics2D.drawImage(image, left, top, targetWidth, targetHeight, null)
        } finally {
            graphics2D.dispose()
        }
    }
}

private data class PreviewVariantChoice(
    val id: String,
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

internal data class PreviewPanelPresentation(
    val title: String?,
    val source: PreviewSourceSelection?,
)

internal fun ViewComposePreviewPanelState.previewPresentation(): PreviewPanelPresentation {
    return when (this) {
        ViewComposePreviewPanelState.Empty -> PreviewPanelPresentation(
            title = null,
            source = null,
        )
        is ViewComposePreviewPanelState.Loading -> PreviewPanelPresentation(
            title = previousResult?.descriptorName ?: selection.symbolName,
            source = selection,
        )
        is ViewComposePreviewPanelState.Rendered -> PreviewPanelPresentation(
            title = result.descriptorName,
            source = result.selection,
        )
        is ViewComposePreviewPanelState.Failed -> PreviewPanelPresentation(
            title = result.selection.symbolName,
            source = result.selection,
        )
        is ViewComposePreviewPanelState.GalleryLoading,
        is ViewComposePreviewPanelState.Gallery,
        is ViewComposePreviewPanelState.GalleryFailed,
        -> PreviewPanelPresentation(
            title = null,
            source = null,
        )
    }
}

private const val GALLERY_CELL_WIDTH = 260
private const val GALLERY_CELL_HEIGHT = 390
private const val GALLERY_IMAGE_WIDTH = 220
private const val GALLERY_IMAGE_HEIGHT = 300
private const val GALLERY_DETAIL_MEMORY_ENTRIES = 3
private const val GALLERY_DETAIL_OPEN_DELAY_MILLIS = 280
private const val GALLERY_OVERLAY_MARGIN = 24
private const val GALLERY_OVERLAY_MAX_WIDTH = 900
private const val GALLERY_OVERLAY_MAX_HEIGHT = 1000
private const val GALLERY_OVERLAY_ALPHA = 150
