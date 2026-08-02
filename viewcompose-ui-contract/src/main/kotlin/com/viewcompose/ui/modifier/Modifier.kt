package com.viewcompose.ui.modifier

/**
 * Stores an immutable, ordered chain of layout, style, interaction, semantics, and draw elements.
 *
 * Renderer phases interpret [elements] in declaration order; changing order may change measurement,
 * drawing, input, or parent-data behavior. Every append returns a new chain and leaves the receiver
 * unchanged. The companion object is the empty chain and conventional DSL starting point.
 *
 * @sample com.viewcompose.ui.samples.modifierChainSample
 * @property elements ordered immutable element list exposed to renderer implementations
 */
open class Modifier private constructor(
    val elements: List<ModifierElement>,
) {
    /**
     * Appends [element] to the end of this chain.
     *
     * @param element renderer contract to append after all current elements
     * @return a new modifier chain
     */
    fun then(element: ModifierElement): Modifier = Modifier(elements + element)

    /**
     * Appends every element from [modifier] to the end of this chain.
     *
     * @param modifier ordered chain to append
     * @return a new combined modifier chain
     */
    fun then(modifier: Modifier): Modifier = Modifier(elements + modifier.elements)

    /**
     * Returns whether [other] contains an equal element sequence in the same order.
     *
     * @param other value to compare
     * @return `true` for this instance or an equal modifier chain
     */
    override fun equals(other: Any?): Boolean =
        this === other || (other is Modifier && elements == other.elements)

    /**
     * Returns the order-sensitive hash of [elements].
     *
     * @return hash consistent with [equals]
     */
    override fun hashCode(): Int = elements.hashCode()

    /**
     * Returns a diagnostic representation containing [elements].
     *
     * @return human-readable modifier-chain text
     */
    override fun toString(): String = "Modifier(elements=$elements)"

    /** Empty modifier chain used as the starting receiver for modifier extension functions. */
    companion object : Modifier(emptyList())
}

/**
 * Marks one platform-neutral contract element in a [Modifier] chain.
 *
 * Renderer implementations may dispatch on known implementations. Application code should use
 * public modifier extension functions instead of inventing elements that a renderer cannot bind.
 */
interface ModifierElement
