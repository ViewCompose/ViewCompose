package com.viewcompose.ui.modifier

/**
 * 不可变修饰符链，按声明顺序保存布局、样式、交互和绘制元素。
 * Immutable modifier chain that preserves declaration order for layout, style, interaction, and drawing elements.
 */
open class Modifier private constructor(
    val elements: List<ModifierElement>,
) {
    /**
     * 在链尾追加一个修饰符元素。
     * Appends one modifier element to the end of the chain.
     */
    fun then(element: ModifierElement): Modifier = Modifier(elements + element)

    /**
     * 在链尾追加另一条修饰符链。
     * Appends another modifier chain to the end of this chain.
     */
    fun then(modifier: Modifier): Modifier = Modifier(elements + modifier.elements)

    override fun equals(other: Any?): Boolean =
        this === other || (other is Modifier && elements == other.elements)

    override fun hashCode(): Int = elements.hashCode()

    override fun toString(): String = "Modifier(elements=$elements)"

    companion object : Modifier(emptyList())
}

/**
 * 单个修饰符元素的公共标记接口，具体语义由各元素数据类承载。
 * Public marker for one modifier element; concrete element data classes carry the actual semantics.
 */
interface ModifierElement
