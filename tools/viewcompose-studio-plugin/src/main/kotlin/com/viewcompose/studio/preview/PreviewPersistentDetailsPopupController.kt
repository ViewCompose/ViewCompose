package com.viewcompose.studio.preview

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.HierarchyEvent
import javax.swing.JComponent
import javax.swing.JPanel

/** A Studio-themed selection detail popup that remains visible until explicitly replaced. */
internal class PreviewPersistentDetailsPopupController(
    private val owner: JComponent,
) : AutoCloseable {
    private var popup: JBPopup? = null

    init {
        owner.addHierarchyListener { event ->
            if (
                event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L &&
                !owner.isShowing
            ) {
                hide()
            }
        }
    }

    fun show(
        text: String,
        anchor: Point,
    ) {
        hide()
        if (text.isBlank() || !owner.isShowing) return
        val content = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = JBUI.Borders.empty(10, 12)
            add(JBLabel(text), BorderLayout.CENTER)
        }
        val nextPopup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(content, null)
            .setRequestFocus(false)
            .setFocusable(false)
            .setCancelOnClickOutside(false)
            .setCancelOnOtherWindowOpen(false)
            .setCancelOnWindowDeactivation(true)
            .setCancelKeyEnabled(true)
            .setLocateWithinScreenBounds(true)
            .setShowBorder(true)
            .setShowShadow(true)
            .createPopup()
        val ownerLocation = runCatching(owner::getLocationOnScreen).getOrNull() ?: return
        val offset = JBUI.scale(PREVIEW_DETAILS_POPUP_OFFSET)
        popup = nextPopup
        nextPopup.showInScreenCoordinates(
            owner,
            Point(
                ownerLocation.x + anchor.x + offset,
                ownerLocation.y + anchor.y + offset,
            ),
        )
    }

    fun hide() {
        popup?.cancel()
        popup = null
    }

    override fun close() {
        hide()
    }
}

private const val PREVIEW_DETAILS_POPUP_OFFSET = 10
