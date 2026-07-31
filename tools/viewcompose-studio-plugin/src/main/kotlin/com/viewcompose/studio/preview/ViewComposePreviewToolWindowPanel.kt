package com.viewcompose.studio.preview

import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Box
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class ViewComposePreviewToolWindowPanel(
    detection: ViewComposeProjectDetection,
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
        contentPanel.add(
            header(
                title = result.descriptorName,
                description = "${result.variantName}$duration$cache",
                selection = result.selection,
            ),
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
            JBScrollPane(textArea).apply {
                border = JBUI.Borders.empty()
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
        val message = diagnostics.joinToString("\n") { diagnostic ->
            "[${diagnostic.severity}] ${diagnostic.message}"
        }
        return JBLabel("<html>${message.toPresentableText().replace("\n", "<br>")}</html>").apply {
            border = JBUI.Borders.emptyTop(8)
        }
    }
}

private fun java.nio.file.Path.toPresentablePath(): String {
    return toString().toPresentableText()
}

private fun String.toPresentableText(): String {
    return this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
