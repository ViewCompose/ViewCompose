package com.viewcompose.renderer.view.container

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

/**
 * LazyColumn/LazyRow 使用的 RecyclerView 基类。
 * RecyclerView base used by LazyColumn/LazyRow.
 */
internal class DeclarativeLazyListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : RecyclerView(context, attrs) {
    /**
     * 是否允许用户触摸滚动，程序化滚动和布局仍由 RecyclerView 正常处理。
     * Whether user touch scrolling is enabled while programmatic scroll and layout keep working.
     */
    var userScrollEnabled: Boolean = true

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return userScrollEnabled && super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return userScrollEnabled && super.onTouchEvent(event)
    }
}
