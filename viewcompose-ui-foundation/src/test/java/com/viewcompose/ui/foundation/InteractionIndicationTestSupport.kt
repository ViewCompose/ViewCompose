package com.viewcompose.ui.foundation

import com.viewcompose.ui.modifier.InteractionIndicationModifierElement
import com.viewcompose.ui.node.UiInteractionIndication
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.VNode

internal fun VNode.stateLayerColorsOrNull(): UiStateLayerColors? {
    val indication = modifier.elements
        .filterIsInstance<InteractionIndicationModifierElement>()
        .lastOrNull()
        ?.indication
    return (indication as? UiInteractionIndication.StateLayer)?.colors
}

internal fun VNode.requireStateLayerColors(): UiStateLayerColors =
    requireNotNull(stateLayerColorsOrNull()) { "VNode does not carry a state-layer indication." }
