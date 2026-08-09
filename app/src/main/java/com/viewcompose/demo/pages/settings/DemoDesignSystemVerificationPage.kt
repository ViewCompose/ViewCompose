package com.viewcompose

import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Dialog
import com.viewcompose.ui.foundation.Environment
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberSaveable
import com.viewcompose.ui.foundation.rememberTextFieldState
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.node.TextFieldAutofillHint
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.systemBarsInsetsPadding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

/** Renders the internal five-component design-system slice with screenshot-readable attribution. */
internal fun UiTreeBuilder.DemoDesignSystemVerificationPage(
    onReplaceDesignSystem: (DemoDesignSystemKind) -> Unit,
) {
    val bundle = DemoDesignSystem
    val nextKind = when (bundle.kind) {
        DemoDesignSystemKind.RoundedReference -> DemoDesignSystemKind.CutContrast
        DemoDesignSystemKind.CutContrast -> DemoDesignSystemKind.RoundedReference
    }
    val checked = rememberSaveable(key = "design-system-switch") { mutableStateOf(true) }
    val selectedIndex = rememberSaveable(key = "design-system-navigation") { mutableStateOf(0) }
    val buttonClicks = rememberSaveable(key = "design-system-button-clicks") { mutableStateOf(0) }
    val dialogVisible = rememberSaveable(key = "design-system-dialog-visible") { mutableStateOf(false) }
    val field = rememberTextFieldState("Ada")
    val errorField = rememberTextFieldState("")
    LazyColumn(
        items = listOf("identity", "switching", "button", "surface", "switch", "textfield", "navigation"),
        key = { section -> section },
        modifier = Modifier
            .fillMaxSize()
            .backgroundColor(Theme.colors.background)
            .systemBarsInsetsPadding()
            .padding(horizontal = 16.dp)
            .testTag(DemoTestTags.DESIGN_SYSTEM_ROOT),
    ) { section ->
        when (section) {
            "identity" -> DemoDesignSystemIdentitySection(bundle)
            "switching" -> Column(
                spacing = 10.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 18.dp),
            ) {
                DemoDesignSectionTitle("Root/session coherence")
                Text(
                    text = "Lazy system: ${bundle.kind.id}",
                    color = Theme.colors.onSurfaceVariant,
                    style = Theme.typography.bodyMedium,
                    modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_LAZY_IDENTITY),
                )
                Row(spacing = 10.dp, verticalAlignment = VerticalAlignment.Center) {
                    DemoDesignButton(
                        text = "Switch to ${nextKind.id}",
                        onClick = { onReplaceDesignSystem(nextKind) },
                        modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_REPLACE_ROOT),
                    )
                    DemoDesignButton(
                        text = "Open dialog",
                        onClick = { dialogVisible.value = true },
                        modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_OPEN_DIALOG),
                    )
                }
            }
            "button" -> Column(
                spacing = 10.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 18.dp),
            ) {
                DemoDesignSectionTitle("Button · shared BasicButton")
                Row(spacing = 10.dp, verticalAlignment = VerticalAlignment.Center) {
                    DemoDesignButton(
                        text = "Confirm",
                        onClick = { buttonClicks.value += 1 },
                        modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_BUTTON),
                    )
                    DemoDesignButton(
                        text = "Disabled",
                        enabled = false,
                        modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_BUTTON_DISABLED),
                    )
                }
                Text(
                    text = "Button clicks: ${buttonClicks.value}",
                    color = Theme.colors.onSurfaceVariant,
                    style = Theme.typography.bodySmall,
                    modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_BUTTON_STATUS),
                )
            }

            "surface" -> Column(
                spacing = 10.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 18.dp),
            ) {
                DemoDesignSectionTitle("Surface/Card · shared BasicSurface")
                DemoDesignCard(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.DESIGN_SYSTEM_SURFACE),
                ) {
                    Column(spacing = 6.dp) {
                        Text(
                            text = "Resolved surface",
                            color = Theme.colors.onSurface,
                            style = Theme.typography.titleMedium,
                        )
                        Text(
                            text = "Fill, border, shape, clip and interaction share one resolved contract.",
                            color = Theme.colors.onSurfaceVariant,
                            style = Theme.typography.bodySmall,
                        )
                    }
                }
            }

            "switch" -> Column(
                spacing = 4.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 18.dp),
            ) {
                DemoDesignSectionTitle("Switch · design-system composite")
                DemoDesignSwitch(
                    text = "Synchronize workspace",
                    checked = checked.value,
                    onCheckedChange = { checked.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.DESIGN_SYSTEM_SWITCH),
                )
                DemoDesignSwitch(
                    text = "Disabled control",
                    checked = false,
                    onCheckedChange = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.DESIGN_SYSTEM_SWITCH_DISABLED),
                )
                Text(
                    text = "Checked: ${checked.value}",
                    color = Theme.colors.onSurfaceVariant,
                    style = Theme.typography.bodySmall,
                    modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_SWITCH_STATUS),
                )
            }

            "textfield" -> Column(
                spacing = 14.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 18.dp),
            ) {
                DemoDesignSectionTitle("TextField · native editing core")
                DemoDesignTextField(
                    state = field,
                    label = "Account name",
                    placeholder = "Name",
                    supportingText = "IME, selection and autofill remain native.",
                    isError = false,
                    autofillHints = setOf(TextFieldAutofillHint.Username),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.DESIGN_SYSTEM_TEXT_FIELD),
                )
                DemoDesignTextField(
                    state = errorField,
                    label = "Required field",
                    placeholder = "Required",
                    supportingText = "A value is required",
                    isError = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.DESIGN_SYSTEM_TEXT_FIELD_ERROR),
                )
            }

            else -> Column(
                spacing = 10.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 18.dp, bottom = 28.dp),
            ) {
                DemoDesignSectionTitle("NavigationBar · design-system composite")
                DemoDesignNavigationBar(
                    items = demoDesignNavigationItems(),
                    selectedIndex = selectedIndex.value,
                    onItemSelected = { selectedIndex.value = it },
                    modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_NAVIGATION),
                )
                Text(
                    text = "Selected: ${demoDesignNavigationItems()[selectedIndex.value].label}",
                    color = Theme.colors.onSurfaceVariant,
                    style = Theme.typography.bodySmall,
                    modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_NAVIGATION_STATUS),
                )
            }
        }
    }
    Dialog(
        visible = dialogVisible.value,
        requestKey = "design-system-coherence-dialog",
        onDismissRequest = { dialogVisible.value = false },
    ) {
        DemoDesignCard(modifier = Modifier.fillMaxWidth()) {
            Column(spacing = 10.dp) {
                Text(
                    text = "Overlay system: ${bundle.kind.id}",
                    color = Theme.colors.onSurface,
                    style = Theme.typography.titleMedium,
                    modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_OVERLAY_IDENTITY),
                )
                Text(
                    text = "Overlay token: demo-design-system/${bundle.kind.id}",
                    color = Theme.colors.onSurfaceVariant,
                    style = Theme.typography.bodyMedium,
                    modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_OVERLAY_TOKEN_SOURCE),
                )
                DemoDesignButton(
                    text = "Switch overlay to ${nextKind.id}",
                    onClick = { onReplaceDesignSystem(nextKind) },
                )
                DemoDesignButton(
                    text = "Close coherent dialog",
                    onClick = { dialogVisible.value = false },
                )
            }
        }
    }
}

private fun UiTreeBuilder.DemoDesignSystemIdentitySection(bundle: DemoDesignSystemBundle) {
    Column(
        spacing = 10.dp,
        modifier = Modifier.fillMaxWidth().margin(top = 16.dp),
    ) {
        Text(
            text = "Multi-design-system verification",
            color = Theme.colors.onBackground,
            style = Theme.typography.headlineSmall,
        )
        Text(
            text = "The visible identity, recipe, token source, motion and fallback values are part of every screenshot.",
            color = Theme.colors.onSurfaceVariant,
            style = Theme.typography.bodyMedium,
        )
        DiagnosticFactGroup(
            title = "Screenshot identity",
            facts = listOf(
                DiagnosticFact("Fixture", "multi-design-five-component-v1"),
                DiagnosticFact("Design system", "${bundle.kind.id} · ${bundle.kind.label}"),
                DiagnosticFact("Token source", "demo-design-system/${bundle.kind.id}"),
                DiagnosticFact("Recipe identity", "${bundle.kind.id}/five-component-v1"),
                DiagnosticFact("Mode", if (bundle.tokens.metadata.isDark == true) "Dark" else "Light"),
                DiagnosticFact("Reduced motion", bundle.reducedMotionEnabled.toString()),
                DiagnosticFact("Font scale", Environment.density.fontScale.toString()),
                DiagnosticFact("Layout direction", Environment.layoutDirection.name),
                DiagnosticFact("Shape", bundle.tokens.shapes.medium.demoLabel()),
                DiagnosticFact("Primary", bundle.tokens.colors.primary.asColorHex()),
                DiagnosticFact("Surface", bundle.tokens.colors.surface.asColorHex()),
                DiagnosticFact("Capability", "continuous-path=yes; backdrop-blur=tinted-surface"),
            ),
            valueTagsByLabel = mapOf(
                "Design system" to DemoTestTags.DESIGN_SYSTEM_IDENTITY,
                "Token source" to DemoTestTags.DESIGN_SYSTEM_TOKEN_SOURCE,
                "Recipe identity" to DemoTestTags.DESIGN_SYSTEM_RECIPE_IDENTITY,
                "Mode" to DemoTestTags.DESIGN_SYSTEM_MODE,
                "Reduced motion" to DemoTestTags.DESIGN_SYSTEM_REDUCED_MOTION,
                "Font scale" to DemoTestTags.DESIGN_SYSTEM_FONT_SCALE,
                "Capability" to DemoTestTags.DESIGN_SYSTEM_CAPABILITY,
            ),
        )
        DiagnosticFactGroup(
            title = "Conformance",
            facts = bundle.conformance.map { item ->
                DiagnosticFact(
                    item.component,
                    "${item.outcome.name} · ${item.implementation} · fallback=${item.fallback}",
                )
            },
        )
    }
}

private fun UiTreeBuilder.DemoDesignSectionTitle(text: String) {
    Text(
        text = text,
        color = Theme.colors.onSurface,
        style = UiTextStyle(fontSizeSp = 18.sp, fontWeight = 650, lineHeightSp = 24.sp),
    )
}
