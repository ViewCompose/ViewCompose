package com.viewcompose

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.ViewGroup
import com.viewcompose.demo.registry.DemoScenarioRegistry
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp

/** Shows facts derived from the running package, device, and executable scenario registry. */
class DemoBuildInfoActivity : DemoRenderActivity() {
    override val demoTitle: String
        get() = getString(R.string.demo_build_info_title)

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        val packageManager = root.context.packageManager
        val packageName = root.context.packageName
        val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        val versionCode = if (Build.VERSION.SDK_INT >= 28) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val debuggable = root.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        with(builder) {
            LazyColumn(
                items = listOf("build"),
                key = { item -> item },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) { _ ->
                DiagnosticFactGroup(
                    title = stringResource(R.string.demo_build_info_title),
                    facts = listOf(
                        DiagnosticFact(stringResource(R.string.demo_build_package), packageName),
                        DiagnosticFact(
                            stringResource(R.string.demo_build_version),
                            "${packageInfo.versionName ?: "?"} ($versionCode)",
                        ),
                        DiagnosticFact(
                            stringResource(R.string.demo_build_variant),
                            if (debuggable) {
                                stringResource(R.string.demo_build_debuggable)
                            } else {
                                stringResource(R.string.demo_build_non_debuggable)
                            },
                        ),
                        DiagnosticFact(
                            stringResource(R.string.demo_build_sdk),
                            "${Build.VERSION.SDK_INT} / target ${root.context.applicationInfo.targetSdkVersion}",
                        ),
                        DiagnosticFact(
                            stringResource(R.string.demo_build_device),
                            "${Build.MANUFACTURER} ${Build.MODEL}",
                        ),
                        DiagnosticFact(
                            stringResource(R.string.demo_build_scenarios),
                            DemoScenarioRegistry.all().size.toString(),
                        ),
                    ),
                )
            }
        }
    }
}
