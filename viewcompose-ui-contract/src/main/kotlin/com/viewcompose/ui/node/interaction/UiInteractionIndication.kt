package com.viewcompose.ui.node

/**
 * Defines renderer-neutral visual feedback for transient user interaction.
 *
 * Component and design-system layers resolve semantic roles, opacities, and enabled-state policy
 * before constructing an indication. Renderers execute the resolved value without consulting a
 * theme or recovering design-system defaults. An absent indication leaves feedback selection to
 * the renderer when a node is otherwise interactive.
 *
 * Renderers must handle every subtype available in the artifact version they consume. New
 * subtypes are introduced only as an explicit compatibility change while ViewCompose remains on
 * its alpha line.
 */
sealed interface UiInteractionIndication {
    /**
     * Draws one bounded color layer for pressed, focused, and hovered states.
     *
     * The target's resolved shape defines the mask. Disabled and inactive states are transparent,
     * and [UiStateLayerColors] defines overlapping-state precedence.
     *
     * @property colors complete colors for the supported transient interaction states
     */
    data class StateLayer(
        val colors: UiStateLayerColors,
    ) : UiInteractionIndication
}
