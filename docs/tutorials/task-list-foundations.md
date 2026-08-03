---
title: Build a task list with state and layout
sidebar_position: 2
---

# Build a task list with state and layout

This tutorial starts a realistic application without expanding the minimal counter from the first
tutorial. You will model one task, retain it as snapshot state, arrange a header and control with
`Column` and `Row`, and update the existing Android View tree from a checkbox event.

The compiled source lives in
[`samples/task-list`](https://github.com/ViewCompose/ViewCompose/tree/main/samples/task-list). The
code blocks marked as tutorial samples are checked byte-for-byte against that module by
`verifyTutorialSamples`.

## What you will build

The first task-list stage contains:

- a `Task list` header and an `Open` or `Done` summary;
- one checkbox backed by immutable task data and mutable snapshot state;
- a reset action;
- a full-screen padded layout rendered as native Android Views.

Expected result: checking the task changes the summary to `Done`; resetting it returns the task to
`Open` without recreating the Activity.

## Prerequisites and verified baseline

Complete [Build your first application](./getting-started.md), or begin from any Kotlin Android
application that can call `setUiContent`. You need Android SDK 36 and JDK 17 to build the repository
sample. The sample uses `minSdk = 24` and JVM target 11.

This tutorial was last verified on 2026-08-03 with this independently versioned module set:

| Artifact | Version | Responsibility in this chapter |
| --- | --- | --- |
| `viewcompose-runtime` | `0.1.0-alpha01` | Snapshot state and invalidation |
| `viewcompose-ui-contract` | `0.1.0-alpha01` | Modifier, alignment, and `dp` contracts |
| `viewcompose-widget-core` | `0.1.0-alpha01` | Layouts, controls, text, theme defaults, and `remember` |
| `viewcompose-host-android` | `0.1.0-alpha01` | Activity lifecycle, theme, and native renderer host |

Check the [published module catalog](../modules/README.md) before mixing newer versions.

## 1. Create the task model

Use a stable ID for identity, a title for display, and an immutable completion flag:

{/* tutorial-sample source="samples/task-list/src/main/java/com/viewcompose/samples/tasklist/TaskListScreens.kt" region="task-item" */}
```kotlin
internal data class TaskItem(
    val id: Long,
    val title: String,
    val completed: Boolean = false,
)
```
{/* tutorial-sample-end */}

The immutable model makes an update explicit: create a copy, then publish that copy through
observable state. The ID becomes the lazy-list key in the next chapter.

## 2. Retain state and arrange the screen

Add the following compiled screen function beside the model. The complete source file contains the
imports; the important boundary here is the `UiTreeBuilder` receiver used by every widget call.

{/* tutorial-sample source="samples/task-list/src/main/java/com/viewcompose/samples/tasklist/TaskListScreens.kt" region="task-list-foundations" */}
```kotlin
internal fun UiTreeBuilder.TaskListFoundationsScreen() {
    val task = remember {
        mutableStateOf(TaskItem(id = 1, title = "Read the tutorial"))
    }

    Column(
        spacing = 16.dp,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Row(
            spacing = 12.dp,
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Task list",
                style = TextDefaults.titleLargeStyle(),
                modifier = Modifier.weight(1f),
            )
            Text(text = if (task.value.completed) "Done" else "Open")
        }
        Checkbox(
            text = task.value.title,
            checked = task.value.completed,
            onCheckedChange = { checked ->
                task.value = task.value.copy(completed = checked)
            },
        )
        Button(
            text = "Reset task",
            onClick = { task.value = task.value.copy(completed = false) },
        )
    }
}
```
{/* tutorial-sample-end */}

`remember` retains the state owner at this composition position. Reading `task.value` registers the
screen as an observer. Either event publishes a new model, which invalidates the observed scope and
patches only the affected native properties.

The layout has two independent axes:

- the outer `Column` places header, task, and action vertically;
- the header `Row` places title and status horizontally, while `weight(1f)` gives the title the
  remaining width;
- `fillMaxSize`, `fillMaxWidth`, and `padding` are ordered modifier elements consumed by the native
  renderer.

## 3. Host this stage

Inside an Activity that already uses `setUiContent`, replace the screen function invoked by its
content lambda with `TaskListFoundationsScreen`. The repository application's compiled
`MainActivity` runs the latest completed stage instead, so the same APK advances as the series
grows. Temporarily selecting the foundations function is useful when following this chapter step by
step.

## 4. Run and verify

Compile the executable source and verify the documentation copy:

```bash
./gradlew verifyTutorialSamples
./gradlew :samples:task-list:assembleDebug
```

Run `:samples:task-list` from Android Studio, temporarily select
`TaskListFoundationsScreen` in `MainActivity`, then verify:

1. the initial summary reads `Open`;
2. checking `Read the tutorial` changes it to `Done`;
3. `Reset task` returns both the checkbox and summary to the open state.

## Continue the application

Next, [add text input and a keyed lazy list](./task-list-input-and-lists.md). That chapter keeps the
same `TaskItem` model and replaces the single task owner with an observable list.
