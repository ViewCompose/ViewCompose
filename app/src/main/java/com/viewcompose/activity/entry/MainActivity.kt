package com.viewcompose

import android.content.Intent
import android.view.ViewGroup
import com.viewcompose.demo.contract.EXTRA_DEMO_SCENARIO_ID
import com.viewcompose.demo.registry.DemoScenarioRegistry
import com.viewcompose.performance.EXTRA_PERFORMANCE_ENGINE
import com.viewcompose.performance.PerformanceComparisonActivity
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * demo 应用入口 Activity。
 * Entry Activity for the demo app.
 *
 * 普通启动展示首页；带 benchmark 或模块 extra 的启动会被转发到对应 Activity。
 * Normal launches show the home page; benchmark or module extras are forwarded to the matching Activity.
 */
class MainActivity : DemoRenderActivity() {
    override val demoTitle: String = "ViewCompose Demo"

    override fun redirectTargetIntent(): Intent? {
        if (intent?.hasExtra(EXTRA_DEMO_SCENARIO_ID) == true) {
            val scenario = DemoScenarioRegistry.require(
                intent?.getStringExtra(EXTRA_DEMO_SCENARIO_ID),
            )
            return DemoScenarioRegistry.createLaunchIntent(
                context = this,
                scenario = scenario,
                source = intent,
            )
        }
        if (intent?.hasExtra(EXTRA_DEMO_DESIGN_SYSTEM_KIND) == true) {
            return Intent(this, DemoDesignSystemVerificationActivity::class.java).apply {
                intent?.extras?.let(::putExtras)
            }
        }
        if (intent?.hasExtra(EXTRA_PERFORMANCE_ENGINE) == true) {
            // benchmark 入口复用主 Activity intent，保持 Macrobenchmark 的启动包名稳定。
            // Benchmark launches reuse the main Activity intent so Macrobenchmark keeps a stable package entry.
            return Intent(this, PerformanceComparisonActivity::class.java).apply {
                intent?.extras?.let(::putExtras)
            }
        }
        val moduleKey = intent?.getStringExtra(EXTRA_DEMO_MODULE_KEY)
        val targetActivity = moduleKey?.let(::findDemoModuleByKey)?.activityClass
        return targetActivity?.let {
            Intent(this, it).apply {
                intent?.extras?.let(::putExtras)
            }
        }
    }

    override fun UiTreeBuilder.buildRootScaffold(root: ViewGroup) {
        DemoHomeScaffold(root = root)
    }

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        // 主页面由 DemoHomeScaffold 自行管理内容，这里不会被调用。
        // Main content is managed by DemoHomeScaffold, so this hook is unused.
    }
}
