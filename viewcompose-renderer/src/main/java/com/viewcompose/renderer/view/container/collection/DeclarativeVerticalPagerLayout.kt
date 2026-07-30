package com.viewcompose.renderer.view.container

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.renderer.interop.asRenderContainerHandle
import com.viewcompose.renderer.reconcile.LazyListDiff
import com.viewcompose.renderer.view.lazy.focus.LazyFocusFollowLayoutMonitor
import com.viewcompose.renderer.view.lazy.session.LazyHolderRegistry
import com.viewcompose.renderer.view.lazy.session.LazyItemSessionController
import com.viewcompose.ui.state.PagerConnector
import com.viewcompose.ui.state.PagerState
import com.viewcompose.renderer.view.lazy.reuse.FrameworkRecyclerViewDefaults
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.shadow.android.ShadowDecorationHostLayout

/**
 * 纵向 Pager 的 Android ViewPager2 宿主。
 * Android ViewPager2 host for vertical Pager.
 *
 * 除页面状态同步外，还在底层 RecyclerView 上应用输入法焦点跟随策略。
 * Besides page state synchronization, it applies keyboard focus-follow policy to the backing RecyclerView.
 */
internal class DeclarativeVerticalPagerLayout(
    context: Context,
) : FrameLayout(context) {
    private val viewPager = ViewPager2(context)
    private val adapter = VerticalPagerAdapter()
    private var onPageChanged: ((Int) -> Unit)? = null
    private var pagerState: PagerState? = null
    // 程序化切页时暂时禁止 onPageChanged，避免 bind -> ViewPager2 -> state 的重复通知。
    // Temporarily block onPageChanged during programmatic paging to avoid duplicate bind -> ViewPager2 -> state notifications.
    private var suppressCallback: Boolean = false
    private var focusFollowKeyboardEnabled: Boolean = false
    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            pagerState?.updateFromPager(
                currentPage = position,
                pageOffset = 0f,
            )
            if (!suppressCallback) {
                onPageChanged?.invoke(position)
            }
        }

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int,
        ) {
            pagerState?.updateFromPager(
                currentPage = position,
                pageOffset = positionOffset,
            )
        }
    }

    init {
        viewPager.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        viewPager.adapter = adapter
        viewPager.registerOnPageChangeCallback(pageChangeCallback)
        applyRecyclerDefaults()
        applyFocusFollowPolicy()
        addView(viewPager)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val startNs = LayoutPassTracker.beginTiming()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        LayoutPassTracker.recordMeasureSince(javaClass, startNs)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val startNs = LayoutPassTracker.beginTiming()
        super.onLayout(changed, left, top, right, bottom)
        LayoutPassTracker.recordLayoutSince(javaClass, startNs)
    }

    fun bind(
        pages: List<LazyListItem>,
        currentPage: Int,
        onPageChanged: ((Int) -> Unit)?,
        offscreenPageLimit: Int,
        pagerState: PagerState?,
        userScrollEnabled: Boolean,
    ) {
        this.onPageChanged = onPageChanged
        if (this.pagerState !== pagerState) {
            this.pagerState?.attach(null)
        }
        this.pagerState = pagerState
        pagerState?.attach(
            object : PagerConnector {
                override fun scrollToPage(page: Int) {
                    viewPager.setCurrentItem(page, true)
                }
            },
        )
        viewPager.offscreenPageLimit = offscreenPageLimit.coerceAtLeast(1)
        viewPager.isUserInputEnabled = userScrollEnabled
        applyFocusFollowPolicy()
        adapter.submitPages(pages)
        val resolvedPage = if (pages.isEmpty()) {
            return
        } else {
            currentPage.coerceIn(0, pages.lastIndex)
        }
        if (viewPager.currentItem != resolvedPage) {
            suppressCallback = true
            viewPager.setCurrentItem(resolvedPage, false)
            suppressCallback = false
        }
        pagerState?.updateFromPager(
            currentPage = viewPager.currentItem,
            pageOffset = 0f,
        )
    }

    fun dispose() {
        applyFocusFollowPolicy(enabled = false)
        pagerState?.attach(null)
        viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        adapter.disposeAll()
    }

    fun applyRecyclerDefaults(
        sharePool: Boolean = false,
        disableItemAnimator: Boolean = false,
        animateInsert: Boolean = true,
        animateRemove: Boolean = true,
        animateMove: Boolean = true,
        animateChange: Boolean = true,
    ) {
        resolvePagerRecyclerView()?.let { recyclerView ->
            FrameworkRecyclerViewDefaults.applyVerticalPagerDefaults(
                recyclerView = recyclerView,
                sharePool = sharePool,
                disableItemAnimator = disableItemAnimator,
                animateInsert = animateInsert,
                animateRemove = animateRemove,
                animateMove = animateMove,
                animateChange = animateChange,
            )
        }
    }

    fun setFocusFollowKeyboardEnabled(enabled: Boolean) {
        focusFollowKeyboardEnabled = enabled
        applyFocusFollowPolicy()
    }

    private fun applyFocusFollowPolicy(enabled: Boolean = focusFollowKeyboardEnabled) {
        resolvePagerRecyclerView()?.let { recyclerView ->
            LazyFocusFollowLayoutMonitor.apply(
                recyclerView = recyclerView,
                enabled = enabled,
            )
        }
    }

    private fun resolvePagerRecyclerView(): RecyclerView? {
        return viewPager.getChildAt(0) as? RecyclerView
    }
}

/**
 * 纵向 Pager 的页面 adapter，负责在页面复用时保持 session 生命周期正确。
 * Page adapter for vertical Pager, keeping session lifetimes correct across page reuse.
 */
internal class VerticalPagerAdapter : RecyclerView.Adapter<VerticalPagerViewHolder>() {
    private var pages: List<LazyListItem> = emptyList()
    private val holderRegistry = LazyHolderRegistry<VerticalPagerViewHolder> { holder ->
        holder.recycle()
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalPagerViewHolder {
        val container = ShadowDecorationHostLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        return VerticalPagerViewHolder(container)
    }

    override fun onBindViewHolder(holder: VerticalPagerViewHolder, position: Int) {
        bindHolder(
            holder = holder,
            position = position,
            payload = null,
        )
    }

    override fun onBindViewHolder(
        holder: VerticalPagerViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        bindHolder(
            holder = holder,
            position = position,
            payload = payloads.lastOrNull(),
        )
    }

    override fun onViewAttachedToWindow(holder: VerticalPagerViewHolder) {
        super.onViewAttachedToWindow(holder)
        holderRegistry.onAttached(holder)
    }

    override fun onViewDetachedFromWindow(holder: VerticalPagerViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holderRegistry.onDetached(holder)
    }

    override fun onViewRecycled(holder: VerticalPagerViewHolder) {
        holderRegistry.onRecycled(holder)
    }

    override fun getItemCount(): Int = pages.size

    override fun getItemId(position: Int): Long {
        val key = pages[position].key
        return key?.hashCode()?.toLong() ?: position.toLong()
    }

    fun submitPages(newPages: List<LazyListItem>) {
        val result = LazyListDiff.calculate(
            previous = this.pages,
            next = newPages,
        )
        this.pages = result.items
        if (result.diffResult != null) {
            result.diffResult.dispatchUpdatesTo(this)
        } else {
            notifyDataSetChanged()
        }
        if (result.updates.isEmpty()) {
            holderRegistry.forEachBound { holder ->
                val position = holder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && position < this.pages.size) {
                    holder.bind(this.pages[position])
                }
            }
        }
    }

    fun disposeAll() {
        holderRegistry.disposeAll()
        pages = emptyList()
        notifyDataSetChanged()
    }

    private fun bindHolder(
        holder: VerticalPagerViewHolder,
        position: Int,
        payload: Any?,
    ) {
        holderRegistry.onBound(holder)
        holder.bind(
            item = pages[position],
            payload = payload,
        )
    }
}

/**
 * 单个纵向 Pager 页面 holder，将声明式页面绑定到 Android 容器。
 * Holder for one vertical Pager page, binding declarative page content to an Android container.
 */
internal class VerticalPagerViewHolder(
    private val container: FrameLayout,
) : RecyclerView.ViewHolder(container) {
    private val controller = LazyItemSessionController(
        createSession = { item ->
            item.sessionFactory.create(container.asRenderContainerHandle())
        },
        clearContainer = container::removeAllViews,
    )

    fun bind(
        item: LazyListItem,
        payload: Any? = null,
    ) {
        controller.bind(
            item = item,
            payload = payload,
        )
    }

    fun recycle() {
        controller.recycle()
    }
}
