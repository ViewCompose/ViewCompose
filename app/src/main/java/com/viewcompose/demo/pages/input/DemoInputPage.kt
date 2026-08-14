package com.viewcompose

import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.demo.registry.DemoScenarioIds
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.TextFieldImeAction
import com.viewcompose.ui.node.TextFieldKeyboardOptions
import com.viewcompose.runtime.derivedStateOf
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonSize
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Checkbox
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.EmailField
import com.viewcompose.ui.foundation.Icon
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.InputControlColorOverride
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.NumberField
import com.viewcompose.ui.foundation.PasswordField
import com.viewcompose.ui.foundation.ProvideCheckboxColors
import com.viewcompose.ui.foundation.ProvideRadioButtonColors
import com.viewcompose.ui.foundation.ProvideSliderColors
import com.viewcompose.ui.foundation.ProvideSwitchColors
import com.viewcompose.ui.foundation.PullToRefresh
import com.viewcompose.ui.foundation.RadioButton
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.SearchBar
import com.viewcompose.ui.foundation.ScrollableColumn
import com.viewcompose.ui.foundation.Slider
import com.viewcompose.ui.foundation.SurfaceDefaults
import com.viewcompose.ui.foundation.Switch
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextArea
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.TextField
import com.viewcompose.ui.foundation.TextFieldSize
import com.viewcompose.ui.foundation.TextFieldVariant
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiThemeOverride
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.VerticalPager
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberTextFieldState
import com.viewcompose.ui.unit.sp

@ViewComposePreview(name = "Input · Fields", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewInputFields() {
    InputPage(InputFixture.Fields)
}

@ViewComposePreview(name = "Input · Selection", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewInputSelection() {
    InputPage(InputFixture.Selection)
}

@ViewComposePreview(name = "Input · Stress", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewInputStress() {
    InputPage(InputFixture.Stress)
}

@ViewComposePreview(name = "Input · Search", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewInputSearch() {
    InputPage(InputFixture.Search)
}

@ViewComposePreview(name = "Input · Summary", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewInputSummary() {
    InputPage(InputFixture.DerivedSummary)
}

internal enum class InputFixture(
    val scenarioId: DemoScenarioId,
) {
    Fields(DemoScenarioIds.InputFields),
    Selection(DemoScenarioIds.InputSelection),
    Stress(DemoScenarioIds.InputStress),
    Search(DemoScenarioIds.InputSearch),
    DerivedSummary(DemoScenarioIds.InputDerivedSummary),
    ;

    companion object {
        fun from(scenarioId: DemoScenarioId): InputFixture =
            entries.singleOrNull { fixture -> fixture.scenarioId == scenarioId }
                ?: error("Unsupported input scenario: $scenarioId")
    }
}

internal fun UiTreeBuilder.InputPage(
    fixture: InputFixture,
    scenario: DemoScenarioSpec? = null,
) {
    val fieldsActive = fixture == InputFixture.Fields
    val selectionActive = fixture == InputFixture.Selection
    val stressActive = fixture == InputFixture.Stress
    val searchActive = fixture == InputFixture.Search
    val summaryActive = fixture == InputFixture.DerivedSummary

    // A strict fixture owns only the state it renders. Inactive scenario state must not enter the
    // composition observer graph or contaminate benchmark allocation and invalidation counts.
    val benchmarkExpandedState = if (fieldsActive) remember { mutableStateOf(false) } else null
    val nameState = if (fieldsActive) rememberTextFieldState("GZQ") else null
    val emailState = if (fieldsActive) rememberTextFieldState("demo@viewcompose.dev") else null
    val passwordState = if (fieldsActive) rememberTextFieldState() else null
    val ageState = if (fieldsActive) rememberTextFieldState("3") else null
    val bioState = if (fieldsActive) {
        rememberTextFieldState("基于虚拟节点、键控 diff 和 Android View 互操作构建。")
    } else {
        null
    }
    val benchmarkFieldState = if (fieldsActive) rememberTextFieldState("紧凑数据") else null
    val disabledEmailState = if (fieldsActive) {
        rememberTextFieldState("disabled@viewcompose.dev")
    } else {
        null
    }

    val notificationsEnabledState = if (selectionActive) remember { mutableStateOf(true) } else null
    val analyticsEnabledState = if (selectionActive) remember { mutableStateOf(false) } else null
    val selectedTierState = if (selectionActive) remember { mutableStateOf("Alpha") } else null
    val intensityState = if (selectionActive) remember { mutableStateOf(32) } else null

    val stressExpandedState = if (stressActive) remember { mutableStateOf(false) } else null
    val stressReadonlyState = if (stressActive) remember { mutableStateOf(true) } else null
    val stressErrorState = if (stressActive) remember { mutableStateOf(true) } else null
    val stressTitleFieldState = if (stressActive) rememberTextFieldState("紧凑标题") else null
    val stressNotesFieldState = if (stressActive) rememberTextFieldState("只读笔记") else null
    val stressPasswordFieldState = if (stressActive) rememberTextFieldState() else null

    val searchQueryState = if (searchActive) rememberTextFieldState() else null
    val searchHistoryState = if (searchActive) rememberTextFieldState() else null
    val disabledSearchState = if (searchActive) rememberTextFieldState() else null
    val searchResultState = if (searchActive) remember { mutableStateOf("") } else null
    val scrollableSearchQueryState = if (searchActive) rememberTextFieldState() else null
    val verticalPagerSearchQueryState = if (searchActive) rememberTextFieldState() else null
    val pullRefreshSearchQueryState = if (searchActive) rememberTextFieldState() else null
    val focusFollowVerticalPagerPageState = if (searchActive) {
        remember { mutableStateOf(0) }
    } else {
        null
    }
    val pullRefreshFocusRefreshingState = if (searchActive) {
        remember { mutableStateOf(false) }
    } else {
        null
    }

    val summaryAlternateState = if (summaryActive) remember { mutableStateOf(false) } else null
    val summaryState = if (summaryActive) {
        val activeSummaryAlternateState = requireNotNull(summaryAlternateState)
        remember {
            derivedStateOf {
                if (activeSummaryAlternateState.value) {
                    "预览: ViewCompose · runtime@viewcompose.dev · 4y"
                } else {
                    "预览: GZQ · demo@viewcompose.dev · 3y"
                }
            }
        }
    } else {
        null
    }
    val pageItems = when (fixture) {
        InputFixture.Fields -> listOf("benchmark", "form")
        InputFixture.Selection -> listOf("controls")
        InputFixture.Stress -> listOf("stress")
        InputFixture.Search -> listOf("search")
        InputFixture.DerivedSummary -> listOf("summary")
    }

    LazyColumn(
        items = pageItems,
        key = { it },
        focusFollowKeyboard = fixture == InputFixture.Search,
        modifier = Modifier
            .fillMaxSize(),
    ) { section ->
        when (section) {
            "benchmark" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = "输入 Benchmark 锚点",
                subtitle = "默认字段页的 benchmark 控件。",
            ) {
                val benchmarkExpandedState = requireNotNull(benchmarkExpandedState)
                val benchmarkFieldState = requireNotNull(benchmarkFieldState)
                val nameState = requireNotNull(nameState)
                val emailState = requireNotNull(emailState)
                val passwordState = requireNotNull(passwordState)
                val ageState = requireNotNull(ageState)
                val bioState = requireNotNull(bioState)
                Text(
                    text = if (benchmarkExpandedState.value) "输入 Benchmark 状态：已展开" else "输入 Benchmark 状态：已收起",
                    modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Button(
                    text = if (benchmarkExpandedState.value) "输入 Benchmark 已展开" else "输入 Benchmark 已收起",
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.INPUT_BENCHMARK_TOGGLE)
                        .inputScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                    onClick = {
                        benchmarkExpandedState.value = !benchmarkExpandedState.value
                        benchmarkFieldState.setTextAndPlaceCursorAtEnd(
                            if (benchmarkExpandedState.value) {
                                "展开的 benchmark 数据"
                            } else {
                                "紧凑数据"
                            },
                        )
                    },
                )
                Button(
                    text = "重置输入 Benchmark",
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.INPUT_BENCHMARK_RESET)
                        .inputScenarioTarget(scenario, DemoAutomationRole.Reset),
                    onClick = {
                        benchmarkExpandedState.value = false
                        benchmarkFieldState.setTextAndPlaceCursorAtEnd("紧凑数据")
                        nameState.setTextAndPlaceCursorAtEnd("GZQ")
                        emailState.setTextAndPlaceCursorAtEnd("demo@viewcompose.dev")
                        passwordState.clearText()
                        ageState.setTextAndPlaceCursorAtEnd("3")
                        bioState.setTextAndPlaceCursorAtEnd(
                            "基于虚拟节点、键控 diff 和 Android View 互操作构建。",
                        )
                    },
                )
                TextField(
                    state = benchmarkFieldState,
                    label = "Benchmark 字段",
                    supportingText = if (benchmarkExpandedState.value) {
                        "展开的辅助文案保持场景确定性，同时压力测试 TextField 容器布局。"
                    } else {
                        "紧凑辅助文案。"
                    },
                    readOnly = true,
                    variant = TextFieldVariant.Outlined,
                    size = TextFieldSize.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.INPUT_BENCHMARK_FIELD)
                        .inputScenarioTarget(scenario, DemoAutomationRole.Target),
                )
            }

            "form" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "表单控件",
                subtitle = "所有字段由状态驱动，更新同一个 render session。",
            ) {
                val nameState = requireNotNull(nameState)
                val emailState = requireNotNull(emailState)
                val passwordState = requireNotNull(passwordState)
                val ageState = requireNotNull(ageState)
                val bioState = requireNotNull(bioState)
                val disabledEmailState = requireNotNull(disabledEmailState)
                TextField(
                    state = nameState,
                    hint = "姓名",
                    label = "显示名称",
                    supportingText = "显示在个人资料头部",
                    keyboardOptions = TextFieldKeyboardOptions(
                        imeAction = TextFieldImeAction.Next,
                    ),
                    variant = TextFieldVariant.Filled,
                    size = TextFieldSize.Large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                )
                EmailField(
                    state = emailState,
                    hint = "邮箱",
                    label = "工作邮箱",
                    supportingText = "仅用于通知",
                    keyboardOptions = TextFieldKeyboardOptions(
                        keyboardType = com.viewcompose.ui.node.TextFieldType.Email,
                        imeAction = TextFieldImeAction.Next,
                    ),
                    variant = TextFieldVariant.Tonal,
                    size = TextFieldSize.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                )
                PasswordField(
                    state = passwordState,
                    hint = "密码",
                    label = "访问密钥",
                    supportingText = "留空保持当前密码",
                    keyboardOptions = TextFieldKeyboardOptions(
                        keyboardType = com.viewcompose.ui.node.TextFieldType.Password,
                        imeAction = TextFieldImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                    variant = TextFieldVariant.Outlined,
                    size = TextFieldSize.Medium,
                    isError = passwordState.text.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                )
                NumberField(
                    state = ageState,
                    hint = "版本年龄",
                    label = "项目年龄",
                    supportingText = "语义版本代数",
                    variant = TextFieldVariant.Outlined,
                    size = TextFieldSize.Compact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                )
                EmailField(
                    state = disabledEmailState,
                    hint = "禁用邮箱",
                    label = "只读联系人",
                    supportingText = "从组织设置继承",
                    variant = TextFieldVariant.Tonal,
                    size = TextFieldSize.Medium,
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                )
                TextArea(
                    state = bioState,
                    hint = "简介",
                    label = "摘要",
                    supportingText = "支持多行编辑和本地状态更新",
                    maxLines = 6,
                    keyboardOptions = TextFieldKeyboardOptions(
                        imeAction = TextFieldImeAction.Done,
                    ),
                    variant = TextFieldVariant.Filled,
                    size = TextFieldSize.Large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .margin(bottom = 12.dp),
                )
                Button(
                    text = "重置表单",
                    leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
                    trailingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
                    size = ButtonSize.Large,
                    onClick = {
                        nameState.setTextAndPlaceCursorAtEnd("GZQ")
                        emailState.setTextAndPlaceCursorAtEnd("demo@viewcompose.dev")
                        passwordState.clearText()
                        ageState.setTextAndPlaceCursorAtEnd("3")
                        bioState.setTextAndPlaceCursorAtEnd(
                            "基于虚拟节点、键控 diff 和 Android View 互操作构建。",
                        )
                    },
                )
            }

            "controls" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "选择 + Slider 控件",
                subtitle = "Checkbox、Switch、RadioButton 和 Slider 属于同一声明式输入家族。",
            ) {
                val notificationsEnabledState = requireNotNull(notificationsEnabledState)
                val analyticsEnabledState = requireNotNull(analyticsEnabledState)
                val selectedTierState = requireNotNull(selectedTierState)
                val intensityState = requireNotNull(intensityState)
                Text(
                    text = "选择状态：通知=${notificationsEnabledState.value}, " +
                        "分析=${analyticsEnabledState.value}, 层级=${selectedTierState.value}, " +
                        "强度=${intensityState.value}",
                    modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Row(
                    spacing = 8.dp,
                    modifier = Modifier.margin(bottom = 12.dp),
                ) {
                    Button(
                        text = "切换选择状态",
                        modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                        onClick = {
                            val alternate = notificationsEnabledState.value
                            notificationsEnabledState.value = !alternate
                            analyticsEnabledState.value = alternate
                            selectedTierState.value = if (alternate) "Beta" else "Alpha"
                            intensityState.value = if (alternate) 68 else 32
                        },
                    )
                    Button(
                        text = "重置选择状态",
                        variant = ButtonVariant.Outlined,
                        modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.Reset),
                        onClick = {
                            notificationsEnabledState.value = true
                            analyticsEnabledState.value = false
                            selectedTierState.value = "Alpha"
                            intensityState.value = 32
                        },
                    )
                }
                Checkbox(
                    text = "通知",
                    checked = notificationsEnabledState.value,
                    onCheckedChange = { notificationsEnabledState.value = it },
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .inputScenarioTarget(scenario, DemoAutomationRole.Target),
                )
                Switch(
                    text = "数据分析",
                    checked = analyticsEnabledState.value,
                    onCheckedChange = { analyticsEnabledState.value = it },
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                RadioButton(
                    text = "Alpha 层级",
                    checked = selectedTierState.value == "Alpha",
                    onCheckedChange = { checked -> if (checked) selectedTierState.value = "Alpha" },
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                RadioButton(
                    text = "Beta 层级",
                    checked = selectedTierState.value == "Beta",
                    onCheckedChange = { checked -> if (checked) selectedTierState.value = "Beta" },
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                Text(
                    text = "强度: ${intensityState.value}",
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Slider(
                    value = intensityState.value,
                    min = 0,
                    max = 100,
                    onValueChange = { intensityState.value = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                ProvideCheckboxColors(
                    InputControlColorOverride(
                        control = Theme.colors.secondary,
                        controlDisabled = Theme.colors.outlineVariant,
                        label = Theme.colors.onSurface,
                        labelDisabled = Theme.colors.onSurfaceVariant,
                    ),
                ) {
                ProvideSwitchColors(
                    InputControlColorOverride(
                        control = Theme.colors.secondary,
                        controlDisabled = Theme.colors.outlineVariant,
                        label = Theme.colors.onSurface,
                        labelDisabled = Theme.colors.onSurfaceVariant,
                    ),
                ) {
                ProvideRadioButtonColors(
                    InputControlColorOverride(
                        control = Theme.colors.secondary,
                        controlDisabled = Theme.colors.outlineVariant,
                        label = Theme.colors.onSurface,
                        labelDisabled = Theme.colors.onSurfaceVariant,
                    ),
                ) {
                ProvideSliderColors(
                    InputControlColorOverride(
                        control = Theme.colors.secondary,
                        controlDisabled = Theme.colors.outlineVariant,
                    ),
                ) {
                    Column(
                        spacing = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .backgroundColor(SurfaceDefaults.backgroundColor())
                            .shape(SurfaceDefaults.shape())
                            .padding(12.dp),
                    ) {
                        Text(text = "输入控件颜色覆盖")
                        Checkbox(text = "本地 Accent Checkbox", checked = true, onCheckedChange = {})
                        Switch(text = "禁用 Accent Switch", checked = false, enabled = false, onCheckedChange = {})
                        RadioButton(text = "本地 Accent Radio", checked = true, onCheckedChange = {})
                        Slider(value = 56, min = 0, max = 100, enabled = false, onValueChange = {}, modifier = Modifier.fillMaxWidth())
                    }
                }
                }
                }
                }
            }

            "stress" -> ScenarioSection(
                kind = ScenarioKind.Stress,
                title = "输入边界用例",
                subtitle = "长标签、只读文本、多行增长和持久错误样式的压力测试。",
            ) {
                val stressExpandedState = requireNotNull(stressExpandedState)
                val stressReadonlyState = requireNotNull(stressReadonlyState)
                val stressErrorState = requireNotNull(stressErrorState)
                val stressTitleFieldState = requireNotNull(stressTitleFieldState)
                val stressNotesFieldState = requireNotNull(stressNotesFieldState)
                val stressPasswordFieldState = requireNotNull(stressPasswordFieldState)
                Text(
                    text = "压力状态：展开=${stressExpandedState.value}, " +
                        "只读=${stressReadonlyState.value}, 错误=${stressErrorState.value}",
                    modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Row(
                    spacing = 8.dp,
                    modifier = Modifier.margin(bottom = 12.dp),
                ) {
                    Button(
                        text = if (stressExpandedState.value) "紧凑文案" else "展开文案",
                        size = ButtonSize.Compact,
                        modifier = Modifier
                            .testTag(DemoTestTags.INPUT_STRESS_EXPAND)
                            .inputScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                        onClick = {
                            stressExpandedState.value = !stressExpandedState.value
                            stressTitleFieldState.setTextAndPlaceCursorAtEnd(
                                if (stressExpandedState.value) {
                                    "一个很长的项目标题，应该仍然保持标签、占位符和辅助文案可读而不被裁切。"
                                } else {
                                    "紧凑标题"
                                },
                            )
                            stressNotesFieldState.setTextAndPlaceCursorAtEnd(
                                if (stressExpandedState.value) {
                                    "只读压力笔记:\n" +
                                        "- 本地主题覆盖保持活跃\n" +
                                        "- 多行容器应保持 padding 稳定\n" +
                                        "- 长文案不应把辅助文本推出卡片"
                                } else {
                                    "只读笔记"
                                },
                            )
                        },
                    )
                    Button(
                        text = if (stressReadonlyState.value) "可编辑" else "只读",
                        size = ButtonSize.Compact,
                        variant = ButtonVariant.Outlined,
                        modifier = Modifier
                            .testTag(DemoTestTags.INPUT_STRESS_READONLY)
                            .inputScenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
                        onClick = { stressReadonlyState.value = !stressReadonlyState.value },
                    )
                    Button(
                        text = if (stressErrorState.value) "清除错误" else "显示错误",
                        size = ButtonSize.Compact,
                        variant = ButtonVariant.Tonal,
                        modifier = Modifier.testTag(DemoTestTags.INPUT_STRESS_ERROR),
                        onClick = {
                            stressErrorState.value = !stressErrorState.value
                            if (stressErrorState.value) {
                                stressPasswordFieldState.clearText()
                            } else {
                                stressPasswordFieldState.setTextAndPlaceCursorAtEnd("stable-password")
                            }
                        },
                    )
                }
                Button(
                    text = "重置输入压力测试",
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp)
                        .inputScenarioTarget(scenario, DemoAutomationRole.Reset),
                    onClick = {
                        stressExpandedState.value = false
                        stressReadonlyState.value = true
                        stressErrorState.value = true
                        stressTitleFieldState.setTextAndPlaceCursorAtEnd("紧凑标题")
                        stressNotesFieldState.setTextAndPlaceCursorAtEnd("只读笔记")
                        stressPasswordFieldState.clearText()
                    },
                )
                TextField(
                    state = stressTitleFieldState,
                    readOnly = true,
                    label = "发布渠道显示名称",
                    supportingText = if (stressExpandedState.value) {
                        "长辅助文案应该整齐换行，并与字段容器保持对齐。"
                    } else {
                        "短辅助文案"
                    },
                    variant = TextFieldVariant.Outlined,
                    size = TextFieldSize.Large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                )
                TextArea(
                    state = stressNotesFieldState,
                    label = "审阅者笔记",
                    supportingText = "切换只读和展开文案检查多行稳定性。",
                    readOnly = stressReadonlyState.value,
                    maxLines = 6,
                    variant = TextFieldVariant.Tonal,
                    size = TextFieldSize.Large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .margin(bottom = 12.dp),
                )
                PasswordField(
                    state = stressPasswordFieldState,
                    label = "受保护字段",
                    supportingText = if (stressErrorState.value) {
                        "错误态必须在主题切换和页面变化中保持可见。"
                    } else {
                        "解决态应恢复标准主题样式。"
                    },
                    isError = stressErrorState.value,
                    variant = TextFieldVariant.Filled,
                    size = TextFieldSize.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.INPUT_STRESS_PROTECTED_FIELD)
                        .inputScenarioTarget(scenario, DemoAutomationRole.Target),
                )
            }

            "search" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "SearchBar 搜索栏",
                subtitle = "SearchBar 提供搜索输入框，支持 query 绑定、onSearch 回调和清除按钮。",
            ) {
                val searchQueryState = requireNotNull(searchQueryState)
                val searchHistoryState = requireNotNull(searchHistoryState)
                val disabledSearchState = requireNotNull(disabledSearchState)
                val searchResultState = requireNotNull(searchResultState)
                val scrollableSearchQueryState = requireNotNull(scrollableSearchQueryState)
                val verticalPagerSearchQueryState = requireNotNull(verticalPagerSearchQueryState)
                val pullRefreshSearchQueryState = requireNotNull(pullRefreshSearchQueryState)
                val focusFollowVerticalPagerPageState = requireNotNull(focusFollowVerticalPagerPageState)
                val pullRefreshFocusRefreshingState = requireNotNull(pullRefreshFocusRefreshingState)
                Text(
                    text = if (searchQueryState.text.isBlank()) {
                        "搜索状态：空闲"
                    } else {
                        "搜索状态：${searchQueryState.text}"
                    },
                    modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Row(
                    spacing = 8.dp,
                    modifier = Modifier.margin(bottom = 12.dp),
                ) {
                    Button(
                        text = "填充搜索场景",
                        modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                        onClick = {
                            searchQueryState.setTextAndPlaceCursorAtEnd("ViewCompose")
                            searchResultState.value = "搜索: ViewCompose"
                        },
                    )
                    Button(
                        text = "重置搜索场景",
                        variant = ButtonVariant.Outlined,
                        modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.Reset),
                        onClick = {
                            searchQueryState.clearText()
                            searchHistoryState.clearText()
                            disabledSearchState.clearText()
                            searchResultState.value = ""
                            scrollableSearchQueryState.clearText()
                            verticalPagerSearchQueryState.clearText()
                            pullRefreshSearchQueryState.clearText()
                            focusFollowVerticalPagerPageState.value = 0
                            pullRefreshFocusRefreshingState.value = false
                        },
                    )
                }
                Text(
                    text = "基础搜索栏",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                SearchBar(
                    state = searchQueryState,
                    onSearch = { query -> searchResultState.value = "搜索: $query" },
                    placeholder = "搜索商品…",
                    leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp)
                        .testTag(DemoTestTags.INPUT_SEARCH_PRIMARY)
                        .inputScenarioTarget(scenario, DemoAutomationRole.Target),
                )
                if (searchResultState.value.isNotEmpty()) {
                    Text(
                        text = searchResultState.value,
                        style = UiTextStyle(fontSizeSp = 13.sp),
                        color = TextDefaults.secondaryColor(),
                        modifier = Modifier.margin(bottom = 12.dp),
                    )
                }
                Text(
                    text = "带清除按钮的搜索栏",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                SearchBar(
                    state = searchHistoryState,
                    onSearch = { query -> searchResultState.value = "搜索: $query" },
                    placeholder = "搜索历史…",
                    leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
                    trailingIcon = {
                        if (searchHistoryState.text.isNotEmpty()) {
                            IconButton(
                                icon = ImageSource.Resource(R.drawable.demo_media_icon),
                                contentDescription = "清除",
                                onClick = searchHistoryState::clearText,
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                )
                Text(
                    text = "禁用搜索栏",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                SearchBar(
                    state = disabledSearchState,
                    placeholder = "搜索不可用",
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "ScrollableColumn + focusFollowKeyboard",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(top = 12.dp, bottom = 8.dp),
                )
                ScrollableColumn(
                    spacing = 8.dp,
                    focusFollowKeyboard = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .margin(bottom = 12.dp),
                ) {
                    Text(
                        text = "滚动容器内聚焦输入框时，焦点跟随策略应只影响当前垂直容器。",
                        style = UiTextStyle(fontSizeSp = 13.sp),
                        color = TextDefaults.secondaryColor(),
                    )
                    SearchBar(
                        state = scrollableSearchQueryState,
                        placeholder = "ScrollableColumn 内搜索…",
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(DemoTestTags.INPUT_FOCUS_SCROLLABLE_SEARCH),
                    )
                    (1..4).forEach { index ->
                        Text(
                            text = "滚动占位行 $index",
                            style = UiTextStyle(fontSizeSp = 13.sp),
                            color = TextDefaults.secondaryColor(),
                        )
                    }
                }
                Text(
                    text = "VerticalPager + focusFollowKeyboard",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                VerticalPager(
                    currentPage = focusFollowVerticalPagerPageState.value,
                    onPageChanged = { focusFollowVerticalPagerPageState.value = it },
                    focusFollowKeyboard = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp)
                        .margin(bottom = 12.dp),
                ) {
                    Page(key = "focus-follow-vertical-pager-search", contentRevision = "focus-follow-vertical-pager-search") {
                        Column(
                            spacing = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                                .shape(SurfaceDefaults.shape())
                                .padding(12.dp),
                        ) {
                            Text(
                                text = "第一页用于回归输入框焦点跟随。",
                                style = UiTextStyle(fontSizeSp = 13.sp),
                                color = TextDefaults.secondaryColor(),
                            )
                            SearchBar(
                                state = verticalPagerSearchQueryState,
                                placeholder = "VerticalPager 页内搜索…",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(DemoTestTags.INPUT_FOCUS_VERTICAL_PAGER_SEARCH),
                            )
                        }
                    }
                    Page(key = "focus-follow-vertical-pager-note", contentRevision = "focus-follow-vertical-pager-note") {
                        Column(
                            spacing = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                                .shape(SurfaceDefaults.shape())
                                .padding(12.dp),
                        ) {
                            Text(text = "第二页用于手动切换验证")
                            Text(
                                text = "切换页面后再聚焦输入框，应保持 page 内可见区域稳定。",
                                style = UiTextStyle(fontSizeSp = 13.sp),
                                color = TextDefaults.secondaryColor(),
                            )
                        }
                    }
                }
                Text(
                    text = "PullToRefresh 子容器 focus follow",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                PullToRefresh(
                    isRefreshing = pullRefreshFocusRefreshingState.value,
                    onRefresh = { pullRefreshFocusRefreshingState.value = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp)
                        .margin(bottom = 8.dp),
                ) {
                    ScrollableColumn(
                        spacing = 8.dp,
                        focusFollowKeyboard = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        SearchBar(
                            state = pullRefreshSearchQueryState,
                            placeholder = "PullToRefresh 子容器搜索…",
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(DemoTestTags.INPUT_FOCUS_PULL_REFRESH_SEARCH),
                        )
                        Button(
                            text = if (pullRefreshFocusRefreshingState.value) "停止刷新" else "模拟刷新",
                            variant = ButtonVariant.Outlined,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                pullRefreshFocusRefreshingState.value = !pullRefreshFocusRefreshingState.value
                            },
                        )
                        Text(
                            text = "PullToRefresh 容器本身不处理 focus follow，行为由内部 ScrollableColumn 负责。",
                            style = UiTextStyle(fontSizeSp = 13.sp),
                            color = TextDefaults.secondaryColor(),
                        )
                    }
                }
                Text(
                    text = "聚焦以上输入框后，外层列表锚点不应跳变到顶部。",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                )
            }

            "summary" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = "派生摘要",
                subtitle = "此区域由 derivedStateOf 驱动，非命令式重复更新。",
            ) {
                val summaryAlternateState = requireNotNull(summaryAlternateState)
                val summaryState = requireNotNull(summaryState)
                Text(
                    text = if (summaryAlternateState.value) "派生状态：备选" else "派生状态：默认",
                    modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Text(
                    text = summaryState.value,
                    modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.Target),
                )
                Row(
                    spacing = 8.dp,
                    modifier = Modifier.margin(top = 12.dp),
                ) {
                    Button(
                        text = "切换派生输入",
                        modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                        onClick = { summaryAlternateState.value = !summaryAlternateState.value },
                    )
                    Button(
                        text = "重置派生输入",
                        variant = ButtonVariant.Outlined,
                        modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.Reset),
                        onClick = { summaryAlternateState.value = false },
                    )
                }
            }

            else -> error("Unsupported input section: $section")
        }
    }
}

private fun Modifier.inputScenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier {
    val target = scenario?.automation?.get(role) ?: return this
    return demoAutomationTarget(target)
}
