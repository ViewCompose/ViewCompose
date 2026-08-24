package com.viewcompose.ui.node

/**
 * Immutable, random-access submission for a renderer-owned lazy collection.
 *
 * This is a Q3 consistency and performance contract. One instance represents one complete
 * revision: [size], [get], [indexOfKey], optional sticky-header metadata, and [updatesFrom] must
 * describe the same immutable state for the instance's entire lifetime. Reads are synchronous,
 * side-effect-free, and must never trigger loading. Keys are unique; [indexOfKey] returns exactly
 * the index whose item has that key, or `-1` when the key is absent.
 *
 * [updatesFrom] may expose a compact ordered transform only for a predecessor the implementation
 * recognizes. Each operation observes the list state produced by preceding operations. Returning
 * an empty list promises semantic equality, while `null` requests the renderer's generic keyed
 * reconciliation. A renderer validates operation bounds and final size before installing a
 * candidate, but the table remains responsible for the semantic accuracy of its transform.
 *
 * Implementations may compute items on demand, provided repeated access within one revision is
 * semantically equal and does not allocate or retain metadata proportional to unloaded slots.
 *
 * @sample com.viewcompose.ui.samples.lazyItemTableSample
 */
interface LazyItemTable : Iterable<LazyListItem> {
    /** Number of addressable item positions in this immutable revision. */
    val size: Int

    /**
     * Returns the item at [index] without triggering external work.
     *
     * @throws IndexOutOfBoundsException when [index] is outside `0 until size`
     */
    operator fun get(index: Int): LazyListItem

    /**
     * Iterates every presented position in order without triggering loading.
     *
     * Iteration is proportional to [size]; compact-table consumers should prefer indexed access
     * when they do not intentionally need the complete presented range.
     */
    override fun iterator(): Iterator<LazyListItem> = object : Iterator<LazyListItem> {
        private var index = 0

        override fun hasNext(): Boolean = index < size

        override fun next(): LazyListItem {
            if (!hasNext()) throw NoSuchElementException()
            return get(index++)
        }
    }

    /**
     * Materializes every presented position as an immutable finite list.
     *
     * This operation is proportional to [size] for a compact table. Finite wrappers return their
     * retained backing list without copying, preserving exact accepted-submission identity.
     */
    fun toList(): List<LazyListItem> = List(size) { index -> get(index) }

    /**
     * Returns the unique position of [key], or `-1` when this revision does not contain it.
     */
    fun indexOfKey(key: Any): Int

    /**
     * Returns an exact ordered transform from [previous], an empty list for semantic equality, or
     * `null` when the renderer must perform generic keyed reconciliation.
     */
    fun updatesFrom(previous: LazyItemTable): List<LazyItemTableUpdate>?
}

/**
 * Optional sticky-header index supplied by a [LazyItemTable] without forcing a full table scan.
 *
 * Tables that can contain [LazyListItemKind.StickyHeader] implement this Q2 metadata contract.
 * Tables that do not implement it promise that every item has [LazyListItemKind.Item].
 */
interface LazyItemTableStickyHeaders {
    /** Whether this revision contains at least one sticky header. */
    val hasStickyHeaders: Boolean

    /**
     * Returns the closest sticky-header position at or before [itemIndex], or `-1` when absent.
     *
     * @throws IndexOutOfBoundsException when [itemIndex] is outside the table
     */
    fun findStickyHeaderIndex(itemIndex: Int): Int
}

/** Ordered immutable update supplied by [LazyItemTable.updatesFrom]. */
sealed interface LazyItemTableUpdate {
    /**
     * Inserts [count] consecutive target items at [index].
     *
     * @property index insertion position in the state produced by preceding operations
     * @property count positive number of consecutive target items to insert
     */
    data class InsertRange(
        val index: Int,
        val count: Int,
    ) : LazyItemTableUpdate

    /**
     * Removes [count] consecutive current items beginning at [index].
     *
     * @property index first removal position in the state produced by preceding operations
     * @property count positive number of consecutive current items to remove
     */
    data class RemoveRange(
        val index: Int,
        val count: Int,
    ) : LazyItemTableUpdate

    /**
     * Moves one current item from [fromIndex] to [toIndex].
     *
     * @property fromIndex source position in the state produced by preceding operations
     * @property toIndex destination position in that same state after removing the source item
     */
    data class Move(
        val fromIndex: Int,
        val toIndex: Int,
    ) : LazyItemTableUpdate

    /**
     * Rebinds [count] consecutive target items beginning at [index].
     *
     * @property index first target position in the state produced by preceding operations
     * @property count positive number of consecutive target items to rebind
     */
    data class ChangeRange(
        val index: Int,
        val count: Int,
    ) : LazyItemTableUpdate

    /** Replaces all adapter positions while retaining renderer-owned lifecycle rules. */
    data object ReloadAll : LazyItemTableUpdate
}

/**
 * Wraps this immutable finite list as a validated [LazyItemTable].
 *
 * The receiver is retained without copying and must not be mutated after this call. Construction
 * validates unique keys and builds bounded lookup and sticky-header metadata. Different wrappers
 * use generic keyed reconciliation unless their lists are semantically equal.
 *
 * @sample com.viewcompose.ui.samples.lazyItemTableSample
 * @receiver immutable finite lazy-item list
 * @return random-access table retaining the receiver
 * @throws IllegalArgumentException when item keys are duplicated or the finite table exceeds the
 * supported indexed-map capacity
 */
fun List<LazyListItem>.asLazyItemTable(): LazyItemTable = FiniteLazyItemTable(this)

private class FiniteLazyItemTable(
    private val items: List<LazyListItem>,
) : LazyItemTable, LazyItemTableStickyHeaders {
    private val positionsByKey: Map<Any, Int>
    private val stickyHeaderIndices: IntArray

    init {
        require(items.size <= MAX_FINITE_ITEM_COUNT) {
            "Finite lazy collection item count is too large: ${items.size}"
        }
        val positions = HashMap<Any, Int>(mapCapacity(items.size))
        var stickyPositions: IntArray? = null
        var stickyCount = 0
        items.forEachIndexed { index, item ->
            require(positions.putIfAbsent(item.key, index) == null) {
                "Lazy collection keys must be unique. Duplicate key: ${item.key}"
            }
            if (item.kind == LazyListItemKind.StickyHeader) {
                val current = stickyPositions
                val target = when {
                    current == null -> IntArray(minOf(items.size, INITIAL_STICKY_CAPACITY))
                    stickyCount == current.size -> current.copyOf(
                        minOf(items.size, current.size * 2),
                    )
                    else -> current
                }
                stickyPositions = target
                target[stickyCount++] = index
            }
        }
        positionsByKey = positions
        stickyHeaderIndices = stickyPositions?.copyOf(stickyCount) ?: intArrayOf()
    }

    override val size: Int
        get() = items.size

    override fun get(index: Int): LazyListItem = items[index]

    override fun toList(): List<LazyListItem> = items

    override fun indexOfKey(key: Any): Int = positionsByKey[key] ?: -1

    override fun updatesFrom(previous: LazyItemTable): List<LazyItemTableUpdate>? {
        if (previous === this) return emptyList()
        if (previous !is FiniteLazyItemTable || previous.items != items) return null
        return emptyList()
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is FiniteLazyItemTable && items == other.items
    }

    override fun hashCode(): Int = items.hashCode()

    override val hasStickyHeaders: Boolean
        get() = stickyHeaderIndices.isNotEmpty()

    override fun findStickyHeaderIndex(itemIndex: Int): Int {
        if (itemIndex !in items.indices) {
            throw IndexOutOfBoundsException(
                "Lazy item index $itemIndex is outside 0 until ${items.size}.",
            )
        }
        val match = stickyHeaderIndices.binarySearch(itemIndex)
        if (match >= 0) return stickyHeaderIndices[match]
        val preceding = -match - 2
        return if (preceding >= 0) stickyHeaderIndices[preceding] else -1
    }

    private companion object {
        private const val INITIAL_STICKY_CAPACITY = 4
        private const val HASH_MAP_LOAD_FACTOR = 0.75f
        private const val MAX_FINITE_ITEM_COUNT = 1 shl 29

        fun mapCapacity(size: Int): Int {
            if (size <= 0) return 0
            return ((size / HASH_MAP_LOAD_FACTOR) + 1).toInt()
        }
    }
}
