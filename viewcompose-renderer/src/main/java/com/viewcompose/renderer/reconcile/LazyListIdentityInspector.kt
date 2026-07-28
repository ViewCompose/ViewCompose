package com.viewcompose.renderer.reconcile

import com.viewcompose.ui.node.LazyListItem

/**
 * lazy item identity 的静态分析结果。
 * Static analysis result for lazy item identity.
 */
data class LazyListIdentityAnalysis(
    val missingKeyIndexes: List<Int>,
    val duplicateKeys: List<Any>,
) {
    /**
     * 只有所有 item 都有唯一 key 时，RecyclerView 才能使用精确 keyed diff。
     * RecyclerView can use precise keyed diff only when every item has a unique key.
     */
    val supportsKeyedDiff: Boolean
        get() = missingKeyIndexes.isEmpty() && duplicateKeys.isEmpty()

    /**
     * 返回面向日志/诊断的身份问题摘要。
     * Returns a diagnostics/logging summary of identity problems.
     */
    fun warningMessage(listName: String): String? {
        if (supportsKeyedDiff) {
            return null
        }
        val parts = buildList {
            if (missingKeyIndexes.isNotEmpty()) {
                add("missing keys at indexes $missingKeyIndexes")
            }
            if (duplicateKeys.isNotEmpty()) {
                add("duplicate keys $duplicateKeys")
            }
        }
        return "LazyColumn $listName cannot use keyed diff: ${parts.joinToString()}"
    }
}

/**
 * 检查 lazy item 是否具备稳定 diff 身份。
 * Checks whether lazy items have stable diff identity.
 */
object LazyListIdentityInspector {
    /**
     * 收集缺失 key 的 index 和重复 key。
     * Collects missing-key indexes and duplicate keys.
     */
    fun analyze(items: List<LazyListItem>): LazyListIdentityAnalysis {
        val missingKeyIndexes = items.mapIndexedNotNull { index, item ->
            index.takeIf { item.key == null }
        }
        val duplicateKeys = items
            .mapNotNull { it.key }
            .groupingBy { it }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .toList()
        return LazyListIdentityAnalysis(
            missingKeyIndexes = missingKeyIndexes,
            duplicateKeys = duplicateKeys,
        )
    }
}
