package com.viewcompose.studio.preview

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewCanvasLayerTest {
    @Test
    fun `floating toolbar stays above canvas and receives button clicks`() {
        val clicked = AtomicBoolean(false)
        val button = JButton("+").apply {
            preferredSize = Dimension(38, 34)
            addActionListener { clicked.set(true) }
        }
        val toolbar = JPanel(GridLayout(1, 1)).apply {
            add(button)
        }
        val layer = PreviewCanvasLayer(
            scrollPane = JScrollPane(JPanel(BorderLayout())),
            floatingToolbar = toolbar,
        )
        layer.setSize(400, 500)
        layer.doLayout()

        val buttonCenter = SwingUtilities.convertPoint(
            button,
            button.width / 2,
            button.height / 2,
            layer,
        )
        assertSame(
            button,
            SwingUtilities.getDeepestComponentAt(layer, buttonCenter.x, buttonCenter.y),
        )

        button.doClick()

        assertTrue(clicked.get())
    }
}
