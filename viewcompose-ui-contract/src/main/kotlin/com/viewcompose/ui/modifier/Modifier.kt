package com.viewcompose.ui.modifier

open class Modifier private constructor(
    val elements: List<ModifierElement>,
) {
    fun then(element: ModifierElement): Modifier = Modifier(elements + element)

    fun then(modifier: Modifier): Modifier = Modifier(elements + modifier.elements)

    override fun equals(other: Any?): Boolean =
        this === other || (other is Modifier && elements == other.elements)

    override fun hashCode(): Int = elements.hashCode()

    override fun toString(): String = "Modifier(elements=$elements)"

    companion object : Modifier(emptyList())
}

interface ModifierElement
