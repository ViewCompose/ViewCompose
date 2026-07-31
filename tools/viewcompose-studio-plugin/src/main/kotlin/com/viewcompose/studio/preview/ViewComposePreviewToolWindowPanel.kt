package com.viewcompose.studio.preview

import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Box
import javax.swing.ImageIcon
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.tree.DefaultMutableTreeNode

internal class ViewComposePreviewToolWindowPanel(
    detection: ViewComposeProjectDetection,
    private val onVariantSelected: (String) -> Unit,
    private val onNavigateToSource: (StudioPreviewSourceLocation) -> Unit,
) : SimpleToolWindowPanel(true, true) {
    private val contentPanel = JPanel(BorderLayout())
    private val detectionEvidence = detection.evidencePath

    val preferredFocusComponent: JComponent
        get() = contentPanel

    init {
        setContent(contentPanel)
        showState(ViewComposePreviewPanelState.Empty)
    }

    fun showState(state: ViewComposePreviewPanelState) {
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

    private fun showEmptyState() {
        contentPanel.border = JBUI.Borders.empty(24)
        contentPanel.add(
            header(
                title = "No preview selected",
                description =
                    "Use a ViewCompose preview gutter action to render a static Android View.",
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
                description = state.message,
                selection = state.selection,
            ),
            BorderLayout.NORTH,
        )
    }

    private fun showRenderedState(result: PreviewRenderOutcome.Success) {
        contentPanel.border = JBUI.Borders.empty(12)
        val duration = result.durationMillis?.let { millis -> " · ${millis} ms" }.orEmpty()
        val cache = if (result.cacheHit) " · cache" else ""
        val renderedHeader = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(
                header(
                    title = result.descriptorName,
                    description = "${result.variantName}$duration$cache",
                    selection = result.selection,
                ),
                BorderLayout.CENTER,
            )
            if (result.variants.size > 1) {
                add(variantSelector(result), BorderLayout.EAST)
            }
        }
        contentPanel.add(
            renderedHeader,
            BorderLayout.NORTH,
        )
        val imageLabel = JLabel(ImageIcon(result.image)).apply {
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.TOP
            border = JBUI.Borders.emptyTop(12)
        }
        val previewScrollPane = JBScrollPane(imageLabel).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(JBUI.scale(360), JBUI.scale(600))
        }
        val snapshot = result.renderSnapshot
        val renderedContent = if (snapshot == null) {
            previewScrollPane
        } else {
            JTabbedPane().apply {
                border = JBUI.Borders.emptyTop(8)
                addTab("Preview", previewScrollPane)
                addTab("Structure", renderStructurePanel(snapshot))
                addTab("Composition", compositionPanel(snapshot.composition))
                addTab("Patches", patchesPanel(snapshot.patches))
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
        return Box.createVerticalBox().apply {
            border = JBUI.Borders.emptyLeft(16)
            add(
                JBLabel("Configuration").apply {
                    alignmentX = RIGHT_ALIGNMENT
                },
            )
            add(Box.createVerticalStrut(JBUI.scale(6)))
            add(
                JComboBox(choices.toTypedArray()).apply {
                    alignmentX = RIGHT_ALIGNMENT
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
            )
        }
    }

    private fun renderStructurePanel(snapshot: StudioPreviewRenderSnapshot): JComponent {
        val structure = snapshot.structure
        val stats = snapshot.stats
        val summary = """
            VNodes: ${structure.vnodeCount} · Mounted: ${structure.mountedNodeCount}
            Depth: ${structure.maxVNodeDepth} · Mounted depth: ${structure.maxMountedDepth}
            Insert: ${stats.inserts} · Reuse: ${stats.reuses} · Remove: ${stats.removals}
            Rebound: ${stats.reboundNodes} · Patched: ${stats.patchedNodes}
            Skipped bindings: ${stats.skippedBindings} · Skipped subtrees: ${stats.skippedSubtrees}
        """.trimIndent()
        val root = DefaultMutableTreeNode("VNode tree")
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
                "Invalidated: ${snapshot.invalidatedScopeCount} · " +
                    "Recomposed: ${snapshot.recomposedScopeCount} · " +
                    "Skipped: ${snapshot.skippedScopeCount}",
            )
            snapshot.scopes.forEach { scope ->
                appendLine()
                append(if (scope.recomposed) "RECOMPOSED" else if (scope.skipped) "SKIPPED" else "CLEAN")
                append(" · ${scope.path}")
                appendLine()
                append("  signature: ${scope.signature}")
                if (scope.reasons.isNotEmpty()) {
                    appendLine()
                    append("  reasons: ${scope.reasons.joinToString()}")
                }
                scope.locals.forEach { local ->
                    appendLine()
                    append("  ${local.name} = ${local.value}")
                }
                appendLine()
            }
        }.trim()
        return JBScrollPane(
            readOnlyText(text.ifBlank { "No composition scopes were recorded." }),
        ).apply {
            border = JBUI.Borders.empty(8)
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
        }.ifBlank { "No patch operations were recorded." }
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
                title = result.title,
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
        val textArea = JBTextArea(diagnostics.ifBlank { "No diagnostic details were produced." }).apply {
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
    ): JComponent {
        return Box.createVerticalBox().apply {
            add(
                JBLabel("<html><b>${title.toPresentableText()}</b></html>").apply {
                    alignmentX = LEFT_ALIGNMENT
                },
            )
            add(Box.createVerticalStrut(JBUI.scale(8)))
            add(
                JBLabel("<html>${description.toPresentableText()}</html>").apply {
                    alignmentX = LEFT_ALIGNMENT
                },
            )
            selection?.let { source ->
                add(Box.createVerticalStrut(JBUI.scale(8)))
                add(
                    JBLabel(
                        "<html>${source.filePath.toPresentableText()}: ${source.line}</html>",
                    ).apply {
                        alignmentX = LEFT_ALIGNMENT
                    },
                )
            }
            if (includeDetectionEvidence) {
                detectionEvidence?.let { evidencePath ->
                    add(Box.createVerticalStrut(JBUI.scale(16)))
                    add(
                        JBLabel(
                            "<html>Project detected from:<br>" +
                                "${evidencePath.toPresentablePath()}</html>",
                        ).apply {
                            alignmentX = LEFT_ALIGNMENT
                        },
                    )
                }
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
                    JBLabel(
                        "<html>" +
                            "[${diagnostic.severity}] ${diagnostic.message}".toPresentableText() +
                            "</html>",
                    ).apply {
                        alignmentX = LEFT_ALIGNMENT
                    },
                )
                diagnostic.sourceLocation?.let { source ->
                    add(
                        ActionLink(source.presentableLinkText()) {
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
                    ActionLink(source.presentableLinkText()) {
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

private data class PreviewVariantChoice(
    val id: String,
    val displayName: String,
) {
    override fun toString(): String = displayName
}

private fun java.nio.file.Path.toPresentablePath(): String {
    return toString().toPresentableText()
}

private fun StudioPreviewSourceLocation.presentableLinkText(): String {
    val fileName = runCatching { java.nio.file.Path.of(filePath).fileName.toString() }
        .getOrDefault(filePath)
    return "Open $fileName:$line"
}

private fun String.toPresentableText(): String {
    return this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
