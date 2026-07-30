package com.viewcompose.renderer.view.lazy.reuse

import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.renderer.R

/**
 * Lazy 容器和 Pager 的 RecyclerView 默认策略集合。
 * Default RecyclerView policies for lazy containers and pagers.
 *
 * 它集中配置 item animator 和可选共享复用池，避免每个容器重复处理 RecyclerView 细节。
 * It centralizes item animator and optional shared pool configuration so each container does not duplicate RecyclerView details.
 */
internal object FrameworkRecyclerViewDefaults {
    private val lazyColumnPool = RecyclerView.RecycledViewPool()
    private val lazyRowPool = RecyclerView.RecycledViewPool()
    private val lazyGridPool = RecyclerView.RecycledViewPool()
    private val horizontalPagerPool = RecyclerView.RecycledViewPool()
    private val verticalPagerPool = RecyclerView.RecycledViewPool()

    fun applyLazyColumnDefaults(
        recyclerView: RecyclerView,
        sharePool: Boolean = false,
        disableItemAnimator: Boolean = false,
        animateInsert: Boolean = true,
        animateRemove: Boolean = true,
        animateMove: Boolean = true,
        animateChange: Boolean = true,
    ) {
        applyDefaults(
            recyclerView = recyclerView,
            sharedPool = lazyColumnPool,
            sharePool = sharePool,
            disableItemAnimator = disableItemAnimator,
            animateInsert = animateInsert,
            animateRemove = animateRemove,
            animateMove = animateMove,
            animateChange = animateChange,
        )
    }

    fun applyLazyRowDefaults(
        recyclerView: RecyclerView,
        sharePool: Boolean = false,
        disableItemAnimator: Boolean = false,
        animateInsert: Boolean = true,
        animateRemove: Boolean = true,
        animateMove: Boolean = true,
        animateChange: Boolean = true,
    ) {
        applyDefaults(
            recyclerView = recyclerView,
            sharedPool = lazyRowPool,
            sharePool = sharePool,
            disableItemAnimator = disableItemAnimator,
            animateInsert = animateInsert,
            animateRemove = animateRemove,
            animateMove = animateMove,
            animateChange = animateChange,
        )
    }

    fun applyLazyGridDefaults(
        recyclerView: RecyclerView,
        sharePool: Boolean = false,
        disableItemAnimator: Boolean = false,
        animateInsert: Boolean = true,
        animateRemove: Boolean = true,
        animateMove: Boolean = true,
        animateChange: Boolean = true,
    ) {
        applyDefaults(
            recyclerView = recyclerView,
            sharedPool = lazyGridPool,
            sharePool = sharePool,
            disableItemAnimator = disableItemAnimator,
            animateInsert = animateInsert,
            animateRemove = animateRemove,
            animateMove = animateMove,
            animateChange = animateChange,
        )
    }

    fun applyHorizontalPagerDefaults(
        recyclerView: RecyclerView,
        sharePool: Boolean = false,
        disableItemAnimator: Boolean = false,
        animateInsert: Boolean = true,
        animateRemove: Boolean = true,
        animateMove: Boolean = true,
        animateChange: Boolean = true,
    ) {
        applyDefaults(
            recyclerView = recyclerView,
            sharedPool = horizontalPagerPool,
            sharePool = sharePool,
            disableItemAnimator = disableItemAnimator,
            animateInsert = animateInsert,
            animateRemove = animateRemove,
            animateMove = animateMove,
            animateChange = animateChange,
        )
    }

    fun applyVerticalPagerDefaults(
        recyclerView: RecyclerView,
        sharePool: Boolean = false,
        disableItemAnimator: Boolean = false,
        animateInsert: Boolean = true,
        animateRemove: Boolean = true,
        animateMove: Boolean = true,
        animateChange: Boolean = true,
    ) {
        applyDefaults(
            recyclerView = recyclerView,
            sharedPool = verticalPagerPool,
            sharePool = sharePool,
            disableItemAnimator = disableItemAnimator,
            animateInsert = animateInsert,
            animateRemove = animateRemove,
            animateMove = animateMove,
            animateChange = animateChange,
        )
    }

    private fun applyDefaults(
        recyclerView: RecyclerView,
        sharedPool: RecyclerView.RecycledViewPool,
        sharePool: Boolean,
        disableItemAnimator: Boolean,
        animateInsert: Boolean,
        animateRemove: Boolean,
        animateMove: Boolean,
        animateChange: Boolean,
    ) {
        // item 内部的 Decoration Host 需要越过 holder 边界绘制阴影；具体 Lazy 容器仍负责
        // 把最终输出裁剪到自己的 viewport。
        // Item Decoration Hosts must draw past holder bounds; each concrete lazy container still
        // clips the final output to its own viewport.
        recyclerView.clipChildren = false
        val anyMotionEnabled = animateInsert || animateRemove || animateMove || animateChange
        if (disableItemAnimator || !anyMotionEnabled) {
            recyclerView.itemAnimator = null
        } else {
            val configurable = recyclerView.itemAnimator as? FrameworkItemAnimator
                ?: FrameworkItemAnimator().also { recyclerView.itemAnimator = it }
            configurable.updateMotionPolicy(
                animateInsert = animateInsert,
                animateRemove = animateRemove,
                animateMove = animateMove,
                animateChange = animateChange,
            )
        }
        if (sharePool) {
            recyclerView.setRecycledViewPool(sharedPool)
        } else {
            // 本地 pool 挂在 tag 上，重复 bind defaults 时不会丢失已缓存 holder。
            // The local pool is kept on a tag so repeated default binding does not discard cached holders.
            recyclerView.setRecycledViewPool(resolveLocalPool(recyclerView))
        }
    }

    private fun resolveLocalPool(recyclerView: RecyclerView): RecyclerView.RecycledViewPool {
        val existing = recyclerView.getTag(R.id.viewcompose_local_recycled_view_pool)
            as? RecyclerView.RecycledViewPool
        if (existing != null) {
            return existing
        }
        return RecyclerView.RecycledViewPool().also { local ->
            recyclerView.setTag(R.id.viewcompose_local_recycled_view_pool, local)
        }
    }
}

/**
 * 可按声明式 motion policy 逐项关闭动画的默认 ItemAnimator。
 * Default ItemAnimator whose animation types can be disabled by declarative motion policy.
 */
private class FrameworkItemAnimator : DefaultItemAnimator() {
    private var animateInsert: Boolean = true
    private var animateRemove: Boolean = true
    private var animateMove: Boolean = true
    private var animateChange: Boolean = true

    fun updateMotionPolicy(
        animateInsert: Boolean,
        animateRemove: Boolean,
        animateMove: Boolean,
        animateChange: Boolean,
    ) {
        this.animateInsert = animateInsert
        this.animateRemove = animateRemove
        this.animateMove = animateMove
        this.animateChange = animateChange
        supportsChangeAnimations = animateChange
    }

    override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
        if (!animateInsert) {
            dispatchAddStarting(holder)
            dispatchAddFinished(holder)
            return false
        }
        return super.animateAdd(holder)
    }

    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        if (!animateRemove) {
            dispatchRemoveStarting(holder)
            dispatchRemoveFinished(holder)
            return false
        }
        return super.animateRemove(holder)
    }

    override fun animateMove(
        holder: RecyclerView.ViewHolder,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
    ): Boolean {
        if (!animateMove) {
            dispatchMoveStarting(holder)
            dispatchMoveFinished(holder)
            return false
        }
        return super.animateMove(holder, fromX, fromY, toX, toY)
    }

    override fun animateChange(
        oldHolder: RecyclerView.ViewHolder,
        newHolder: RecyclerView.ViewHolder?,
        fromLeft: Int,
        fromTop: Int,
        toLeft: Int,
        toTop: Int,
    ): Boolean {
        if (!animateChange) {
            dispatchChangeStarting(oldHolder, true)
            dispatchChangeFinished(oldHolder, true)
            if (newHolder != null && newHolder !== oldHolder) {
                dispatchChangeStarting(newHolder, false)
                dispatchChangeFinished(newHolder, false)
            }
            return false
        }
        return super.animateChange(oldHolder, newHolder, fromLeft, fromTop, toLeft, toTop)
    }
}
