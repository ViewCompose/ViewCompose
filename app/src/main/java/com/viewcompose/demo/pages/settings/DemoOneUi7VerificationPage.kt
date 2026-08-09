package com.viewcompose

import com.viewcompose.oneui7.OneUi7Button
import com.viewcompose.oneui7.OneUi7ButtonVariant
import com.viewcompose.oneui7.OneUi7NavigationBar
import com.viewcompose.oneui7.OneUi7NavigationItem
import com.viewcompose.oneui7.OneUi7Reference
import com.viewcompose.oneui7.OneUi7Surface
import com.viewcompose.oneui7.OneUi7Switch
import com.viewcompose.oneui7.OneUi7TextField
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.DesignSystemDiagnostics
import com.viewcompose.ui.foundation.Environment
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.rememberSaveable
import com.viewcompose.ui.foundation.rememberTextFieldState
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.systemBarsInsetsPadding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.unit.dp

/** Emits screenshot-readable evidence for the public One UI 7 five-component alpha slice. */
internal fun UiTreeBuilder.DemoOneUi7VerificationPage() {
    val clicks = rememberSaveable(key = "one-ui-7-clicks") { mutableStateOf(0) }
    val checked = rememberSaveable(key = "one-ui-7-switch") { mutableStateOf(true) }
    val selected = rememberSaveable(key = "one-ui-7-navigation") { mutableStateOf(0) }
    val account = rememberTextFieldState("Galaxy")
    val destinations = listOf(
        OneUi7NavigationItem("home", "Home"),
        OneUi7NavigationItem("search", "Search"),
        OneUi7NavigationItem("profile", "Profile"),
    )
    val attribution = DesignSystemDiagnostics.current
    val componentEvidence = attribution?.components?.joinToString(separator = " · ") { component ->
        "${component.familyId}:${component.recipeId}:${component.backend.name}/${component.conformance.name}"
    } ?: "unattributed"
    val overlayEvidence = attribution?.integrations?.joinToString(separator = " · ") { integration ->
        "${integration.capabilityId}:${integration.presenterId}/${integration.conformance.name}" +
            if (integration.fallback == "none") "" else "→${integration.fallback}"
    } ?: "unattributed"
    LazyColumn(
        items = listOf("identity", "button", "surface", "switch", "textfield", "navigation"),
        key = { it },
        modifier = Modifier
            .fillMaxSize()
            .backgroundColor(Theme.colors.background)
            .systemBarsInsetsPadding()
            .padding(horizontal = 16.dp)
            .testTag(DemoTestTags.ONE_UI_7_ROOT),
    ) { section ->
        when (section) {
            "identity" -> Column(
                spacing = 10.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 18.dp),
            ) {
                Text(
                    text = "One UI 7 five-component alpha",
                    color = Theme.colors.onBackground,
                    style = Theme.typography.headlineSmall,
                )
                Text(
                    text = "Public Samsung guidance pinned; ViewCompose-owned interpreted tokens.",
                    color = Theme.colors.onSurfaceVariant,
                    style = Theme.typography.bodyMedium,
                )
                DiagnosticFactGroup(
                    title = "Screenshot identity",
                    facts = listOf(
                        DiagnosticFact("Reference", OneUi7Reference.targetVersion),
                        DiagnosticFact("Component set", OneUi7Reference.componentSet),
                        DiagnosticFact("Token source", Theme.current.metadata.provenance.sourceId),
                        DiagnosticFact(
                            "Primary source",
                            Theme.current.metadata.provenance.originOf("colors.primary").name,
                        ),
                        DiagnosticFact("Design system", attribution?.designSystemId ?: "unattributed"),
                        DiagnosticFact("Recipe set", attribution?.recipeSetId ?: "unattributed"),
                        DiagnosticFact("Component backends", componentEvidence),
                        DiagnosticFact(
                            "Overlay transport",
                            attribution?.integration("overlay.dialog")?.transportId ?: "unattributed",
                        ),
                        DiagnosticFact("Overlay presenters", overlayEvidence),
                        DiagnosticFact("Mode", if (Theme.current.metadata.isDark == true) "Dark" else "Light"),
                        DiagnosticFact("Font scale", Environment.density.fontScale.toString()),
                        DiagnosticFact("Direction", Environment.layoutDirection.name),
                        DiagnosticFact("Primary", Theme.colors.primary.asColorHex()),
                        DiagnosticFact("Surface", Theme.colors.surface.asColorHex()),
                    ),
                    valueTagsByLabel = mapOf(
                        "Component set" to DemoTestTags.ONE_UI_7_IDENTITY,
                        "Token source" to DemoTestTags.ONE_UI_7_TOKEN_PRODUCER,
                        "Primary source" to DemoTestTags.ONE_UI_7_PRIMARY_ORIGIN,
                        "Design system" to DemoTestTags.ONE_UI_7_DESIGN_SYSTEM,
                        "Recipe set" to DemoTestTags.ONE_UI_7_RECIPE_SET,
                        "Component backends" to DemoTestTags.ONE_UI_7_COMPONENT_BACKENDS,
                    ),
                )
                DiagnosticFactGroup(
                    title = "Conformance",
                    facts = listOf(
                        DiagnosticFact("Button", "Equivalent · shared BasicButton"),
                        DiagnosticFact("Surface/Card", "Equivalent · shared BasicSurface"),
                        DiagnosticFact("Switch", "Equivalent · owned composite"),
                        DiagnosticFact("TextField", "Equivalent · native edit core"),
                        DiagnosticFact("Navigation", "Equivalent · text-only tabs"),
                        DiagnosticFact("Backdrop blur", "Degraded · tinted surface"),
                    ),
                )
            }

            "button" -> Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 20.dp),
            ) {
                Text("Buttons", color = Theme.colors.onSurface, style = Theme.typography.titleMedium)
                Row(spacing = 10.dp, verticalAlignment = VerticalAlignment.Center) {
                    OneUi7Button(
                        text = "Continue",
                        onClick = { clicks.value += 1 },
                        modifier = Modifier.testTag(DemoTestTags.ONE_UI_7_BUTTON),
                    )
                    OneUi7Button(
                        text = "Later",
                        onClick = {},
                        variant = OneUi7ButtonVariant.Neutral,
                    )
                }
                Text(
                    text = "Button clicks: ${clicks.value}",
                    color = Theme.colors.onSurfaceVariant,
                    style = Theme.typography.bodySmall,
                    modifier = Modifier.testTag(DemoTestTags.ONE_UI_7_BUTTON_STATUS),
                )
            }

            "surface" -> Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 20.dp),
            ) {
                Text("Surface/Card", color = Theme.colors.onSurface, style = Theme.typography.titleMedium)
                OneUi7Surface(modifier = Modifier.fillMaxWidth()) {
                    Column(spacing = 5.dp) {
                        Text("Connected devices", color = Theme.colors.onSurface, style = Theme.typography.titleSmall)
                        Text(
                            "Shared BasicSurface; renderer has no One UI branch.",
                            color = Theme.colors.onSurfaceVariant,
                            style = Theme.typography.bodySmall,
                        )
                    }
                }
            }

            "switch" -> Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 20.dp),
            ) {
                Text("Switch", color = Theme.colors.onSurface, style = Theme.typography.titleMedium)
                OneUi7Switch(
                    text = "Sync devices",
                    checked = checked.value,
                    onCheckedChange = { checked.value = it },
                    modifier = Modifier.fillMaxWidth().testTag(DemoTestTags.ONE_UI_7_SWITCH),
                )
                Text(
                    "Checked: ${checked.value}",
                    color = Theme.colors.onSurfaceVariant,
                    style = Theme.typography.bodySmall,
                    modifier = Modifier.testTag(DemoTestTags.ONE_UI_7_SWITCH_STATUS),
                )
            }

            "textfield" -> Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 20.dp),
            ) {
                Text("Text field", color = Theme.colors.onSurface, style = Theme.typography.titleMedium)
                OneUi7TextField(
                    state = account,
                    label = "Account name",
                    placeholder = "Name",
                    supportingText = "Native Android editing, selection and IME.",
                    modifier = Modifier.fillMaxWidth().testTag(DemoTestTags.ONE_UI_7_TEXT_FIELD),
                )
            }

            else -> Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 20.dp, bottom = 28.dp),
            ) {
                Text("Text-only navigation", color = Theme.colors.onSurface, style = Theme.typography.titleMedium)
                OneUi7NavigationBar(
                    items = destinations,
                    selectedIndex = selected.value,
                    onItemSelected = { selected.value = it },
                    modifier = Modifier.testTag(DemoTestTags.ONE_UI_7_NAVIGATION),
                )
                Text(
                    "Selected: ${destinations[selected.value].label}",
                    color = Theme.colors.onSurfaceVariant,
                    style = Theme.typography.bodySmall,
                    modifier = Modifier.testTag(DemoTestTags.ONE_UI_7_NAVIGATION_STATUS),
                )
            }
        }
    }
}
