package com.viewcompose.performance

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec

internal fun createAndroidViewsListPerformanceScreen(
    context: Context,
    scenario: DemoScenarioSpec,
    fixtures: PerformanceFixtures,
): View {
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(PERFORMANCE_BACKGROUND_COLOR)
        performanceScenarioTarget(scenario, DemoAutomationRole.Root)
    }
    val adapter = AndroidViewsPerformanceListAdapter(
        context = context,
        initialRows = fixtures.listRows(revision = 0),
    )
    var revision = 0
    lateinit var header: AndroidViewsPerformanceHeader
    fun submitRevision(nextRevision: Int) {
        adapter.submitRows(fixtures.listRows(nextRevision)) {
            revision = nextRevision
            header.stateView.text = fixtures.copy.listRevision(revision)
        }
    }
    header = createAndroidViewsPerformanceHeader(
        context = context,
        readyText = fixtures.copy.listReady(
            fixtures.copy.engineName(
                engine = PerformanceEngine.AndroidViews,
                shadowsEnabled = false,
            ),
        ),
        stateText = fixtures.copy.listRevision(revision),
        primaryActionText = fixtures.copy.mutateList,
        resetText = fixtures.copy.resetList,
        scenario = scenario,
        onPrimaryAction = { submitRevision(revision + 1) },
        onReset = { submitRevision(0) },
    )
    root.addView(
        header.view,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )
    val list = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        this.adapter = adapter
        // The declarative controls do not request item-placement or change animations. Disable the
        // platform default so the native mutation control measures reuse and binding, not an
        // additional transition workload.
        itemAnimator = null
        setHasFixedSize(false)
        clipToPadding = false
        setPadding(
            context.performanceDp(8),
            context.performanceDp(8),
            context.performanceDp(8),
            context.performanceDp(8),
        )
        addItemDecoration(
            AndroidViewsPerformanceListSpacing(
                spacingPx = context.performanceDp(6),
            ),
        )
        performanceScenarioTarget(scenario, DemoAutomationRole.Target)
    }
    root.addView(
        list,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ),
    )
    return root
}

internal class AndroidViewsPerformanceListAdapter(
    private val context: Context,
    initialRows: List<PerformanceListRow>,
) : RecyclerView.Adapter<AndroidViewsPerformanceListAdapter.RowHolder>() {
    private var rows: List<PerformanceListRow> = initialRows

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemId(position: Int): Long = rows[position].id.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder =
        RowHolder(context)

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        holder.bind(rows[position])
    }

    override fun onBindViewHolder(
        holder: RowHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            holder.bindContent(rows[position])
        }
    }

    fun submitRows(
        nextRows: List<PerformanceListRow>,
        onCommitted: () -> Unit,
    ) {
        if (rows == nextRows) {
            onCommitted()
            return
        }
        val previousRows = rows
        val diff = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = previousRows.size

                override fun getNewListSize(): Int = nextRows.size

                override fun areItemsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int,
                ): Boolean = previousRows[oldItemPosition].id == nextRows[newItemPosition].id

                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int,
                ): Boolean = previousRows[oldItemPosition] == nextRows[newItemPosition]

                override fun getChangePayload(
                    oldItemPosition: Int,
                    newItemPosition: Int,
                ): Any = ContentPayload
            },
            true,
        )
        rows = nextRows
        diff.dispatchUpdatesTo(this)
        onCommitted()
    }

    internal class RowHolder(
        private val context: Context,
    ) : RecyclerView.ViewHolder(LinearLayout(context)) {
        private val root = itemView as LinearLayout
        private val accent = View(context)
        private val accentBackground: GradientDrawable = context.performanceRoundedBackground(
            color = PERFORMANCE_PRIMARY_COLOR,
            radiusDp = 3,
        )
        private val title = context.performanceTextView(
            sizeSp = 14f,
            color = PERFORMANCE_PRIMARY_TEXT_COLOR,
            medium = true,
        )
        private val subtitle = context.performanceTextView(
            sizeSp = 12f,
            color = PERFORMANCE_SECONDARY_TEXT_COLOR,
        )
        private val badge = context.performanceTextView(
            sizeSp = 12f,
            color = PERFORMANCE_PRIMARY_COLOR,
            medium = true,
        )

        init {
            root.orientation = LinearLayout.HORIZONTAL
            root.gravity = Gravity.CENTER_VERTICAL
            root.background = context.performanceRoundedBackground(
                color = PERFORMANCE_SURFACE_COLOR,
                radiusDp = 10,
            )
            root.setPadding(
                context.performanceDp(10),
                context.performanceDp(10),
                context.performanceDp(10),
                context.performanceDp(10),
            )
            root.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            root.addView(
                accent.apply { background = accentBackground },
                LinearLayout.LayoutParams(
                    context.performanceDp(6),
                    context.performanceDp(44),
                ),
            )
            val copy = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            copy.addView(
                title,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            copy.addView(
                subtitle,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).withTopMargin(context.performanceDp(3)),
            )
            root.addView(
                copy,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).withStartMargin(context.performanceDp(10)),
            )
            badge.gravity = Gravity.CENTER
            badge.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            badge.background = context.performanceRoundedBackground(
                color = PERFORMANCE_BADGE_COLOR,
                radiusDp = 12,
            )
            badge.setPadding(
                context.performanceDp(8),
                context.performanceDp(4),
                context.performanceDp(8),
                context.performanceDp(4),
            )
            root.addView(
                badge,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).withStartMargin(context.performanceDp(10)),
            )
        }

        fun bind(row: PerformanceListRow) {
            title.text = row.title
            accentBackground.setColor(row.accentColor)
            bindContent(row)
        }

        fun bindContent(row: PerformanceListRow) {
            subtitle.text = row.subtitle
            badge.text = row.badge
        }
    }

    private object ContentPayload
}

private class AndroidViewsPerformanceListSpacing(
    private val spacingPx: Int,
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: android.graphics.Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position != RecyclerView.NO_POSITION && position < state.itemCount - 1) {
            outRect.bottom = spacingPx
        }
    }
}
