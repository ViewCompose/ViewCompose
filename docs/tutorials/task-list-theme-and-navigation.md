---
title: Add semantic theming and list-detail navigation
sidebar_position: 4
---

# Add semantic theming and list-detail navigation

This chapter turns the task list into a two-destination application. You will style content with
semantic tokens supplied by the Android host, retain a framework navigation controller, pass a
typed task ID to a detail destination, and return through the framework-owned back stack.

The code is compiled in
[`samples/task-list`](https://github.com/ViewCompose/ViewCompose/tree/main/samples/task-list). The
sample's final Activity runs a later stage, but this screen remains compiled so you can select it
while following the chapter.

## What you will build

- a task-list destination and a task-detail destination;
- a remembered `NavHostController` with an explicit start route;
- a typed `NavValue.LongValue` route argument;
- semantic background and primary colors resolved from the Android host theme;
- framework Back handling through `popBackStack` and system Back.

## Prerequisites and module baseline

Complete [Add task input and a keyed lazy list](./task-list-input-and-lists.md). This chapter keeps
the same Android SDK 36, `minSdk = 24`, JDK 17, and JVM target 11 baseline, last verified on
2026-08-03.

| Artifact | Version | Responsibility in this chapter |
| --- | --- | --- |
| `viewcompose-navigation-core` | `0.1.0-alpha01` | Route, typed argument, and back-stack model |
| `viewcompose-navigation` | `0.1.0-alpha01` | Remembered controller and Android `NavHost` |
| `viewcompose-widget-core` | `0.1.0-alpha01` | Semantic `Theme` tokens and destination widgets |
| `viewcompose-host-android` | `0.1.0-alpha01` | Lifecycle, saved-state, Back, and Android theme owners |

Check the [published module catalog](../modules/README.md) before mixing newer versions.

## 1. Create the routes and controller

The compiled stage keeps the task collection outside the destinations so both destinations observe
one state owner. `rememberNavHostController` restores the stack through the host's saveable-state
registry. Each detail request carries the stable task ID instead of copying a mutable task object.

{/* tutorial-sample source="samples/task-list/src/main/java/com/viewcompose/samples/tasklist/TaskListScreens.kt" region="task-list-theme-navigation" */}
```kotlin
internal fun UiTreeBuilder.TaskListThemeNavigationScreen() {
    val tasks = remember {
        mutableStateOf(
            listOf(
                TaskItem(id = 1, title = "Read the tutorial"),
                TaskItem(id = 2, title = "Run the sample", completed = true),
            ),
        )
    }
    val controller = rememberNavHostController(
        startDestination = NavRoute(TASKS_ROUTE),
    )

    NavHost(
        controller = controller,
        debug = true,
        debugTag = "TaskListNavigation",
        modifier = Modifier
            .fillMaxSize()
            .backgroundColor(Theme.colors.background),
    ) { entry ->
        when (entry.route.name) {
            TASKS_ROUTE -> TaskListNavigationHome(
                tasks = tasks.value,
                controller = controller,
            )
            TASK_DETAILS_ROUTE -> {
                val taskId = (entry.route[TASK_ID_ARGUMENT] as? NavValue.LongValue)?.value
                TaskDetailsScreen(
                    task = tasks.value.firstOrNull { it.id == taskId },
                    onBack = controller::popBackStack,
                )
            }
            else -> error("Unknown task-list route ${entry.route.name}")
        }
    }
}
```
{/* tutorial-sample-end */}

Call `navigate` only from content mounted by this `NavHost`; an unattached controller rejects
commands. The detail destination resolves the current task by ID, so a deleted or unavailable task
has an explicit fallback rather than displaying stale route data.

## 2. Use semantic tokens

`setUiContent` resolves the Activity theme and provides it to the declarative tree. Reading
`Theme.colors.background` and `Theme.colors.primary` keeps the screen aligned with light/dark mode
and the host's dynamic-color policy. Avoid copying resolved integers into long-lived application
state; read the token in composition so a configuration or theme refresh can invalidate the UI.

The route host owns a separate native destination container. Locals captured when `NavHost` is
declared, including the current theme, are propagated into destination render sessions.

## 3. Verify navigation behavior

Temporarily call `TaskListThemeNavigationScreen()` from `MainActivity`, then run the sample. Open a
task, verify its title and status on the detail page, and use both `Back to tasks` and system Back.
The list destination and its task state remain owned by the navigation host rather than being
recreated as an unrelated Activity.

```bash
./gradlew verifyTutorialSamples
./gradlew :samples:task-list:assembleDebug
```

## Continue the application

Next, [confirm deletion with an overlay and add a native Android View](./task-list-overlays-and-android-views.md).
