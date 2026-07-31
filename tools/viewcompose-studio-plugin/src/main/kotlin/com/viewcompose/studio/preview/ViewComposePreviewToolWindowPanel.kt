package com.viewcompose.studio.preview

import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.swing.Box
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

internal class ViewComposePreviewToolWindowPanel(
    detection: ViewComposeProjectDetection,
    projectRoot: Path?,
    initialLanguage: PreviewUiLanguage,
    private val onVariantSelected: (String) -> Unit,
    private val onNavigateToSource: (StudioPreviewSourceLocation) -> Unit,
) : SimpleToolWindowPanel(true, true) {
    private val contentPanel = JPanel(BorderLayout())
    private val detectionEvidence = detection.evidencePath
    private val projectRoot = projectRoot?.toAbsolutePath()?.normalize()
    private var language = initialLanguage
    private var currentState: ViewComposePreviewPanelState = ViewComposePreviewPanelState.Empty
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
        contentPanel.border = JBUI.Borders.empty(24)
        contentPanel.add(
            header(
                title = state.selection.symbolName,
                description = messages.loadingMessage(state.message),
                selection = state.selection,
            ),
            BorderLayout.NORTH,
        )
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
            }
        }
        contentPanel.add(renderedContent, BorderLayout.CENTER)
        if (result.diagnostics.isNotEmpty()) {
            contentPanel.add(
                diagnosticsPanel(result.diagnostics),
                BorderLayout.SOUTH,
            )
        }
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
                    JTree(root).apply {
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
        val tree = JTree(root).apply {
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
        )
        val imageScrollPane = JBScrollPane(canvas).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(JBUI.scale(360), JBUI.scale(600))
        }
        if (nativeViews.isEmpty()) return imageScrollPane

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    border = JBUI.Borders.empty(8, 4, 4, 4)
                    add(
                        JCheckBox(messages.text("preview.showLayoutBounds")).apply {
                            isOpaque = false
                            addActionListener {
                                canvas.showLayoutBounds = isSelected
                            }
                        },
                        BorderLayout.WEST,
                    )
                    add(
                        JBLabel(
                            messages.text(
                                "preview.viewCount",
                                nativeViews.sumOf(StudioPreviewNativeViewNode::nodeCount),
                            ),
                        ),
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
    val swingNode = DefaultMutableTreeNode(label)
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
    val swingNode = DefaultMutableTreeNode(label)
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
) : JComponent() {
    var showLayoutBounds: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    init {
        preferredSize = Dimension(image.width, image.height)
        minimumSize = preferredSize
        isOpaque = true
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val imageLeft = ((width - image.width) / 2).coerceAtLeast(0)
        graphics.drawImage(image, imageLeft, 0, null)
        if (!showLayoutBounds) return

        val graphics2D = graphics.create() as Graphics2D
        try {
            graphics2D.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON,
            )
            graphics2D.stroke = BasicStroke(JBUI.scale(1).toFloat())
            nativeViews.forEach { view ->
                graphics2D.paintViewBounds(
                    view = view,
                    imageLeft = imageLeft,
                    depth = 0,
                )
            }
        } finally {
            graphics2D.dispose()
        }
    }
}

private fun Graphics2D.paintViewBounds(
    view: StudioPreviewNativeViewNode,
    imageLeft: Int,
    depth: Int,
) {
    val bounds = view.bounds
    if (depth > 0 && bounds.width > 0 && bounds.height > 0) {
        val baseColor = LAYOUT_BOUND_COLORS[(depth - 1) % LAYOUT_BOUND_COLORS.size]
        color = Color(baseColor.red, baseColor.green, baseColor.blue, 22)
        fillRect(
            imageLeft + bounds.left,
            bounds.top,
            bounds.width,
            bounds.height,
        )
        color = Color(baseColor.red, baseColor.green, baseColor.blue, 190)
        drawRect(
            imageLeft + bounds.left,
            bounds.top,
            (bounds.width - 1).coerceAtLeast(0),
            (bounds.height - 1).coerceAtLeast(0),
        )
    }
    view.children.forEach { child ->
        paintViewBounds(
            view = child,
            imageLeft = imageLeft,
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
