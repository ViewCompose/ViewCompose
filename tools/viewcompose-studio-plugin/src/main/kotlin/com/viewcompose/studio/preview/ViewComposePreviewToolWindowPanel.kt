package com.viewcompose.studio.preview

import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JPanel

internal class ViewComposePreviewToolWindowPanel(
    detection: ViewComposeProjectDetection,
) : SimpleToolWindowPanel(true, true) {
    private val titleLabel = JBLabel()
    private val descriptionLabel = JBLabel()
    private val sourceLabel = JBLabel()
    private val contentPanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(24)
        val labels = Box.createVerticalBox()
        titleLabel.alignmentX = LEFT_ALIGNMENT
        labels.add(titleLabel)
        labels.add(Box.createVerticalStrut(JBUI.scale(8)))
        descriptionLabel.alignmentX = LEFT_ALIGNMENT
        labels.add(descriptionLabel)
        labels.add(Box.createVerticalStrut(JBUI.scale(12)))
        sourceLabel.alignmentX = LEFT_ALIGNMENT
        labels.add(sourceLabel)
        detection.evidencePath?.let { evidencePath ->
            labels.add(Box.createVerticalStrut(JBUI.scale(16)))
            labels.add(
                JBLabel(
                    "<html>Project detected from:<br>${evidencePath.toPresentablePath()}</html>",
                ).apply {
                    alignmentX = LEFT_ALIGNMENT
                },
            )
        }
        add(labels, BorderLayout.NORTH)
    }

    val preferredFocusComponent: JComponent
        get() = contentPanel

    init {
        setContent(contentPanel)
        showSelection(null)
    }

    fun showSelection(selection: PreviewSourceSelection?) {
        if (selection == null) {
            titleLabel.text = "<html><b>No preview selected</b></html>"
            descriptionLabel.text =
                "<html>Use a ViewCompose preview gutter action to select a static Android View preview.</html>"
            sourceLabel.isVisible = false
            sourceLabel.text = ""
            return
        }

        titleLabel.text = "<html><b>${selection.symbolName.toPresentableText()}</b></html>"
        descriptionLabel.text =
            "<html>Preview selected. Rendering controls are added in the next integration slice.</html>"
        sourceLabel.text =
            "<html>${selection.filePath.toPresentableText()}: ${selection.line}</html>"
        sourceLabel.isVisible = true
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
