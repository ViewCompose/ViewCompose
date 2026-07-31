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
    private val emptyState = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(24)
        val labels = Box.createVerticalBox()
        labels.add(
            JBLabel("<html><b>No preview selected</b></html>").apply {
                alignmentX = LEFT_ALIGNMENT
            },
        )
        labels.add(Box.createVerticalStrut(JBUI.scale(8)))
        labels.add(
            JBLabel(
                "<html>Use a ViewCompose preview gutter action to render a static Android View.</html>",
            ).apply {
                alignmentX = LEFT_ALIGNMENT
            },
        )
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
        get() = emptyState

    init {
        setContent(emptyState)
    }
}

private fun java.nio.file.Path.toPresentablePath(): String {
    return toString()
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
