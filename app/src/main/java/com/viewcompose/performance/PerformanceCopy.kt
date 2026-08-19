package com.viewcompose.performance

import android.content.Context
import com.viewcompose.R

/** Localized copy shared by every engine so comparison fixtures render the same content. */
internal class PerformanceCopy(
    context: Context,
) {
    private val resources = context.resources

    val mutateList: String = resources.getString(R.string.demo_performance_mutate_list)
    val resetList: String = resources.getString(R.string.demo_performance_reset_list)
    val updateDashboard: String = resources.getString(R.string.demo_performance_update_dashboard)
    val updateDashboardStructure: String =
        resources.getString(R.string.demo_performance_update_dashboard_structure)
    val resetDashboard: String = resources.getString(R.string.demo_performance_reset_dashboard)
    val active: String = resources.getString(R.string.demo_performance_status_active)
    val stable: String = resources.getString(R.string.demo_performance_status_stable)
    val updated: String = resources.getString(R.string.demo_performance_status_updated)
    val requests: String = resources.getString(R.string.demo_performance_metric_requests)
    val success: String = resources.getString(R.string.demo_performance_metric_success)
    val latency: String = resources.getString(R.string.demo_performance_metric_latency)
    val detail: String = resources.getString(R.string.demo_performance_detail)
    val constraintUpdate: String =
        resources.getString(R.string.demo_performance_constraint_update)
    val constraintReset: String =
        resources.getString(R.string.demo_performance_constraint_reset)

    fun engineName(engine: PerformanceEngine, shadowsEnabled: Boolean): String =
        if (shadowsEnabled) {
            resources.getString(R.string.demo_performance_engine_shadow, engine.displayName)
        } else {
            engine.displayName
        }

    fun listReady(engineName: String): String =
        resources.getString(R.string.demo_performance_list_ready, engineName)

    fun listRevision(revision: Int): String =
        resources.getString(R.string.demo_performance_list_revision, revision)

    fun listItem(index: Int): String =
        resources.getString(R.string.demo_performance_list_item, index)

    fun stableListSubtitle(group: Int): String =
        resources.getString(R.string.demo_performance_list_stable_subtitle, group)

    fun updatedListSubtitle(revision: Int, group: Int): String =
        resources.getString(R.string.demo_performance_list_updated_subtitle, revision, group)

    fun revisionBadge(revision: Int): String =
        resources.getString(R.string.demo_performance_revision_badge, revision)

    fun complexReady(engineName: String): String =
        resources.getString(R.string.demo_performance_complex_ready, engineName)

    fun dashboardRevision(propertyRevision: Int, structureRevision: Int): String =
        resources.getString(
            R.string.demo_performance_dashboard_revision,
            propertyRevision,
            structureRevision,
        )

    fun dashboardSection(index: Int): String =
        resources.getString(R.string.demo_performance_dashboard_section, index)

    fun nestedLayoutGroup(group: Int): String =
        resources.getString(R.string.demo_performance_nested_layout_group, group)

    fun updatedLayoutRevision(revision: Int): String =
        resources.getString(R.string.demo_performance_updated_layout_revision, revision)

    fun latencyValue(value: Int): String =
        resources.getString(R.string.demo_performance_latency_value, value)

    fun region(index: Int): String =
        resources.getString(R.string.demo_performance_region, index)

    fun tier(index: Int): String =
        resources.getString(R.string.demo_performance_tier, index)

    fun node(index: Int): String =
        resources.getString(R.string.demo_performance_node, index)

    fun detailContent(index: Int): String =
        resources.getString(R.string.demo_performance_detail_content, index)

    fun constraintReady(engineName: String, nodeCount: Int, workload: String): String =
        resources.getString(
            R.string.demo_performance_constraint_ready,
            engineName,
            nodeCount,
            workload,
        )

    fun constraintRevision(revision: Int): String =
        resources.getString(R.string.demo_performance_constraint_revision, revision)
}
