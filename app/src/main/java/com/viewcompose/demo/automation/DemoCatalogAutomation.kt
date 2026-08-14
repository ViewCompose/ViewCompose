package com.viewcompose.demo.automation

import com.viewcompose.R
import com.viewcompose.demo.contract.DemoAutomationContract
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioId

internal object DemoCatalogAutomation {
    private val catalogId = DemoScenarioId("catalog")

    val contract: DemoAutomationContract = DemoAutomationContract.create(
        catalogId,
        Triple(DemoAutomationRole.Root, R.id.demo_catalog_root, "demo_catalog_root"),
        Triple(DemoAutomationRole.Ready, R.id.demo_catalog_ready, "demo_catalog_ready"),
        Triple(
            DemoAutomationRole.PrimaryAction,
            R.id.demo_catalog_primary_action,
            "demo_catalog_primary_action",
        ),
        Triple(
            DemoAutomationRole.SecondaryAction,
            R.id.demo_catalog_secondary_action,
            "demo_catalog_secondary_action",
        ),
        Triple(DemoAutomationRole.State, R.id.demo_catalog_state, "demo_catalog_state"),
        Triple(DemoAutomationRole.Target, R.id.demo_catalog_target, "demo_catalog_target"),
    )
}
