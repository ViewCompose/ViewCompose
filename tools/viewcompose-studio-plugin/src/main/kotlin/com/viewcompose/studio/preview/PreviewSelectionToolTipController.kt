package com.viewcompose.studio.preview

import com.intellij.util.ui.JBUI
import java.awt.Point
import java.awt.event.HierarchyEvent
import javax.swing.JComponent
import javax.swing.Popup
import javax.swing.PopupFactory
import javax.swing.Timer

/** Displays an immediate, selection-owned tooltip without relying on hover timing. */
internal class PreviewSelectionToolTipController(
    private val owner: JComponent,
    dismissDelayMillis: Int = PREVIEW_SELECTION_TOOLTIP_DISMISS_DELAY_MILLIS,
) : AutoCloseable {
    private var popup: Popup? = null
    private val dismissTimer = Timer(dismissDelayMillis) { hide() }.apply {
        isRepeats = false
    }

    init {
        require(dismissDelayMillis > 0)
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
        val tip = owner.createToolTip().apply {
            tipText = text
        }
        val ownerLocation = runCatching(owner::getLocationOnScreen).getOrNull() ?: return
        val offset = JBUI.scale(PREVIEW_SELECTION_TOOLTIP_OFFSET)
        popup = PopupFactory.getSharedInstance().getPopup(
            owner,
            tip,
            ownerLocation.x + anchor.x + offset,
            ownerLocation.y + anchor.y + offset,
        ).also(Popup::show)
        dismissTimer.restart()
    }

    fun hide() {
        dismissTimer.stop()
        popup?.hide()
        popup = null
    }

    override fun close() {
        hide()
    }
}

private const val PREVIEW_SELECTION_TOOLTIP_DISMISS_DELAY_MILLIS = 5_000
private const val PREVIEW_SELECTION_TOOLTIP_OFFSET = 12
