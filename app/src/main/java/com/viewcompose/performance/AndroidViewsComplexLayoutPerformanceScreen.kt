package com.viewcompose.performance

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec

internal fun createAndroidViewsComplexLayoutPerformanceScreen(
    context: Context,
    scenario: DemoScenarioSpec,
    fixtures: PerformanceFixtures,
): View {
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(PERFORMANCE_BACKGROUND_COLOR)
        performanceScenarioTarget(scenario, DemoAutomationRole.Root)
    }
    val initialCards = fixtures.dashboardCards(revision = 0)
    val cardViews = initialCards.map { card ->
        AndroidViewsDashboardCardView(
            context = context,
            initialCard = card,
            copy = fixtures.copy,
        )
    }
    var propertyRevision = 0
    var structureRevision = 0
    lateinit var header: AndroidViewsPerformanceHeader
    fun updateStateText() {
        header.stateView.text = fixtures.copy.dashboardRevision(
            propertyRevision,
            structureRevision,
        )
    }
    fun submitPropertyRevision(nextRevision: Int) {
        val cards = fixtures.dashboardCards(nextRevision)
        check(cards.size == cardViews.size) {
            "Android Views complex-layout control requires a stable card count."
        }
        cardViews.forEachIndexed { index, cardView ->
            cardView.bindProperties(cards[index])
        }
        propertyRevision = nextRevision
        updateStateText()
    }
    fun submitStructureRevision(nextRevision: Int) {
        val cards = fixtures.dashboardCards(nextRevision)
        check(cards.size == cardViews.size) {
            "Android Views complex-layout control requires a stable card count."
        }
        cardViews.forEachIndexed { index, cardView ->
            cardView.bindStructure(cards[index])
        }
        structureRevision = nextRevision
        updateStateText()
    }
    fun resetRevisions() {
        val cards = fixtures.dashboardCards(0)
        cardViews.forEachIndexed { index, cardView ->
            cardView.bindProperties(cards[index])
            cardView.bindStructure(cards[index])
        }
        propertyRevision = 0
        structureRevision = 0
        updateStateText()
    }
    header = createAndroidViewsPerformanceHeader(
        context = context,
        readyText = fixtures.copy.complexReady(
            fixtures.copy.engineName(
                engine = PerformanceEngine.AndroidViews,
                shadowsEnabled = false,
            ),
        ),
        stateText = fixtures.copy.dashboardRevision(propertyRevision, structureRevision),
        primaryActionText = fixtures.copy.updateDashboard,
        secondaryActionText = fixtures.copy.updateDashboardStructure,
        resetText = fixtures.copy.resetDashboard,
        scenario = scenario,
        onPrimaryAction = { submitPropertyRevision(propertyRevision + 1) },
        onSecondaryAction = { submitStructureRevision(structureRevision + 1) },
        onReset = ::resetRevisions,
    )
    root.addView(
        header.view,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            context.performanceDp(8),
            context.performanceDp(8),
            context.performanceDp(8),
            context.performanceDp(8),
        )
    }
    cardViews.forEachIndexed { index, cardView ->
        content.addView(
            cardView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (index < cardViews.lastIndex) {
                    bottomMargin = context.performanceDp(8)
                }
            },
        )
    }
    val scroll = ScrollView(context).apply {
        isFillViewport = true
        addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        performanceScenarioTarget(scenario, DemoAutomationRole.Target)
    }
    root.addView(
        scroll,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ),
    )
    return root
}

internal class AndroidViewsDashboardCardView(
    context: Context,
    initialCard: PerformanceDashboardCard,
    private val copy: PerformanceCopy,
) : LinearLayout(context) {
    private val titleView = context.performanceTextView(
        text = initialCard.title,
        sizeSp = 16f,
        color = PERFORMANCE_PRIMARY_TEXT_COLOR,
        medium = true,
    )
    private val subtitleView = context.performanceTextView(
        text = initialCard.subtitle,
        sizeSp = 12f,
        color = PERFORMANCE_SECONDARY_TEXT_COLOR,
    )
    private val statusView = context.performanceTextView(
        text = initialCard.status,
        sizeSp = 12f,
        color = initialCard.accentColor,
        medium = true,
    )
    private val metricValueViews = mutableListOf<TextView>()
    private var detailView: LinearLayout? = null

    init {
        orientation = VERTICAL
        background = context.performanceRoundedBackground(
            color = PERFORMANCE_SURFACE_COLOR,
            radiusDp = 12,
        )
        setPadding(
            context.performanceDp(12),
            context.performanceDp(12),
            context.performanceDp(12),
            context.performanceDp(12),
        )
        addView(createHeader(context, initialCard))
        addView(
            createMetricRow(context, initialCard.metrics),
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).withTopMargin(context.performanceDp(10)),
        )
        addView(
            createTagRow(context, initialCard.tags),
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).withTopMargin(context.performanceDp(10)),
        )
        bindProperties(initialCard)
        bindStructure(initialCard)
    }

    fun bindProperties(card: PerformanceDashboardCard) {
        titleView.text = card.title
        subtitleView.text = card.subtitle
        statusView.text = card.status
        statusView.setTextColor(card.accentColor)
        check(card.metrics.size == metricValueViews.size) {
            "Android Views complex-layout control requires a stable metric count."
        }
        card.metrics.forEachIndexed { index, metric ->
            metricValueViews[index].text = metric.value
        }
    }

    fun bindStructure(card: PerformanceDashboardCard) {
        if (card.detailsVisible) {
            val details = detailView ?: createDetailRow(context, card).also { created ->
                detailView = created
                addView(
                    created,
                    LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).withTopMargin(context.performanceDp(10)),
                )
            }
            details.getChildAt(1).let { content ->
                check(content is TextView)
                content.text = copy.detailContent(card.id + 1)
            }
        } else {
            detailView?.let { details ->
                removeView(details)
                detailView = null
            }
        }
    }

    private fun createHeader(
        context: Context,
        card: PerformanceDashboardCard,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val accent = View(context).apply {
            background = context.performanceRoundedBackground(
                color = card.accentColor,
                radiusDp = 5,
            )
        }
        addView(
            accent,
            LayoutParams(
                context.performanceDp(10),
                context.performanceDp(48),
            ),
        )
        val labels = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(
                titleView,
                LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                subtitleView,
                LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).withTopMargin(context.performanceDp(3)),
            )
        }
        addView(
            labels,
            LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ).withStartMargin(context.performanceDp(10)),
        )
        statusView.gravity = Gravity.CENTER
        statusView.background = context.performanceRoundedBackground(
            color = PERFORMANCE_BADGE_COLOR,
            radiusDp = 12,
        )
        statusView.setPadding(
            context.performanceDp(8),
            context.performanceDp(4),
            context.performanceDp(8),
            context.performanceDp(4),
        )
        addView(
            statusView,
            LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).withStartMargin(context.performanceDp(10)),
        )
    }

    private fun createMetricRow(
        context: Context,
        metrics: List<PerformanceDashboardMetric>,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        metrics.forEachIndexed { index, metric ->
            val metricView = LinearLayout(context).apply {
                orientation = VERTICAL
                background = context.performanceRoundedBackground(
                    color = PERFORMANCE_BACKGROUND_COLOR,
                    radiusDp = 8,
                )
                setPadding(
                    context.performanceDp(8),
                    context.performanceDp(8),
                    context.performanceDp(8),
                    context.performanceDp(8),
                )
            }
            metricView.addView(
                context.performanceTextView(
                    text = metric.label,
                    sizeSp = 12f,
                    color = PERFORMANCE_SECONDARY_TEXT_COLOR,
                ),
            )
            val valueView = context.performanceTextView(
                text = metric.value,
                sizeSp = 15f,
                color = PERFORMANCE_PRIMARY_TEXT_COLOR,
                medium = true,
            )
            metricValueViews += valueView
            metricView.addView(
                valueView,
                LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).withTopMargin(context.performanceDp(2)),
            )
            addView(
                metricView,
                LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    if (index > 0) {
                        marginStart = context.performanceDp(6)
                    }
                },
            )
        }
    }

    private fun createTagRow(
        context: Context,
        tags: List<String>,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        tags.forEachIndexed { index, tag ->
            val tagView = context.performanceTextView(
                text = tag,
                sizeSp = 12f,
                color = PERFORMANCE_SECONDARY_TEXT_COLOR,
            ).apply {
                gravity = Gravity.CENTER
                background = context.performanceRoundedBackground(
                    color = PERFORMANCE_BADGE_COLOR,
                    radiusDp = 10,
                )
                setPadding(
                    context.performanceDp(7),
                    context.performanceDp(3),
                    context.performanceDp(7),
                    context.performanceDp(3),
                )
            }
            addView(
                tagView,
                LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (index > 0) {
                        marginStart = context.performanceDp(6)
                    }
                },
            )
        }
    }

    private fun createDetailRow(
        context: Context,
        card: PerformanceDashboardCard,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = context.performanceRoundedBackground(
            color = PERFORMANCE_BACKGROUND_COLOR,
            radiusDp = 8,
        )
        setPadding(
            context.performanceDp(8),
            context.performanceDp(8),
            context.performanceDp(8),
            context.performanceDp(8),
        )
        addView(
            context.performanceTextView(
                text = copy.detail,
                sizeSp = 12f,
                color = card.accentColor,
                medium = true,
            ),
        )
        addView(
            context.performanceTextView(
                text = copy.detailContent(card.id + 1),
                sizeSp = 12f,
                color = PERFORMANCE_SECONDARY_TEXT_COLOR,
            ),
            LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ).withStartMargin(context.performanceDp(8)),
        )
    }
}
