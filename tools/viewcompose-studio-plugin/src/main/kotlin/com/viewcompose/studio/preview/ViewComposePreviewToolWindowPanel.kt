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
import javax.swing.SwingConstants

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
        contentPanel.add(
            JBScrollPane(imageLabel).apply {
                border = JBUI.Borders.empty()
                preferredSize = Dimension(JBUI.scale(360), JBUI.scale(600))
            },
            BorderLayout.CENTER,
        )
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
