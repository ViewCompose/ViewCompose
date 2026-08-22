package com.viewcompose.demo.registry

import android.content.Context
import android.content.Intent
import com.viewcompose.ActionsActivity
import com.viewcompose.AnimationActivity
import com.viewcompose.FeedbackActivity
import com.viewcompose.FoundationsActivity
import com.viewcompose.GesturesActivity
import com.viewcompose.GraphicsActivity
import com.viewcompose.CollectionsActivity
import com.viewcompose.ComponentShowcaseActivity
import com.viewcompose.DiagnosticsActivity
import com.viewcompose.LayoutsActivity
import com.viewcompose.ModifiersActivity
import com.viewcompose.NavigationActivity
import com.viewcompose.InputActivity
import com.viewcompose.InteropActivity
import com.viewcompose.R
import com.viewcompose.ResourceConfigurationActivity
import com.viewcompose.StateActivity
import com.viewcompose.SystemNavigationActivity
import com.viewcompose.ThemeSwitchActivity
import com.viewcompose.Material3DefaultThemeActivity
import com.viewcompose.DemoDesignSystemVerificationActivity
import com.viewcompose.OneUi7VerificationActivity
import com.viewcompose.demo.contract.DemoAutomationContract
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoBenchmarkContract
import com.viewcompose.demo.contract.DemoHostPolicy
import com.viewcompose.demo.contract.DemoRouteExtra
import com.viewcompose.demo.contract.DemoScenarioCategory
import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.contract.DemoScenarioRoute
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.demo.contract.DemoVerificationKind
import com.viewcompose.demo.contract.EXTRA_DEMO_SCENARIO_ID
import com.viewcompose.performance.EXTRA_PERFORMANCE_ENGINE
import com.viewcompose.performance.EXTRA_PERFORMANCE_SCENARIO
import com.viewcompose.performance.PerformanceComparisonActivity

internal object DemoScenarioIds {
    val RuntimeState = DemoScenarioId("runtime.state")
    val RuntimeKeyIdentity = DemoScenarioId("runtime.key-identity")
    val RuntimeViewPatch = DemoScenarioId("runtime.view-patch")
    val InputFields = DemoScenarioId("input.fields")
    val InputSelection = DemoScenarioId("input.selection")
    val InputStress = DemoScenarioId("input.stress")
    val InputSearch = DemoScenarioId("input.search")
    val InputFocusFollowLazyColumn = DemoScenarioId("input.focus-follow-lazy-column")
    val InputFocusFollowLazyGrid = DemoScenarioId("input.focus-follow-lazy-grid")
    val InputFocusFollowScrollableColumn = DemoScenarioId("input.focus-follow-scrollable-column")
    val InputFocusFollowVerticalPager = DemoScenarioId("input.focus-follow-vertical-pager")
    val InputFocusFollowPullRefresh = DemoScenarioId("input.focus-follow-pull-refresh")
    val InputDerivedSummary = DemoScenarioId("input.derived-summary")
    val GestureTap = DemoScenarioId("gesture.tap")
    val GestureDragSwipe = DemoScenarioId("gesture.drag-swipe")
    val GestureTransform = DemoScenarioId("gesture.transform")
    val GraphicsDrawing = DemoScenarioId("graphics.drawing")
    val GraphicsOuterShadow = DemoScenarioId("graphics.outer-shadow")
    val GraphicsInnerShadow = DemoScenarioId("graphics.inner-shadow")
    val GraphicsShadowList = DemoScenarioId("graphics.shadow-list")
    val AnimationCore = DemoScenarioId("animation.core")
    val AnimationContent = DemoScenarioId("animation.content")
    val AnimationListMotion = DemoScenarioId("animation.list-motion")
    val AnimationSpecs = DemoScenarioId("animation.specs")
    val AnimationContentSize = DemoScenarioId("animation.content-size")
    val AnimationTransition = DemoScenarioId("animation.transition")
    val AnimationInfinite = DemoScenarioId("animation.infinite")
    val ModifierVisual = DemoScenarioId("modifier.visual")
    val ModifierSizing = DemoScenarioId("modifier.sizing")
    val ModifierAccessibility = DemoScenarioId("modifier.accessibility")
    val DiagnosticsRuntime = DemoScenarioId("diagnostics.runtime")
    val DiagnosticsTheme = DemoScenarioId("diagnostics.theme")
    val DiagnosticsRenderer = DemoScenarioId("diagnostics.renderer")
    val CollectionControls = DemoScenarioId("collection.controls")
    val CollectionLazyList = DemoScenarioId("collection.lazy-list")
    val CollectionStress = DemoScenarioId("collection.stress")
    val CollectionAndroidView = DemoScenarioId("collection.android-view")
    val CollectionLazyRow = DemoScenarioId("collection.lazy-row")
    val CollectionGrid = DemoScenarioId("collection.grid")
    val CollectionPullRefresh = DemoScenarioId("collection.pull-refresh")
    val CollectionNestedLazyList = DemoScenarioId("collection.nested-lazy-list")
    val LayoutLinear = DemoScenarioId("layout.linear")
    val LayoutStack = DemoScenarioId("layout.stack")
    val LayoutEdges = DemoScenarioId("layout.edges")
    val LayoutFlow = DemoScenarioId("layout.flow")
    val LayoutScroll = DemoScenarioId("layout.scroll")
    val LayoutConstraint = DemoScenarioId("layout.constraint")
    val EnvironmentResources = DemoScenarioId("environment.resources")
    val EnvironmentCrossActivityTheme = DemoScenarioId("environment.cross-activity-theme")
    val InteropAndroidView = DemoScenarioId("interop.android-view")
    val OverlayTransient = DemoScenarioId("overlay.transient")
    val OverlayDialog = DemoScenarioId("overlay.dialog")
    val OverlayMenu = DemoScenarioId("overlay.menu")
    val NavigationSystem = DemoScenarioId("navigation.system")
    val DesignMaterial3Xml = DemoScenarioId("design.material3-xml")
    val DesignMaterial3Static = DemoScenarioId("design.material3-static")
    val DesignMaterial3Custom = DemoScenarioId("design.material3-custom")
    val DesignBundleMaterial3 = DemoScenarioId("design.bundle-material3")
    val DesignBundleContrast = DemoScenarioId("design.bundle-contrast")
    val DesignOneUi7 = DemoScenarioId("design.oneui7")
    val ComponentCard = DemoScenarioId("component.card")
    val ComponentFab = DemoScenarioId("component.fab")
    val ComponentChip = DemoScenarioId("component.chip")
    val ComponentListItem = DemoScenarioId("component.list-item")
    val ComponentAppBars = DemoScenarioId("component.app-bars")
    val ComponentNavigationBar = DemoScenarioId("component.navigation-bar")
    val ComponentScaffold = DemoScenarioId("component.scaffold")
    val ComponentButton = DemoScenarioId("component.button")
    val ComponentIconButton = DemoScenarioId("component.icon-button")
    val ComponentSegmentedControl = DemoScenarioId("component.segmented-control")
    val ComponentDivider = DemoScenarioId("component.divider")
    val ComponentProgress = DemoScenarioId("component.progress")
    val FoundationsLocals = DemoScenarioId("foundations.locals")
    val FoundationsTheme = DemoScenarioId("foundations.theme")
    val FoundationsMedia = DemoScenarioId("foundations.media")
    val FoundationsTypography = DemoScenarioId("foundations.typography")
    val PerformanceList = DemoScenarioId("performance.list")
    val PerformanceComplexLayout = DemoScenarioId("performance.complex-layout")
    val PerformanceShadowList = DemoScenarioId("performance.shadow-list")
    val PerformanceShadowComplexLayout = DemoScenarioId("performance.shadow-complex-layout")
}

internal object DemoScenarioRegistry {
    private val scenarios: List<DemoScenarioSpec> = listOf(
        scenario(
            id = DemoScenarioIds.RuntimeState,
            category = DemoScenarioCategory.Runtime,
            titleRes = R.string.demo_scenario_runtime_state_title,
            summaryRes = R.string.demo_scenario_runtime_state_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Benchmark),
            route = DemoScenarioRoute(StateActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_runtime_state_root,
                ready = R.id.demo_runtime_state_ready,
                primaryAction = R.id.demo_runtime_state_primary_action,
                reset = R.id.demo_runtime_state_reset,
                state = R.id.demo_runtime_state_state,
                target = R.id.demo_runtime_state_target,
            ),
            benchmarkRevision = 2,
        ),
        scenario(
            id = DemoScenarioIds.RuntimeKeyIdentity,
            category = DemoScenarioCategory.Runtime,
            titleRes = R.string.demo_scenario_runtime_key_identity_title,
            summaryRes = R.string.demo_scenario_runtime_key_identity_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual),
            route = DemoScenarioRoute(StateActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_runtime_key_identity_root,
                ready = R.id.demo_runtime_key_identity_ready,
                primaryAction = R.id.demo_runtime_key_identity_primary_action,
                reset = R.id.demo_runtime_key_identity_reset,
                state = R.id.demo_runtime_key_identity_state,
                target = R.id.demo_runtime_key_identity_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.RuntimeViewPatch,
            category = DemoScenarioCategory.Runtime,
            titleRes = R.string.demo_scenario_runtime_view_patch_title,
            summaryRes = R.string.demo_scenario_runtime_view_patch_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Benchmark),
            route = DemoScenarioRoute(StateActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_runtime_view_patch_root,
                ready = R.id.demo_runtime_view_patch_ready,
                primaryAction = R.id.demo_runtime_view_patch_primary_action,
                reset = R.id.demo_runtime_view_patch_reset,
                state = R.id.demo_runtime_view_patch_state,
                target = R.id.demo_runtime_view_patch_target,
            ),
            benchmarkRevision = 2,
        ),
        scenario(
            id = DemoScenarioIds.InputFields,
            category = DemoScenarioCategory.Input,
            titleRes = R.string.demo_scenario_input_fields_title,
            summaryRes = R.string.demo_scenario_input_fields_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual, DemoVerificationKind.Benchmark),
            route = DemoScenarioRoute(InputActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_input_fields_root,
                ready = R.id.demo_input_fields_ready,
                primaryAction = R.id.demo_input_fields_primary_action,
                reset = R.id.demo_input_fields_reset,
                state = R.id.demo_input_fields_state,
                target = R.id.demo_input_fields_target,
            ),
            benchmarkRevision = 2,
        ),
        scenario(
            id = DemoScenarioIds.InputSelection,
            category = DemoScenarioCategory.Input,
            titleRes = R.string.demo_scenario_input_selection_title,
            summaryRes = R.string.demo_scenario_input_selection_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(InputActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_input_selection_root,
                ready = R.id.demo_input_selection_ready,
                primaryAction = R.id.demo_input_selection_primary_action,
                reset = R.id.demo_input_selection_reset,
                state = R.id.demo_input_selection_state,
                target = R.id.demo_input_selection_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.InputStress,
            category = DemoScenarioCategory.Input,
            titleRes = R.string.demo_scenario_input_stress_title,
            summaryRes = R.string.demo_scenario_input_stress_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(InputActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_input_stress_root,
                ready = R.id.demo_input_stress_ready,
                primaryAction = R.id.demo_input_stress_primary_action,
                secondaryAction = R.id.demo_input_stress_secondary_action,
                reset = R.id.demo_input_stress_reset,
                state = R.id.demo_input_stress_state,
                target = R.id.demo_input_stress_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.InputSearch,
            category = DemoScenarioCategory.Input,
            titleRes = R.string.demo_scenario_input_search_title,
            summaryRes = R.string.demo_scenario_input_search_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(InputActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_input_search_root,
                ready = R.id.demo_input_search_ready,
                primaryAction = R.id.demo_input_search_primary_action,
                reset = R.id.demo_input_search_reset,
                state = R.id.demo_input_search_state,
                target = R.id.demo_input_search_target,
            ),
        ),
        inputFocusFollowScenario(
            id = DemoScenarioIds.InputFocusFollowLazyColumn,
            titleRes = R.string.demo_scenario_input_focus_follow_lazy_column_title,
            summaryRes = R.string.demo_scenario_input_focus_follow_lazy_column_summary,
            root = R.id.demo_input_focus_follow_lazy_column_root,
            ready = R.id.demo_input_focus_follow_lazy_column_ready,
            primaryAction = R.id.demo_input_focus_follow_lazy_column_primary_action,
            reset = R.id.demo_input_focus_follow_lazy_column_reset,
            state = R.id.demo_input_focus_follow_lazy_column_state,
            target = R.id.demo_input_focus_follow_lazy_column_target,
        ),
        inputFocusFollowScenario(
            id = DemoScenarioIds.InputFocusFollowLazyGrid,
            titleRes = R.string.demo_scenario_input_focus_follow_lazy_grid_title,
            summaryRes = R.string.demo_scenario_input_focus_follow_lazy_grid_summary,
            root = R.id.demo_input_focus_follow_lazy_grid_root,
            ready = R.id.demo_input_focus_follow_lazy_grid_ready,
            primaryAction = R.id.demo_input_focus_follow_lazy_grid_primary_action,
            reset = R.id.demo_input_focus_follow_lazy_grid_reset,
            state = R.id.demo_input_focus_follow_lazy_grid_state,
            target = R.id.demo_input_focus_follow_lazy_grid_target,
        ),
        inputFocusFollowScenario(
            id = DemoScenarioIds.InputFocusFollowScrollableColumn,
            titleRes = R.string.demo_scenario_input_focus_follow_scrollable_column_title,
            summaryRes = R.string.demo_scenario_input_focus_follow_scrollable_column_summary,
            root = R.id.demo_input_focus_follow_scrollable_column_root,
            ready = R.id.demo_input_focus_follow_scrollable_column_ready,
            primaryAction = R.id.demo_input_focus_follow_scrollable_column_primary_action,
            reset = R.id.demo_input_focus_follow_scrollable_column_reset,
            state = R.id.demo_input_focus_follow_scrollable_column_state,
            target = R.id.demo_input_focus_follow_scrollable_column_target,
        ),
        inputFocusFollowScenario(
            id = DemoScenarioIds.InputFocusFollowVerticalPager,
            titleRes = R.string.demo_scenario_input_focus_follow_vertical_pager_title,
            summaryRes = R.string.demo_scenario_input_focus_follow_vertical_pager_summary,
            root = R.id.demo_input_focus_follow_vertical_pager_root,
            ready = R.id.demo_input_focus_follow_vertical_pager_ready,
            primaryAction = R.id.demo_input_focus_follow_vertical_pager_primary_action,
            reset = R.id.demo_input_focus_follow_vertical_pager_reset,
            state = R.id.demo_input_focus_follow_vertical_pager_state,
            target = R.id.demo_input_focus_follow_vertical_pager_target,
        ),
        inputFocusFollowScenario(
            id = DemoScenarioIds.InputFocusFollowPullRefresh,
            titleRes = R.string.demo_scenario_input_focus_follow_pull_refresh_title,
            summaryRes = R.string.demo_scenario_input_focus_follow_pull_refresh_summary,
            root = R.id.demo_input_focus_follow_pull_refresh_root,
            ready = R.id.demo_input_focus_follow_pull_refresh_ready,
            primaryAction = R.id.demo_input_focus_follow_pull_refresh_primary_action,
            reset = R.id.demo_input_focus_follow_pull_refresh_reset,
            state = R.id.demo_input_focus_follow_pull_refresh_state,
            target = R.id.demo_input_focus_follow_pull_refresh_target,
        ),
        scenario(
            id = DemoScenarioIds.InputDerivedSummary,
            category = DemoScenarioCategory.Input,
            titleRes = R.string.demo_scenario_input_derived_summary_title,
            summaryRes = R.string.demo_scenario_input_derived_summary_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(
                DemoVerificationKind.Manual,
                DemoVerificationKind.Benchmark,
            ),
            route = DemoScenarioRoute(InputActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_input_derived_summary_root,
                ready = R.id.demo_input_derived_summary_ready,
                primaryAction = R.id.demo_input_derived_summary_primary_action,
                reset = R.id.demo_input_derived_summary_reset,
                state = R.id.demo_input_derived_summary_state,
                target = R.id.demo_input_derived_summary_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.GestureTap,
            category = DemoScenarioCategory.Input,
            titleRes = R.string.demo_scenario_gesture_tap_title,
            summaryRes = R.string.demo_scenario_gesture_tap_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(GesturesActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_gesture_tap_root,
                ready = R.id.demo_gesture_tap_ready,
                primaryAction = R.id.demo_gesture_tap_primary_action,
                reset = R.id.demo_gesture_tap_reset,
                state = R.id.demo_gesture_tap_state,
                target = R.id.demo_gesture_tap_target,
                secondaryTarget = R.id.demo_gesture_tap_secondary_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.GestureDragSwipe,
            category = DemoScenarioCategory.Input,
            titleRes = R.string.demo_scenario_gesture_drag_swipe_title,
            summaryRes = R.string.demo_scenario_gesture_drag_swipe_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(GesturesActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_gesture_drag_swipe_root,
                ready = R.id.demo_gesture_drag_swipe_ready,
                primaryAction = R.id.demo_gesture_drag_swipe_primary_action,
                reset = R.id.demo_gesture_drag_swipe_reset,
                state = R.id.demo_gesture_drag_swipe_state,
                target = R.id.demo_gesture_drag_swipe_target,
                secondaryTarget = R.id.demo_gesture_drag_swipe_secondary_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.GestureTransform,
            category = DemoScenarioCategory.Input,
            titleRes = R.string.demo_scenario_gesture_transform_title,
            summaryRes = R.string.demo_scenario_gesture_transform_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(GesturesActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_gesture_transform_root,
                ready = R.id.demo_gesture_transform_ready,
                primaryAction = R.id.demo_gesture_transform_primary_action,
                reset = R.id.demo_gesture_transform_reset,
                state = R.id.demo_gesture_transform_state,
                target = R.id.demo_gesture_transform_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.GraphicsDrawing,
            category = DemoScenarioCategory.Rendering,
            titleRes = R.string.demo_scenario_graphics_drawing_title,
            summaryRes = R.string.demo_scenario_graphics_drawing_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(GraphicsActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_graphics_drawing_root,
                ready = R.id.demo_graphics_drawing_ready,
                primaryAction = R.id.demo_graphics_drawing_primary_action,
                reset = R.id.demo_graphics_drawing_reset,
                state = R.id.demo_graphics_drawing_state,
                target = R.id.demo_graphics_drawing_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.GraphicsOuterShadow,
            category = DemoScenarioCategory.Rendering,
            titleRes = R.string.demo_scenario_graphics_outer_shadow_title,
            summaryRes = R.string.demo_scenario_graphics_outer_shadow_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(GraphicsActivity::class.java),
            mutable = false,
            ids = TargetIds(
                root = R.id.demo_graphics_outer_shadow_root,
                ready = R.id.demo_graphics_outer_shadow_ready,
                target = R.id.demo_graphics_outer_shadow_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.GraphicsInnerShadow,
            category = DemoScenarioCategory.Rendering,
            titleRes = R.string.demo_scenario_graphics_inner_shadow_title,
            summaryRes = R.string.demo_scenario_graphics_inner_shadow_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(GraphicsActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_graphics_inner_shadow_root,
                ready = R.id.demo_graphics_inner_shadow_ready,
                primaryAction = R.id.demo_graphics_inner_shadow_primary_action,
                reset = R.id.demo_graphics_inner_shadow_reset,
                state = R.id.demo_graphics_inner_shadow_state,
                target = R.id.demo_graphics_inner_shadow_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.GraphicsShadowList,
            category = DemoScenarioCategory.Rendering,
            titleRes = R.string.demo_scenario_graphics_shadow_list_title,
            summaryRes = R.string.demo_scenario_graphics_shadow_list_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(GraphicsActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_graphics_shadow_list_root,
                ready = R.id.demo_graphics_shadow_list_ready,
                primaryAction = R.id.demo_graphics_shadow_list_primary_action,
                secondaryAction = R.id.demo_graphics_shadow_list_secondary_action,
                reset = R.id.demo_graphics_shadow_list_reset,
                state = R.id.demo_graphics_shadow_list_state,
                target = R.id.demo_graphics_shadow_list_target,
            ),
        ),
        animationScenario(
            id = DemoScenarioIds.AnimationCore,
            titleRes = R.string.demo_scenario_animation_core_title,
            summaryRes = R.string.demo_scenario_animation_core_summary,
            root = R.id.demo_animation_core_root,
            ready = R.id.demo_animation_core_ready,
            primaryAction = R.id.demo_animation_core_primary_action,
            secondaryAction = R.id.demo_animation_core_secondary_action,
            reset = R.id.demo_animation_core_reset,
            state = R.id.demo_animation_core_state,
            target = R.id.demo_animation_core_target,
            benchmarkRevision = 3,
            benchmarkActions = listOf(DemoAutomationRole.PrimaryAction),
        ),
        animationScenario(
            id = DemoScenarioIds.AnimationContent,
            titleRes = R.string.demo_scenario_animation_content_title,
            summaryRes = R.string.demo_scenario_animation_content_summary,
            root = R.id.demo_animation_content_root,
            ready = R.id.demo_animation_content_ready,
            primaryAction = R.id.demo_animation_content_primary_action,
            secondaryAction = R.id.demo_animation_content_secondary_action,
            reset = R.id.demo_animation_content_reset,
            state = R.id.demo_animation_content_state,
            target = R.id.demo_animation_content_target,
            benchmarkRevision = 2,
            benchmarkActions = listOf(
                DemoAutomationRole.PrimaryAction,
                DemoAutomationRole.SecondaryAction,
            ),
        ),
        animationScenario(
            id = DemoScenarioIds.AnimationListMotion,
            titleRes = R.string.demo_scenario_animation_list_motion_title,
            summaryRes = R.string.demo_scenario_animation_list_motion_summary,
            root = R.id.demo_animation_list_motion_root,
            ready = R.id.demo_animation_list_motion_ready,
            primaryAction = R.id.demo_animation_list_motion_primary_action,
            secondaryAction = R.id.demo_animation_list_motion_secondary_action,
            reset = R.id.demo_animation_list_motion_reset,
            state = R.id.demo_animation_list_motion_state,
            target = R.id.demo_animation_list_motion_target,
        ),
        animationScenario(
            id = DemoScenarioIds.AnimationSpecs,
            titleRes = R.string.demo_scenario_animation_specs_title,
            summaryRes = R.string.demo_scenario_animation_specs_summary,
            root = R.id.demo_animation_specs_root,
            ready = R.id.demo_animation_specs_ready,
            primaryAction = R.id.demo_animation_specs_primary_action,
            secondaryAction = R.id.demo_animation_specs_secondary_action,
            reset = R.id.demo_animation_specs_reset,
            state = R.id.demo_animation_specs_state,
            target = R.id.demo_animation_specs_target,
            benchmarkRevision = 1,
            benchmarkActions = listOf(
                DemoAutomationRole.SecondaryAction,
                DemoAutomationRole.PrimaryAction,
            ),
        ),
        animationScenario(
            id = DemoScenarioIds.AnimationContentSize,
            titleRes = R.string.demo_scenario_animation_content_size_title,
            summaryRes = R.string.demo_scenario_animation_content_size_summary,
            root = R.id.demo_animation_content_size_root,
            ready = R.id.demo_animation_content_size_ready,
            primaryAction = R.id.demo_animation_content_size_primary_action,
            secondaryAction = R.id.demo_animation_content_size_secondary_action,
            reset = R.id.demo_animation_content_size_reset,
            state = R.id.demo_animation_content_size_state,
            target = R.id.demo_animation_content_size_target,
            benchmarkRevision = 1,
            benchmarkActions = listOf(
                DemoAutomationRole.PrimaryAction,
                DemoAutomationRole.SecondaryAction,
            ),
        ),
        animationScenario(
            id = DemoScenarioIds.AnimationTransition,
            titleRes = R.string.demo_scenario_animation_transition_title,
            summaryRes = R.string.demo_scenario_animation_transition_summary,
            root = R.id.demo_animation_transition_root,
            ready = R.id.demo_animation_transition_ready,
            primaryAction = R.id.demo_animation_transition_primary_action,
            secondaryAction = R.id.demo_animation_transition_secondary_action,
            reset = R.id.demo_animation_transition_reset,
            state = R.id.demo_animation_transition_state,
            target = R.id.demo_animation_transition_target,
            benchmarkRevision = 2,
            benchmarkActions = listOf(
                DemoAutomationRole.PrimaryAction,
                DemoAutomationRole.SecondaryAction,
            ),
        ),
        animationScenario(
            id = DemoScenarioIds.AnimationInfinite,
            titleRes = R.string.demo_scenario_animation_infinite_title,
            summaryRes = R.string.demo_scenario_animation_infinite_summary,
            root = R.id.demo_animation_infinite_root,
            ready = R.id.demo_animation_infinite_ready,
            primaryAction = R.id.demo_animation_infinite_primary_action,
            secondaryAction = R.id.demo_animation_infinite_secondary_action,
            reset = R.id.demo_animation_infinite_reset,
            state = R.id.demo_animation_infinite_state,
            target = R.id.demo_animation_infinite_target,
        ),
        modifierScenario(
            id = DemoScenarioIds.ModifierVisual,
            titleRes = R.string.demo_scenario_modifier_visual_title,
            summaryRes = R.string.demo_scenario_modifier_visual_summary,
            root = R.id.demo_modifier_visual_root,
            ready = R.id.demo_modifier_visual_ready,
            target = R.id.demo_modifier_visual_target,
            secondaryTarget = R.id.demo_modifier_visual_secondary_target,
        ),
        modifierScenario(
            id = DemoScenarioIds.ModifierSizing,
            titleRes = R.string.demo_scenario_modifier_sizing_title,
            summaryRes = R.string.demo_scenario_modifier_sizing_summary,
            root = R.id.demo_modifier_sizing_root,
            ready = R.id.demo_modifier_sizing_ready,
            target = R.id.demo_modifier_sizing_target,
        ),
        modifierScenario(
            id = DemoScenarioIds.ModifierAccessibility,
            titleRes = R.string.demo_scenario_modifier_accessibility_title,
            summaryRes = R.string.demo_scenario_modifier_accessibility_summary,
            root = R.id.demo_modifier_accessibility_root,
            ready = R.id.demo_modifier_accessibility_ready,
            target = R.id.demo_modifier_accessibility_target,
            secondaryTarget = R.id.demo_modifier_accessibility_secondary_target,
        ),
        scenario(
            id = DemoScenarioIds.DiagnosticsRuntime,
            category = DemoScenarioCategory.Runtime,
            titleRes = R.string.demo_scenario_diagnostics_runtime_title,
            summaryRes = R.string.demo_scenario_diagnostics_runtime_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual),
            route = DemoScenarioRoute(
                activityClass = DiagnosticsActivity::class.java,
                extras = mapOf(
                    DiagnosticsActivity.EXTRA_PAGE to
                        DemoRouteExtra.IntValue(DiagnosticsActivity.PAGE_RUNTIME),
                ),
            ),
            mutable = false,
            ids = TargetIds(
                root = R.id.demo_diagnostics_runtime_root,
                ready = R.id.demo_diagnostics_runtime_ready,
                target = R.id.demo_diagnostics_runtime_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.DiagnosticsTheme,
            category = DemoScenarioCategory.Rendering,
            titleRes = R.string.demo_scenario_diagnostics_theme_title,
            summaryRes = R.string.demo_scenario_diagnostics_theme_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(
                DemoVerificationKind.Manual,
                DemoVerificationKind.Visual,
                DemoVerificationKind.Benchmark,
            ),
            route = DemoScenarioRoute(
                activityClass = DiagnosticsActivity::class.java,
                extras = mapOf(
                    DiagnosticsActivity.EXTRA_PAGE to
                        DemoRouteExtra.IntValue(DiagnosticsActivity.PAGE_THEME),
                ),
            ),
            mutable = false,
            ids = TargetIds(
                root = R.id.demo_diagnostics_theme_root,
                ready = R.id.demo_diagnostics_theme_ready,
                state = R.id.demo_diagnostics_theme_state,
                target = R.id.demo_diagnostics_theme_target,
                secondaryTarget = R.id.demo_diagnostics_theme_secondary_target,
            ),
            benchmarkRevision = 2,
            benchmarkActions = listOf(DemoAutomationRole.Target),
        ),
        scenario(
            id = DemoScenarioIds.DiagnosticsRenderer,
            category = DemoScenarioCategory.Rendering,
            titleRes = R.string.demo_scenario_diagnostics_renderer_title,
            summaryRes = R.string.demo_scenario_diagnostics_renderer_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual),
            route = DemoScenarioRoute(
                activityClass = DiagnosticsActivity::class.java,
                extras = mapOf(
                    DiagnosticsActivity.EXTRA_PAGE to
                        DemoRouteExtra.IntValue(DiagnosticsActivity.PAGE_RENDERER),
                ),
            ),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_diagnostics_renderer_root,
                ready = R.id.demo_diagnostics_renderer_ready,
                primaryAction = R.id.demo_diagnostics_renderer_primary_action,
                reset = R.id.demo_diagnostics_renderer_reset,
                state = R.id.demo_diagnostics_renderer_state,
                target = R.id.demo_diagnostics_renderer_target,
            ),
            benchmarkRevision = 3,
        ),
        scenario(
            id = DemoScenarioIds.CollectionControls,
            category = DemoScenarioCategory.Collections,
            titleRes = R.string.demo_scenario_collection_controls_title,
            summaryRes = R.string.demo_scenario_collection_controls_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Benchmark),
            route = DemoScenarioRoute(CollectionsActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_collection_controls_root,
                ready = R.id.demo_collection_controls_ready,
                primaryAction = R.id.demo_collection_controls_primary_action,
                reset = R.id.demo_collection_controls_reset,
                state = R.id.demo_collection_controls_state,
                target = R.id.demo_collection_controls_target,
            ),
            benchmarkRevision = 2,
        ),
        scenario(
            id = DemoScenarioIds.CollectionLazyList,
            category = DemoScenarioCategory.Collections,
            titleRes = R.string.demo_scenario_collection_lazy_list_title,
            summaryRes = R.string.demo_scenario_collection_lazy_list_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual),
            route = DemoScenarioRoute(CollectionsActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_collection_lazy_list_root,
                ready = R.id.demo_collection_lazy_list_ready,
                primaryAction = R.id.demo_collection_lazy_list_primary_action,
                secondaryAction = R.id.demo_collection_lazy_list_secondary_action,
                reset = R.id.demo_collection_lazy_list_reset,
                state = R.id.demo_collection_lazy_list_state,
                target = R.id.demo_collection_lazy_list_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.CollectionStress,
            category = DemoScenarioCategory.Collections,
            titleRes = R.string.demo_scenario_collection_stress_title,
            summaryRes = R.string.demo_scenario_collection_stress_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Benchmark),
            route = DemoScenarioRoute(CollectionsActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_collection_stress_root,
                ready = R.id.demo_collection_stress_ready,
                primaryAction = R.id.demo_collection_stress_primary_action,
                secondaryAction = R.id.demo_collection_stress_secondary_action,
                reset = R.id.demo_collection_stress_reset,
                state = R.id.demo_collection_stress_state,
                target = R.id.demo_collection_stress_target,
            ),
            benchmarkRevision = 3,
            benchmarkActions = listOf(
                DemoAutomationRole.PrimaryAction,
                DemoAutomationRole.SecondaryAction,
                DemoAutomationRole.Reset,
            ),
        ),
        scenario(
            id = DemoScenarioIds.CollectionAndroidView,
            category = DemoScenarioCategory.Collections,
            titleRes = R.string.demo_scenario_collection_android_view_title,
            summaryRes = R.string.demo_scenario_collection_android_view_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual),
            route = DemoScenarioRoute(CollectionsActivity::class.java),
            mutable = false,
            ids = TargetIds(
                root = R.id.demo_collection_android_view_root,
                ready = R.id.demo_collection_android_view_ready,
            ),
        ),
        scenario(
            id = DemoScenarioIds.CollectionLazyRow,
            category = DemoScenarioCategory.Collections,
            titleRes = R.string.demo_scenario_collection_lazy_row_title,
            summaryRes = R.string.demo_scenario_collection_lazy_row_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(CollectionsActivity::class.java),
            mutable = false,
            ids = TargetIds(
                root = R.id.demo_collection_lazy_row_root,
                ready = R.id.demo_collection_lazy_row_ready,
            ),
        ),
        scenario(
            id = DemoScenarioIds.CollectionGrid,
            category = DemoScenarioCategory.Collections,
            titleRes = R.string.demo_scenario_collection_grid_title,
            summaryRes = R.string.demo_scenario_collection_grid_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(CollectionsActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_collection_grid_root,
                ready = R.id.demo_collection_grid_ready,
                reset = R.id.demo_collection_grid_reset,
                target = R.id.demo_collection_grid_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.CollectionPullRefresh,
            category = DemoScenarioCategory.Collections,
            titleRes = R.string.demo_scenario_collection_pull_refresh_title,
            summaryRes = R.string.demo_scenario_collection_pull_refresh_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(CollectionsActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_collection_pull_refresh_root,
                ready = R.id.demo_collection_pull_refresh_ready,
                primaryAction = R.id.demo_collection_pull_refresh_primary_action,
                secondaryAction = R.id.demo_collection_pull_refresh_secondary_action,
                reset = R.id.demo_collection_pull_refresh_reset,
                state = R.id.demo_collection_pull_refresh_state,
                target = R.id.demo_collection_pull_refresh_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.CollectionNestedLazyList,
            category = DemoScenarioCategory.Collections,
            titleRes = R.string.demo_scenario_collection_nested_lazy_list_title,
            summaryRes = R.string.demo_scenario_collection_nested_lazy_list_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(CollectionsActivity::class.java),
            mutable = false,
            ids = TargetIds(
                root = R.id.demo_collection_nested_lazy_list_root,
                ready = R.id.demo_collection_nested_lazy_list_ready,
                target = R.id.demo_collection_nested_lazy_list_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.LayoutLinear,
            category = DemoScenarioCategory.Rendering,
            titleRes = R.string.demo_scenario_layout_linear_title,
            summaryRes = R.string.demo_scenario_layout_linear_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Benchmark),
            route = DemoScenarioRoute(LayoutsActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_layout_linear_root,
                ready = R.id.demo_layout_linear_ready,
                primaryAction = R.id.demo_layout_linear_primary_action,
                reset = R.id.demo_layout_linear_reset,
                state = R.id.demo_layout_linear_state,
                target = R.id.demo_layout_linear_target,
            ),
            benchmarkRevision = 2,
        ),
        scenario(
            id = DemoScenarioIds.LayoutStack,
            category = DemoScenarioCategory.Rendering,
            titleRes = R.string.demo_scenario_layout_stack_title,
            summaryRes = R.string.demo_scenario_layout_stack_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(LayoutsActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_layout_stack_root,
                ready = R.id.demo_layout_stack_ready,
                primaryAction = R.id.demo_layout_stack_primary_action,
                reset = R.id.demo_layout_stack_reset,
                state = R.id.demo_layout_stack_state,
            ),
        ),
        scenario(
            id = DemoScenarioIds.LayoutEdges,
            category = DemoScenarioCategory.Rendering,
            titleRes = R.string.demo_scenario_layout_edges_title,
            summaryRes = R.string.demo_scenario_layout_edges_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(LayoutsActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_layout_edges_root,
                ready = R.id.demo_layout_edges_ready,
                primaryAction = R.id.demo_layout_edges_primary_action,
                reset = R.id.demo_layout_edges_reset,
                state = R.id.demo_layout_edges_state,
                target = R.id.demo_layout_edges_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.LayoutFlow,
            category = DemoScenarioCategory.Rendering,
            titleRes = R.string.demo_scenario_layout_flow_title,
            summaryRes = R.string.demo_scenario_layout_flow_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(LayoutsActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_layout_flow_root,
                ready = R.id.demo_layout_flow_ready,
                primaryAction = R.id.demo_layout_flow_primary_action,
                secondaryAction = R.id.demo_layout_flow_secondary_action,
                reset = R.id.demo_layout_flow_reset,
                state = R.id.demo_layout_flow_state,
            ),
        ),
        scenario(
            id = DemoScenarioIds.LayoutScroll,
            category = DemoScenarioCategory.Rendering,
            titleRes = R.string.demo_scenario_layout_scroll_title,
            summaryRes = R.string.demo_scenario_layout_scroll_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual),
            route = DemoScenarioRoute(LayoutsActivity::class.java),
            mutable = false,
            ids = TargetIds(
                root = R.id.demo_layout_scroll_root,
                ready = R.id.demo_layout_scroll_ready,
            ),
        ),
        scenario(
            id = DemoScenarioIds.LayoutConstraint,
            category = DemoScenarioCategory.Rendering,
            titleRes = R.string.demo_scenario_layout_constraint_title,
            summaryRes = R.string.demo_scenario_layout_constraint_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(LayoutsActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_layout_constraint_root,
                ready = R.id.demo_layout_constraint_ready,
                reset = R.id.demo_layout_constraint_reset,
            ),
        ),
        scenario(
            id = DemoScenarioIds.EnvironmentResources,
            category = DemoScenarioCategory.AndroidIntegration,
            titleRes = R.string.demo_scenario_environment_resources_title,
            summaryRes = R.string.demo_scenario_environment_resources_summary,
            host = DemoHostPolicy.Dedicated,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(ResourceConfigurationActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_environment_resources_root,
                ready = R.id.demo_environment_resources_ready,
                primaryAction = R.id.demo_environment_resources_primary_action,
                reset = R.id.demo_environment_resources_reset,
                state = R.id.demo_environment_resources_state,
                target = R.id.demo_environment_resources_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.EnvironmentCrossActivityTheme,
            category = DemoScenarioCategory.AndroidIntegration,
            titleRes = R.string.demo_scenario_environment_cross_activity_theme_title,
            summaryRes = R.string.demo_scenario_environment_cross_activity_theme_summary,
            host = DemoHostPolicy.Dedicated,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(ThemeSwitchActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_environment_cross_activity_theme_root,
                ready = R.id.demo_environment_cross_activity_theme_ready,
                primaryAction = R.id.demo_environment_cross_activity_theme_primary_action,
                secondaryAction = R.id.demo_environment_cross_activity_theme_secondary_action,
                reset = R.id.demo_environment_cross_activity_theme_reset,
                state = R.id.demo_environment_cross_activity_theme_state,
                target = R.id.demo_environment_cross_activity_theme_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.InteropAndroidView,
            category = DemoScenarioCategory.AndroidIntegration,
            titleRes = R.string.demo_scenario_interop_android_view_title,
            summaryRes = R.string.demo_scenario_interop_android_view_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(
                DemoVerificationKind.Manual,
                DemoVerificationKind.Visual,
                DemoVerificationKind.Benchmark,
            ),
            route = DemoScenarioRoute(InteropActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_interop_android_view_root,
                ready = R.id.demo_interop_android_view_ready,
                primaryAction = R.id.demo_interop_android_view_primary_action,
                reset = R.id.demo_interop_android_view_reset,
                state = R.id.demo_interop_android_view_state,
                target = R.id.demo_interop_android_view_target,
                secondaryTarget = R.id.demo_interop_android_view_secondary_target,
            ),
            benchmarkRevision = 2,
        ),
        scenario(
            id = DemoScenarioIds.OverlayTransient,
            category = DemoScenarioCategory.AndroidIntegration,
            titleRes = R.string.demo_scenario_overlay_transient_title,
            summaryRes = R.string.demo_scenario_overlay_transient_summary,
            host = DemoHostPolicy.Overlay,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(FeedbackActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_overlay_transient_root,
                ready = R.id.demo_overlay_transient_ready,
                primaryAction = R.id.demo_overlay_transient_primary_action,
                secondaryAction = R.id.demo_overlay_transient_secondary_action,
                reset = R.id.demo_overlay_transient_reset,
                state = R.id.demo_overlay_transient_state,
                target = R.id.demo_overlay_transient_target,
                secondaryTarget = R.id.demo_overlay_transient_secondary_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.OverlayDialog,
            category = DemoScenarioCategory.AndroidIntegration,
            titleRes = R.string.demo_scenario_overlay_dialog_title,
            summaryRes = R.string.demo_scenario_overlay_dialog_summary,
            host = DemoHostPolicy.Overlay,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(FeedbackActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_overlay_dialog_root,
                ready = R.id.demo_overlay_dialog_ready,
                primaryAction = R.id.demo_overlay_dialog_primary_action,
                secondaryAction = R.id.demo_overlay_dialog_secondary_action,
                reset = R.id.demo_overlay_dialog_reset,
                state = R.id.demo_overlay_dialog_state,
                target = R.id.demo_overlay_dialog_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.OverlayMenu,
            category = DemoScenarioCategory.AndroidIntegration,
            titleRes = R.string.demo_scenario_overlay_menu_title,
            summaryRes = R.string.demo_scenario_overlay_menu_summary,
            host = DemoHostPolicy.Overlay,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(FeedbackActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_overlay_menu_root,
                ready = R.id.demo_overlay_menu_ready,
                primaryAction = R.id.demo_overlay_menu_primary_action,
                secondaryAction = R.id.demo_overlay_menu_secondary_action,
                reset = R.id.demo_overlay_menu_reset,
                state = R.id.demo_overlay_menu_state,
                target = R.id.demo_overlay_menu_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.NavigationSystem,
            category = DemoScenarioCategory.Navigation,
            titleRes = R.string.demo_scenario_navigation_system_title,
            summaryRes = R.string.demo_scenario_navigation_system_summary,
            host = DemoHostPolicy.SystemNavigation,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Benchmark),
            route = DemoScenarioRoute(SystemNavigationActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_navigation_system_root,
                ready = R.id.demo_navigation_system_ready,
                primaryAction = R.id.demo_navigation_system_primary_action,
                reset = R.id.demo_navigation_system_reset,
                state = R.id.demo_navigation_system_state,
                target = R.id.demo_navigation_system_target,
            ),
            benchmarkRevision = 6,
        ),
        material3ThemeScenario(
            id = DemoScenarioIds.DesignMaterial3Xml,
            titleRes = R.string.demo_scenario_design_material3_xml_title,
            summaryRes = R.string.demo_scenario_design_material3_xml_summary,
            root = R.id.demo_design_material3_xml_root,
            ready = R.id.demo_design_material3_xml_ready,
            primaryAction = R.id.demo_design_material3_xml_primary_action,
            reset = R.id.demo_design_material3_xml_reset,
            state = R.id.demo_design_material3_xml_state,
            target = R.id.demo_design_material3_xml_target,
        ),
        material3ThemeScenario(
            id = DemoScenarioIds.DesignMaterial3Static,
            titleRes = R.string.demo_scenario_design_material3_static_title,
            summaryRes = R.string.demo_scenario_design_material3_static_summary,
            root = R.id.demo_design_material3_static_root,
            ready = R.id.demo_design_material3_static_ready,
            primaryAction = R.id.demo_design_material3_static_primary_action,
            reset = R.id.demo_design_material3_static_reset,
            state = R.id.demo_design_material3_static_state,
            target = R.id.demo_design_material3_static_target,
        ),
        material3ThemeScenario(
            id = DemoScenarioIds.DesignMaterial3Custom,
            titleRes = R.string.demo_scenario_design_material3_custom_title,
            summaryRes = R.string.demo_scenario_design_material3_custom_summary,
            root = R.id.demo_design_material3_custom_root,
            ready = R.id.demo_design_material3_custom_ready,
            primaryAction = R.id.demo_design_material3_custom_primary_action,
            reset = R.id.demo_design_material3_custom_reset,
            state = R.id.demo_design_material3_custom_state,
            target = R.id.demo_design_material3_custom_target,
        ),
        designSystemBundleScenario(
            id = DemoScenarioIds.DesignBundleMaterial3,
            titleRes = R.string.demo_scenario_design_bundle_material3_title,
            summaryRes = R.string.demo_scenario_design_bundle_material3_summary,
            root = R.id.demo_design_bundle_material3_root,
            ready = R.id.demo_design_bundle_material3_ready,
            primaryAction = R.id.demo_design_bundle_material3_primary_action,
            secondaryAction = R.id.demo_design_bundle_material3_secondary_action,
            reset = R.id.demo_design_bundle_material3_reset,
            state = R.id.demo_design_bundle_material3_state,
            target = R.id.demo_design_bundle_material3_target,
            secondaryTarget = R.id.demo_design_bundle_material3_secondary_target,
        ),
        designSystemBundleScenario(
            id = DemoScenarioIds.DesignBundleContrast,
            titleRes = R.string.demo_scenario_design_bundle_contrast_title,
            summaryRes = R.string.demo_scenario_design_bundle_contrast_summary,
            root = R.id.demo_design_bundle_contrast_root,
            ready = R.id.demo_design_bundle_contrast_ready,
            primaryAction = R.id.demo_design_bundle_contrast_primary_action,
            secondaryAction = R.id.demo_design_bundle_contrast_secondary_action,
            reset = R.id.demo_design_bundle_contrast_reset,
            state = R.id.demo_design_bundle_contrast_state,
            target = R.id.demo_design_bundle_contrast_target,
            secondaryTarget = R.id.demo_design_bundle_contrast_secondary_target,
        ),
        scenario(
            id = DemoScenarioIds.DesignOneUi7,
            category = DemoScenarioCategory.DesignSystems,
            titleRes = R.string.demo_scenario_design_one_ui7_title,
            summaryRes = R.string.demo_scenario_design_one_ui7_summary,
            host = DemoHostPolicy.Dedicated,
            verificationKinds = setOf(
                DemoVerificationKind.Manual,
                DemoVerificationKind.Visual,
            ),
            route = DemoScenarioRoute(OneUi7VerificationActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_design_oneui7_root,
                ready = R.id.demo_design_oneui7_ready,
                primaryAction = R.id.demo_design_oneui7_primary_action,
                secondaryAction = R.id.demo_design_oneui7_secondary_action,
                reset = R.id.demo_design_oneui7_reset,
                state = R.id.demo_design_oneui7_state,
                target = R.id.demo_design_oneui7_target,
                secondaryTarget = R.id.demo_design_oneui7_secondary_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.FoundationsLocals,
            category = DemoScenarioCategory.Rendering,
            titleRes = R.string.demo_scenario_foundations_locals_title,
            summaryRes = R.string.demo_scenario_foundations_locals_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(FoundationsActivity::class.java),
            mutable = false,
            ids = TargetIds(
                root = R.id.demo_foundations_locals_root,
                ready = R.id.demo_foundations_locals_ready,
                target = R.id.demo_foundations_locals_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.FoundationsTheme,
            category = DemoScenarioCategory.Rendering,
            titleRes = R.string.demo_scenario_foundations_theme_title,
            summaryRes = R.string.demo_scenario_foundations_theme_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(FoundationsActivity::class.java),
            mutable = false,
            ids = TargetIds(
                root = R.id.demo_foundations_theme_root,
                ready = R.id.demo_foundations_theme_ready,
                target = R.id.demo_foundations_theme_target,
                secondaryTarget = R.id.demo_foundations_theme_secondary_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.FoundationsMedia,
            category = DemoScenarioCategory.Rendering,
            titleRes = R.string.demo_scenario_foundations_media_title,
            summaryRes = R.string.demo_scenario_foundations_media_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(FoundationsActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_foundations_media_root,
                ready = R.id.demo_foundations_media_ready,
                primaryAction = R.id.demo_foundations_media_primary_action,
                reset = R.id.demo_foundations_media_reset,
                state = R.id.demo_foundations_media_state,
                target = R.id.demo_foundations_media_target,
                secondaryTarget = R.id.demo_foundations_media_secondary_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.FoundationsTypography,
            category = DemoScenarioCategory.Rendering,
            titleRes = R.string.demo_scenario_foundations_typography_title,
            summaryRes = R.string.demo_scenario_foundations_typography_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(FoundationsActivity::class.java),
            mutable = false,
            ids = TargetIds(
                root = R.id.demo_foundations_typography_root,
                ready = R.id.demo_foundations_typography_ready,
                target = R.id.demo_foundations_typography_target,
            ),
        ),
        actionComponentScenario(
            id = DemoScenarioIds.ComponentCard,
            titleRes = R.string.demo_scenario_component_card_title,
            summaryRes = R.string.demo_scenario_component_card_summary,
            root = R.id.demo_component_card_root,
            ready = R.id.demo_component_card_ready,
            primaryAction = R.id.demo_component_card_primary_action,
            reset = R.id.demo_component_card_reset,
            state = R.id.demo_component_card_state,
            target = R.id.demo_component_card_target,
        ),
        actionComponentScenario(
            id = DemoScenarioIds.ComponentFab,
            titleRes = R.string.demo_scenario_component_fab_title,
            summaryRes = R.string.demo_scenario_component_fab_summary,
            root = R.id.demo_component_fab_root,
            ready = R.id.demo_component_fab_ready,
            primaryAction = R.id.demo_component_fab_primary_action,
            reset = R.id.demo_component_fab_reset,
            state = R.id.demo_component_fab_state,
            target = R.id.demo_component_fab_target,
        ),
        actionComponentScenario(
            id = DemoScenarioIds.ComponentChip,
            titleRes = R.string.demo_scenario_component_chip_title,
            summaryRes = R.string.demo_scenario_component_chip_summary,
            root = R.id.demo_component_chip_root,
            ready = R.id.demo_component_chip_ready,
            primaryAction = R.id.demo_component_chip_primary_action,
            reset = R.id.demo_component_chip_reset,
            state = R.id.demo_component_chip_state,
            target = R.id.demo_component_chip_target,
        ),
        actionComponentScenario(
            id = DemoScenarioIds.ComponentListItem,
            titleRes = R.string.demo_scenario_component_list_item_title,
            summaryRes = R.string.demo_scenario_component_list_item_summary,
            root = R.id.demo_component_list_item_root,
            ready = R.id.demo_component_list_item_ready,
            primaryAction = R.id.demo_component_list_item_primary_action,
            reset = R.id.demo_component_list_item_reset,
            state = R.id.demo_component_list_item_state,
            target = R.id.demo_component_list_item_target,
        ),
        navigationComponentScenario(
            id = DemoScenarioIds.ComponentAppBars,
            titleRes = R.string.demo_scenario_component_app_bars_title,
            summaryRes = R.string.demo_scenario_component_app_bars_summary,
            root = R.id.demo_component_app_bars_root,
            ready = R.id.demo_component_app_bars_ready,
            primaryAction = R.id.demo_component_app_bars_primary_action,
            reset = R.id.demo_component_app_bars_reset,
            state = R.id.demo_component_app_bars_state,
            target = R.id.demo_component_app_bars_target,
        ),
        navigationComponentScenario(
            id = DemoScenarioIds.ComponentNavigationBar,
            titleRes = R.string.demo_scenario_component_navigation_bar_title,
            summaryRes = R.string.demo_scenario_component_navigation_bar_summary,
            root = R.id.demo_component_navigation_bar_root,
            ready = R.id.demo_component_navigation_bar_ready,
            primaryAction = R.id.demo_component_navigation_bar_primary_action,
            reset = R.id.demo_component_navigation_bar_reset,
            state = R.id.demo_component_navigation_bar_state,
            target = R.id.demo_component_navigation_bar_target,
        ),
        navigationComponentScenario(
            id = DemoScenarioIds.ComponentScaffold,
            titleRes = R.string.demo_scenario_component_scaffold_title,
            summaryRes = R.string.demo_scenario_component_scaffold_summary,
            root = R.id.demo_component_scaffold_root,
            ready = R.id.demo_component_scaffold_ready,
            primaryAction = R.id.demo_component_scaffold_primary_action,
            reset = R.id.demo_component_scaffold_reset,
            state = R.id.demo_component_scaffold_state,
            target = R.id.demo_component_scaffold_target,
        ),
        componentShowcaseScenario(
            id = DemoScenarioIds.ComponentButton,
            titleRes = R.string.demo_scenario_component_button_title,
            summaryRes = R.string.demo_scenario_component_button_summary,
            root = R.id.demo_component_button_root,
            ready = R.id.demo_component_button_ready,
            primaryAction = R.id.demo_component_button_primary_action,
            reset = R.id.demo_component_button_reset,
            state = R.id.demo_component_button_state,
            target = R.id.demo_component_button_target,
        ),
        componentShowcaseScenario(
            id = DemoScenarioIds.ComponentIconButton,
            titleRes = R.string.demo_scenario_component_icon_button_title,
            summaryRes = R.string.demo_scenario_component_icon_button_summary,
            root = R.id.demo_component_icon_button_root,
            ready = R.id.demo_component_icon_button_ready,
            primaryAction = R.id.demo_component_icon_button_primary_action,
            reset = R.id.demo_component_icon_button_reset,
            state = R.id.demo_component_icon_button_state,
            target = R.id.demo_component_icon_button_target,
        ),
        componentShowcaseScenario(
            id = DemoScenarioIds.ComponentSegmentedControl,
            titleRes = R.string.demo_scenario_component_segmented_control_title,
            summaryRes = R.string.demo_scenario_component_segmented_control_summary,
            root = R.id.demo_component_segmented_control_root,
            ready = R.id.demo_component_segmented_control_ready,
            primaryAction = R.id.demo_component_segmented_control_primary_action,
            reset = R.id.demo_component_segmented_control_reset,
            state = R.id.demo_component_segmented_control_state,
            target = R.id.demo_component_segmented_control_target,
        ),
        scenario(
            id = DemoScenarioIds.ComponentDivider,
            category = DemoScenarioCategory.Rendering,
            titleRes = R.string.demo_scenario_component_divider_title,
            summaryRes = R.string.demo_scenario_component_divider_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(ComponentShowcaseActivity::class.java),
            mutable = false,
            ids = TargetIds(
                root = R.id.demo_component_divider_root,
                ready = R.id.demo_component_divider_ready,
                target = R.id.demo_component_divider_target,
            ),
        ),
        componentShowcaseScenario(
            id = DemoScenarioIds.ComponentProgress,
            titleRes = R.string.demo_scenario_component_progress_title,
            summaryRes = R.string.demo_scenario_component_progress_summary,
            root = R.id.demo_component_progress_root,
            ready = R.id.demo_component_progress_ready,
            primaryAction = R.id.demo_component_progress_primary_action,
            reset = R.id.demo_component_progress_reset,
            state = R.id.demo_component_progress_state,
            target = R.id.demo_component_progress_target,
        ),
        performanceScenario(
            id = DemoScenarioIds.PerformanceList,
            titleRes = R.string.demo_scenario_performance_list_title,
            summaryRes = R.string.demo_scenario_performance_list_summary,
            performanceScenario = "list",
            root = R.id.demo_performance_list_root,
            ready = R.id.demo_performance_list_ready,
            primaryAction = R.id.demo_performance_list_primary_action,
            reset = R.id.demo_performance_list_reset,
            state = R.id.demo_performance_list_state,
            target = R.id.demo_performance_list_target,
            benchmarkRevision = 5,
        ),
        performanceScenario(
            id = DemoScenarioIds.PerformanceComplexLayout,
            titleRes = R.string.demo_scenario_performance_complex_layout_title,
            summaryRes = R.string.demo_scenario_performance_complex_layout_summary,
            performanceScenario = "complex_layout",
            root = R.id.demo_performance_complex_layout_root,
            ready = R.id.demo_performance_complex_layout_ready,
            primaryAction = R.id.demo_performance_complex_layout_primary_action,
            secondaryAction = R.id.demo_performance_complex_layout_secondary_action,
            reset = R.id.demo_performance_complex_layout_reset,
            state = R.id.demo_performance_complex_layout_state,
            target = R.id.demo_performance_complex_layout_target,
            benchmarkRevision = 6,
        ),
        performanceScenario(
            id = DemoScenarioIds.PerformanceShadowList,
            titleRes = R.string.demo_scenario_performance_shadow_list_title,
            summaryRes = R.string.demo_scenario_performance_shadow_list_summary,
            performanceScenario = "shadow_list",
            root = R.id.demo_performance_shadow_list_root,
            ready = R.id.demo_performance_shadow_list_ready,
            primaryAction = R.id.demo_performance_shadow_list_primary_action,
            reset = R.id.demo_performance_shadow_list_reset,
            state = R.id.demo_performance_shadow_list_state,
            target = R.id.demo_performance_shadow_list_target,
            benchmarkRevision = 3,
        ),
        performanceScenario(
            id = DemoScenarioIds.PerformanceShadowComplexLayout,
            titleRes = R.string.demo_scenario_performance_shadow_complex_layout_title,
            summaryRes = R.string.demo_scenario_performance_shadow_complex_layout_summary,
            performanceScenario = "shadow_complex_layout",
            root = R.id.demo_performance_shadow_complex_layout_root,
            ready = R.id.demo_performance_shadow_complex_layout_ready,
            primaryAction = R.id.demo_performance_shadow_complex_layout_primary_action,
            secondaryAction = R.id.demo_performance_shadow_complex_layout_secondary_action,
            reset = R.id.demo_performance_shadow_complex_layout_reset,
            state = R.id.demo_performance_shadow_complex_layout_state,
            target = R.id.demo_performance_shadow_complex_layout_target,
            benchmarkRevision = 3,
        ),
    )

    private val scenariosById: Map<String, DemoScenarioSpec> = scenarios.associateBy { it.id.value }

    init {
        validate(scenarios)
    }

    fun all(): List<DemoScenarioSpec> = scenarios

    fun find(id: String?): DemoScenarioSpec? = id?.let(scenariosById::get)

    fun require(id: String?): DemoScenarioSpec =
        requireNotNull(find(id)) {
            "Unknown demo scenario ID: $id"
        }

    fun fromIntent(intent: Intent?): DemoScenarioSpec? =
        find(intent?.getStringExtra(EXTRA_DEMO_SCENARIO_ID))

    fun createLaunchIntent(
        context: Context,
        scenario: DemoScenarioSpec,
        source: Intent? = null,
    ): Intent {
        return scenario.route.createIntent(context, source).apply {
            putExtra(EXTRA_DEMO_SCENARIO_ID, scenario.id.value)
        }
    }

    internal fun validate(specs: List<DemoScenarioSpec>) {
        require(specs.map { it.id }.distinct().size == specs.size) {
            "Duplicate demo scenario ID"
        }
        specs.forEach { spec ->
            require(spec.titleRes != 0 && spec.summaryRes != 0) {
                "${spec.id} is missing display resources"
            }
            require(spec.verificationKinds.isNotEmpty()) {
                "${spec.id} has no verification kind"
            }
            val targets = spec.automation.targets
            require(spec.automation[DemoAutomationRole.Root] != null) {
                "${spec.id} is missing root"
            }
            require(spec.automation[DemoAutomationRole.Ready] != null) {
                "${spec.id} is missing ready"
            }
            require(targets.map { it.androidViewId }.distinct().size == targets.size) {
                "${spec.id} reuses an Android target ID"
            }
            require(targets.map { it.resourceName }.distinct().size == targets.size) {
                "${spec.id} reuses an Android target resource name"
            }
            require(targets.map { it.testTag }.distinct().size == targets.size) {
                "${spec.id} reuses an in-process target tag"
            }
            targets.forEach { target ->
                require(target.androidViewId != 0) {
                    "${spec.id}/${target.role.wireValue} has no Android resource ID"
                }
                val expectedName = "demo_${spec.id.value.replace('.', '_').replace('-', '_')}_" +
                    target.role.wireValue
                require(target.resourceName == expectedName) {
                    "${spec.id}/${target.role.wireValue} must use $expectedName"
                }
            }
            if (spec.mutable) {
                require(spec.automation[DemoAutomationRole.Reset] != null) {
                    "${spec.id} is mutable but has no reset target"
                }
            }
            spec.benchmark?.let { benchmark ->
                require(benchmark.workloadRevision > 0) {
                    "${spec.id} has an invalid workload revision"
                }
                require(benchmark.actionSequence.isNotEmpty()) {
                    "${spec.id} benchmark has no action sequence"
                }
                benchmark.actionSequence.forEach { role ->
                    require(spec.automation[role] != null) {
                        "${spec.id} benchmark action ${role.wireValue} has no target"
                    }
                }
                require(spec.automation[DemoAutomationRole.State] != null) {
                    "${spec.id} benchmark has no state target"
                }
                require(spec.automation[DemoAutomationRole.Target] != null) {
                    "${spec.id} benchmark has no fixture target"
                }
            }
            if (spec.host == DemoHostPolicy.Benchmark) {
                require(spec.benchmark != null) {
                    "${spec.id} uses the benchmark host without a workload contract"
                }
            }
        }
    }

    private data class TargetIds(
        val root: Int,
        val ready: Int,
        val primaryAction: Int? = null,
        val secondaryAction: Int? = null,
        val reset: Int? = null,
        val state: Int? = null,
        val target: Int? = null,
        val secondaryTarget: Int? = null,
    )

    private fun animationScenario(
        id: DemoScenarioId,
        titleRes: Int,
        summaryRes: Int,
        root: Int,
        ready: Int,
        primaryAction: Int,
        secondaryAction: Int,
        reset: Int,
        state: Int,
        target: Int,
        benchmarkRevision: Int? = null,
        benchmarkActions: List<DemoAutomationRole>? = null,
    ): DemoScenarioSpec = scenario(
        id = id,
        category = DemoScenarioCategory.Rendering,
        titleRes = titleRes,
        summaryRes = summaryRes,
        host = DemoHostPolicy.SharedFixture,
        verificationKinds = buildSet {
            add(DemoVerificationKind.Manual)
            add(DemoVerificationKind.Visual)
            if (benchmarkRevision != null) add(DemoVerificationKind.Benchmark)
        },
        route = DemoScenarioRoute(AnimationActivity::class.java),
        mutable = true,
        ids = TargetIds(
            root = root,
            ready = ready,
            primaryAction = primaryAction,
            secondaryAction = secondaryAction,
            reset = reset,
            state = state,
            target = target,
        ),
        benchmarkRevision = benchmarkRevision,
        benchmarkActions = benchmarkActions,
    )

    private fun inputFocusFollowScenario(
        id: DemoScenarioId,
        titleRes: Int,
        summaryRes: Int,
        root: Int,
        ready: Int,
        primaryAction: Int,
        reset: Int,
        state: Int,
        target: Int,
    ): DemoScenarioSpec = scenario(
        id = id,
        category = DemoScenarioCategory.Input,
        titleRes = titleRes,
        summaryRes = summaryRes,
        host = DemoHostPolicy.SharedFixture,
        verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
        route = DemoScenarioRoute(InputActivity::class.java),
        mutable = true,
        ids = TargetIds(
            root = root,
            ready = ready,
            primaryAction = primaryAction,
            reset = reset,
            state = state,
            target = target,
        ),
    )

    private fun actionComponentScenario(
        id: DemoScenarioId,
        titleRes: Int,
        summaryRes: Int,
        root: Int,
        ready: Int,
        primaryAction: Int,
        secondaryAction: Int? = null,
        reset: Int,
        state: Int,
        target: Int,
    ): DemoScenarioSpec = scenario(
        id = id,
        category = DemoScenarioCategory.Rendering,
        titleRes = titleRes,
        summaryRes = summaryRes,
        host = DemoHostPolicy.SharedFixture,
        verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
        route = DemoScenarioRoute(ActionsActivity::class.java),
        mutable = true,
        ids = TargetIds(
            root = root,
            ready = ready,
            primaryAction = primaryAction,
            secondaryAction = secondaryAction,
            reset = reset,
            state = state,
            target = target,
        ),
    )

    private fun navigationComponentScenario(
        id: DemoScenarioId,
        titleRes: Int,
        summaryRes: Int,
        root: Int,
        ready: Int,
        primaryAction: Int,
        reset: Int,
        state: Int,
        target: Int,
    ): DemoScenarioSpec = scenario(
        id = id,
        category = DemoScenarioCategory.Rendering,
        titleRes = titleRes,
        summaryRes = summaryRes,
        host = DemoHostPolicy.SharedFixture,
        verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
        route = DemoScenarioRoute(NavigationActivity::class.java),
        mutable = true,
        ids = TargetIds(
            root = root,
            ready = ready,
            primaryAction = primaryAction,
            reset = reset,
            state = state,
            target = target,
        ),
    )

    private fun componentShowcaseScenario(
        id: DemoScenarioId,
        titleRes: Int,
        summaryRes: Int,
        root: Int,
        ready: Int,
        primaryAction: Int,
        reset: Int,
        state: Int,
        target: Int,
    ): DemoScenarioSpec = scenario(
        id = id,
        category = DemoScenarioCategory.Rendering,
        titleRes = titleRes,
        summaryRes = summaryRes,
        host = DemoHostPolicy.SharedFixture,
        verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
        route = DemoScenarioRoute(ComponentShowcaseActivity::class.java),
        mutable = true,
        ids = TargetIds(
            root = root,
            ready = ready,
            primaryAction = primaryAction,
            reset = reset,
            state = state,
            target = target,
        ),
    )

    private fun modifierScenario(
        id: DemoScenarioId,
        titleRes: Int,
        summaryRes: Int,
        root: Int,
        ready: Int,
        target: Int,
        secondaryTarget: Int? = null,
    ): DemoScenarioSpec = scenario(
        id = id,
        category = DemoScenarioCategory.Rendering,
        titleRes = titleRes,
        summaryRes = summaryRes,
        host = DemoHostPolicy.SharedFixture,
        verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
        route = DemoScenarioRoute(ModifiersActivity::class.java),
        mutable = false,
        ids = TargetIds(
            root = root,
            ready = ready,
            target = target,
            secondaryTarget = secondaryTarget,
        ),
    )

    private fun material3ThemeScenario(
        id: DemoScenarioId,
        titleRes: Int,
        summaryRes: Int,
        root: Int,
        ready: Int,
        primaryAction: Int,
        reset: Int,
        state: Int,
        target: Int,
    ): DemoScenarioSpec = scenario(
        id = id,
        category = DemoScenarioCategory.DesignSystems,
        titleRes = titleRes,
        summaryRes = summaryRes,
        host = DemoHostPolicy.Dedicated,
        verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
        route = DemoScenarioRoute(Material3DefaultThemeActivity::class.java),
        mutable = true,
        ids = TargetIds(
            root = root,
            ready = ready,
            primaryAction = primaryAction,
            reset = reset,
            state = state,
            target = target,
        ),
    )

    private fun designSystemBundleScenario(
        id: DemoScenarioId,
        titleRes: Int,
        summaryRes: Int,
        root: Int,
        ready: Int,
        primaryAction: Int,
        secondaryAction: Int,
        reset: Int,
        state: Int,
        target: Int,
        secondaryTarget: Int,
    ): DemoScenarioSpec = scenario(
        id = id,
        category = DemoScenarioCategory.DesignSystems,
        titleRes = titleRes,
        summaryRes = summaryRes,
        host = DemoHostPolicy.Dedicated,
        verificationKinds = setOf(
            DemoVerificationKind.Manual,
            DemoVerificationKind.Visual,
            DemoVerificationKind.Benchmark,
        ),
        route = DemoScenarioRoute(DemoDesignSystemVerificationActivity::class.java),
        mutable = true,
        ids = TargetIds(
            root = root,
            ready = ready,
            primaryAction = primaryAction,
            secondaryAction = secondaryAction,
            reset = reset,
            state = state,
            target = target,
            secondaryTarget = secondaryTarget,
        ),
        benchmarkRevision = 3,
    )

    private fun performanceScenario(
        id: DemoScenarioId,
        titleRes: Int,
        summaryRes: Int,
        performanceScenario: String,
        root: Int,
        ready: Int,
        primaryAction: Int,
        secondaryAction: Int? = null,
        reset: Int,
        state: Int,
        target: Int,
        benchmarkRevision: Int = 1,
    ): DemoScenarioSpec = scenario(
        id = id,
        category = DemoScenarioCategory.Performance,
        titleRes = titleRes,
        summaryRes = summaryRes,
        host = DemoHostPolicy.Benchmark,
        verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Benchmark),
        route = DemoScenarioRoute(
            activityClass = PerformanceComparisonActivity::class.java,
            extras = mapOf(
                EXTRA_PERFORMANCE_ENGINE to DemoRouteExtra.StringValue("viewcompose"),
                EXTRA_PERFORMANCE_SCENARIO to DemoRouteExtra.StringValue(performanceScenario),
            ),
            callerOverrideableExtraKeys = setOf(EXTRA_PERFORMANCE_ENGINE),
        ),
        mutable = true,
        ids = TargetIds(
            root = root,
            ready = ready,
            primaryAction = primaryAction,
            secondaryAction = secondaryAction,
            reset = reset,
            state = state,
            target = target,
        ),
        benchmarkRevision = benchmarkRevision,
    )

    private fun scenario(
        id: DemoScenarioId,
        category: DemoScenarioCategory,
        titleRes: Int,
        summaryRes: Int,
        host: DemoHostPolicy,
        verificationKinds: Set<DemoVerificationKind>,
        route: DemoScenarioRoute,
        mutable: Boolean,
        ids: TargetIds,
        benchmarkRevision: Int? = null,
        benchmarkActions: List<DemoAutomationRole>? = null,
    ): DemoScenarioSpec {
        fun target(
            role: DemoAutomationRole,
            androidViewId: Int,
        ): Triple<DemoAutomationRole, Int, String> {
            val resourceName = "demo_${id.value.replace('.', '_').replace('-', '_')}_${role.wireValue}"
            return Triple(role, androidViewId, resourceName)
        }

        val targets = buildList {
            add(target(DemoAutomationRole.Root, ids.root))
            add(target(DemoAutomationRole.Ready, ids.ready))
            ids.primaryAction?.let { add(target(DemoAutomationRole.PrimaryAction, it)) }
            ids.secondaryAction?.let { add(target(DemoAutomationRole.SecondaryAction, it)) }
            ids.reset?.let { add(target(DemoAutomationRole.Reset, it)) }
            ids.state?.let { add(target(DemoAutomationRole.State, it)) }
            ids.target?.let { add(target(DemoAutomationRole.Target, it)) }
            ids.secondaryTarget?.let { add(target(DemoAutomationRole.SecondaryTarget, it)) }
        }
        return DemoScenarioSpec(
            id = id,
            category = category,
            titleRes = titleRes,
            summaryRes = summaryRes,
            host = host,
            verificationKinds = verificationKinds,
            route = route,
            automation = DemoAutomationContract.create(id, *targets.toTypedArray()),
            mutable = mutable,
            benchmark = benchmarkRevision?.let { revision ->
                DemoBenchmarkContract(
                    workloadRevision = revision,
                    actionSequence = benchmarkActions ?: listOf(
                        DemoAutomationRole.PrimaryAction,
                        DemoAutomationRole.Reset,
                    ),
                )
            },
        )
    }
}
