package com.viewcompose

import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.demo.registry.DemoScenarioRegistry
import com.viewcompose.host.android.resources.AndroidResourceRefreshController
import com.viewcompose.host.android.resources.LocalAndroidResources
import com.viewcompose.host.android.resources.booleanResource
import com.viewcompose.host.android.resources.colorResource
import com.viewcompose.host.android.resources.dimensionPixelSizeResource
import com.viewcompose.host.android.resources.dimensionResource
import com.viewcompose.host.android.resources.integerArrayResource
import com.viewcompose.host.android.resources.integerResource
import com.viewcompose.host.android.resources.pluralStringResource
import com.viewcompose.host.android.resources.stringArrayResource
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.material3.Material3DynamicColorPolicy
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.overlay.material3.android.host.AndroidOverlayHost
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Environment
import com.viewcompose.ui.foundation.Image
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.SideEffect
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.modifier.systemBarsInsetsPadding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

/** Demonstrates resource and environment refreshes without recreating the Activity or root View. */
class ResourceConfigurationActivity : AppCompatActivity() {
    private lateinit var configurationController: DemoResourceConfigurationController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configurationController = DemoResourceConfigurationController(this)
        setMaterial3UiContent(
            debug = true,
            debugTag = "ViewComposeSample",
            dynamicColorPolicy = Material3DynamicColorPolicy.Disabled,
            rootContext = configurationController.context,
            resourceRefreshController = configurationController.refreshController,
            overlayHostFactory = ::AndroidOverlayHost,
            onRenderResult = DemoRenderDiagnosticsStore::record,
        ) { root ->
            ResourceConfigurationPage(
                root = root,
                controller = configurationController,
                hostActivity = this@ResourceConfigurationActivity,
                scenario = DemoScenarioRegistry.fromIntent(intent),
            )
        }
    }

    companion object {
        internal fun newIntent(context: Context): Intent =
            Intent(context, ResourceConfigurationActivity::class.java)
    }
}

/**
 * Owns a stable Android Context whose base Resources can be replaced for demo verification.
 *
 * The mutable fields are configuration inputs, not ViewCompose state. Publishing a new Android
 * configuration through [refreshController] is the only invalidation path exercised by the page.
 */
internal class DemoResourceConfigurationController(
    private val sourceContext: Context,
) {
    private val baseline = Configuration(sourceContext.resources.configuration)
    private val baselineDensityDpi = sourceContext.resources.displayMetrics.densityDpi
    private val mutableContext = MutableContextWrapper(themedContext(baseline))
    private var language = Locale.ENGLISH
    private var dark = false
    private var largeFont = false
    private var highDensity = false
    private var rtl = false

    val context: Context
        get() = mutableContext

    val refreshController = AndroidResourceRefreshController()

    fun toggleLanguage() {
        language = if (language.language == Locale.ENGLISH.language) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH
        publish()
    }

    fun toggleNightMode() {
        dark = !dark
        publish()
    }

    fun toggleFontScale() {
        largeFont = !largeFont
        publish()
    }

    fun toggleDensity() {
        highDensity = !highDensity
        publish()
    }

    fun toggleLayoutDirection() {
        rtl = !rtl
        publish()
    }

    fun reset() {
        language = Locale.ENGLISH
        dark = false
        largeFont = false
        highDensity = false
        rtl = false
        publish()
    }

    private fun publish() {
        mutableContext.setBaseContext(themedContext(currentConfiguration()))
        refreshController.refresh()
    }

    private fun themedContext(configuration: Configuration): Context {
        val configured = sourceContext.createConfigurationContext(configuration)
        return ContextThemeWrapper(configured, R.style.Theme_ViewCompose)
    }

    private fun currentConfiguration(): Configuration {
        return Configuration(baseline).apply {
            setLocales(LocaleList(language))
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
            fontScale = if (largeFont) 1.3f else 1f
            densityDpi = if (highDensity) (baselineDensityDpi * 1.25f).roundToInt() else baselineDensityDpi
            screenLayout = (screenLayout and Configuration.SCREENLAYOUT_LAYOUTDIR_MASK.inv()) or if (rtl) {
                Configuration.SCREENLAYOUT_LAYOUTDIR_RTL
            } else {
                Configuration.SCREENLAYOUT_LAYOUTDIR_LTR
            }
        }
    }
}

private fun UiTreeBuilder.ResourceConfigurationPage(
    root: ViewGroup,
    controller: DemoResourceConfigurationController,
    hostActivity: AppCompatActivity,
    scenario: DemoScenarioSpec?,
) {
    val resources = LocalAndroidResources.current
    val configuration = resources.configuration
    val displayMetrics = resources.displayMetrics
    val isDark = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    val isRtl = configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    val localeTag = configuration.locales[0].toLanguageTag()
    val resolvedColor = colorResource(R.color.resource_configuration_accent)
    val resolvedDimension = dimensionResource(R.dimen.resource_configuration_spacing)
    val resolvedPixels = dimensionPixelSizeResource(R.dimen.resource_configuration_spacing)
    val facts = "locale=$localeTag; night=${if (isDark) "dark" else "light"}; " +
        "direction=${if (isRtl) "rtl" else "ltr"}; fontScale=${formatFloat(configuration.fontScale)}; " +
        "densityDpi=${displayMetrics.densityDpi}; revision=${Environment.resourceRevision}; " +
        "root=${System.identityHashCode(root)}"
    val values = buildString {
        append(stringResource(R.string.resource_configuration_greeting, "ViewCompose"))
        append(" | ")
        append(pluralStringResource(R.plurals.resource_configuration_items, 3, 3))
        append(" | color=")
        append(String.format(Locale.US, "#%08X", resolvedColor))
        append(" | dimen=")
        append(resolvedDimension.value)
        append("dp/")
        append(resolvedPixels)
        append("px | bool=")
        append(booleanResource(R.bool.resource_configuration_night_value))
        append(" | int=")
        append(integerResource(R.integer.resource_configuration_answer))
        append(" | strings=")
        append(stringArrayResource(R.array.resource_configuration_labels).joinToString())
        append(" | integers=")
        append(integerArrayResource(R.array.resource_configuration_numbers).joinToString())
    }

    val currentTheme = Theme.current
    SideEffect {
        hostActivity.applyDemoThemeWindowAppearance(currentTheme)
    }
    LazyColumn(
        items = listOf("header", "controls", "facts", "resources", "image"),
        key = { it },
        modifier = Modifier
            .fillMaxSize()
            .systemBarsInsetsPadding()
            .backgroundColor(Theme.colors.background)
            .padding(horizontal = 16.dp)
            .testTag(DemoTestTags.RESOURCE_CONFIGURATION_ROOT)
            .scenarioTarget(scenario, DemoAutomationRole.Root),
    ) { section ->
        when (section) {
            "header" -> Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.resource_configuration_title),
                    style = UiTextStyle(fontSizeSp = 24.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.RESOURCE_CONFIGURATION_TITLE)
                        .scenarioTarget(scenario, DemoAutomationRole.Ready),
                )
                Text(
                    text = stringResource(R.string.resource_configuration_description),
                    color = TextDefaults.secondaryColor(),
                )
            }

            "controls" -> Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 16.dp),
            ) {
                Button(
                    text = stringResource(R.string.resource_configuration_language_action),
                    onClick = controller::toggleLanguage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.RESOURCE_CONFIGURATION_LANGUAGE)
                        .scenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                )
                Button(
                    text = stringResource(R.string.resource_configuration_night_action),
                    onClick = controller::toggleNightMode,
                    modifier = Modifier.fillMaxWidth().testTag(DemoTestTags.RESOURCE_CONFIGURATION_NIGHT),
                )
                Button(
                    text = stringResource(R.string.resource_configuration_font_action),
                    onClick = controller::toggleFontScale,
                    modifier = Modifier.fillMaxWidth().testTag(DemoTestTags.RESOURCE_CONFIGURATION_FONT_SCALE),
                )
                Button(
                    text = stringResource(R.string.resource_configuration_density_action),
                    onClick = controller::toggleDensity,
                    modifier = Modifier.fillMaxWidth().testTag(DemoTestTags.RESOURCE_CONFIGURATION_DENSITY),
                )
                Button(
                    text = stringResource(R.string.resource_configuration_direction_action),
                    onClick = controller::toggleLayoutDirection,
                    modifier = Modifier.fillMaxWidth().testTag(DemoTestTags.RESOURCE_CONFIGURATION_DIRECTION),
                )
                Button(
                    text = stringResource(R.string.resource_configuration_reset_action),
                    onClick = controller::reset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .scenarioTarget(scenario, DemoAutomationRole.Reset),
                )
            }

            "facts" -> Text(
                text = facts,
                modifier = Modifier
                    .fillMaxWidth()
                    .margin(top = 16.dp)
                    .testTag(DemoTestTags.RESOURCE_CONFIGURATION_FACTS)
                    .scenarioTarget(scenario, DemoAutomationRole.State),
            )

            "resources" -> Text(
                text = values,
                color = resolvedColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .margin(top = 12.dp)
                    .testTag(DemoTestTags.RESOURCE_CONFIGURATION_VALUES),
            )

            "image" -> Image(
                source = ImageSource.Resource(R.drawable.resource_configuration_badge),
                contentDescription = stringResource(R.string.resource_configuration_image_description),
                modifier = Modifier
                    .size(96.dp, 96.dp)
                    .margin(top = 16.dp, bottom = 24.dp)
                    .testTag(DemoTestTags.RESOURCE_CONFIGURATION_IMAGE)
                    .scenarioTarget(scenario, DemoAutomationRole.Target),
            )
        }
    }
}

private fun Modifier.scenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier {
    val target = scenario?.automation?.get(role) ?: return this
    return demoAutomationTarget(target)
}

private fun formatFloat(value: Float): String = String.format(Locale.US, "%.2f", value)
