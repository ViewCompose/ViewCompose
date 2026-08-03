---
title: Add task input and a keyed lazy list
sidebar_position: 3
---

# Add task input and a keyed lazy list

This tutorial evolves the first task-list screen into a usable collection. You will give text input
its own state owner, append immutable task records, render them with stable keys, and update one
record without mutating the list in place.

The screen is compiled in
[`samples/task-list`](https://github.com/ViewCompose/ViewCompose/tree/main/samples/task-list), runs
from that sample's `MainActivity`, and is exercised through real Android Views by
`TaskListAppTest`.

## What you will build

The second stage adds:

- a `TextField` and an `Add task` action;
- an observable immutable list with deterministic IDs;
- a completion summary derived from the current list;
- a vertically scrolling `LazyColumn` with stable keys and a shared content type;
- checkbox events that replace only the selected task record.

Expected result: entering a non-blank title enables the action, adding it clears the field and
shows a new row, and checking any row updates the completion summary.

## Prerequisites and verified baseline

Complete [Build a task list with state and layout](./task-list-foundations.md). This chapter uses
the same Android SDK 36, `minSdk = 24`, JDK 17, and JVM target 11 baseline, last verified on
2026-08-03.

| Artifact | Version | Responsibility in this chapter |
| --- | --- | --- |
| `viewcompose-runtime` | `0.1.0-alpha01` | Observable list, ID, and derived reads |
| `viewcompose-text-core` | `0.1.0-alpha01` | Editable document and cursor state |
| `viewcompose-ui-contract` | `0.1.0-alpha01` | Modifier and lazy collection contracts |
| `viewcompose-widget-core` | `0.1.0-alpha01` | `TextField`, `LazyColumn`, `Checkbox`, and state helpers |
| `viewcompose-host-android` | `0.1.0-alpha01` | Android input, lifecycle, and native renderer host |

Check the [published module catalog](../modules/README.md) before mixing newer versions.

## 1. Replace the single task with collection and input state

Keep the `TaskItem` model from the previous chapter and replace the screen function with the
compiled second stage:

{/* tutorial-sample source="samples/task-list/src/main/java/com/viewcompose/samples/tasklist/TaskListScreens.kt" region="task-list-input" */}
```kotlin
internal fun UiTreeBuilder.TaskListInputScreen() {
    val tasks = remember {
        mutableStateOf(
            listOf(
                TaskItem(id = 1, title = "Read the tutorial"),
                TaskItem(id = 2, title = "Run the sample", completed = true),
            ),
        )
    }
    val nextId = remember { mutableStateOf(3L) }
    val newTask = rememberTextFieldState()
    val completedCount = tasks.value.count(TaskItem::completed)

    Column(
        spacing = 12.dp,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(text = "Task list", style = TextDefaults.titleLargeStyle())
        Text(text = "$completedCount of ${tasks.value.size} complete")
        TextField(
            state = newTask,
            hint = "New task",
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            text = "Add task",
            enabled = newTask.text.isNotBlank(),
            onClick = {
                val title = newTask.text.trim()
                if (title.isNotEmpty()) {
                    tasks.value = tasks.value + TaskItem(nextId.value, title)
                    nextId.value += 1
                    newTask.clearText()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(
            items = tasks.value,
            key = TaskItem::id,
            contentType = { "task" },
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { task ->
            Checkbox(
                text = task.title,
                checked = task.completed,
                onCheckedChange = { checked ->
                    tasks.value = tasks.value.map { current ->
                        if (current.id == task.id) current.copy(completed = checked) else current
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
```
{/* tutorial-sample-end */}

There are three state owners with different jobs:

- `tasks` publishes the immutable collection observed by the summary and list;
- `nextId` provides stable identity for records added during this composition;
- `newTask` owns text, selection, IME composition, and edit history, so `TextField` does not need a
  separate string callback.

`completedCount` is inexpensive and is recalculated whenever the observed task list changes. For a
costly calculation, move the same read behind `derivedStateOf`.

## 2. Preserve lazy-item identity

The structured `LazyColumn` overload requires `key`. `TaskItem::id` lets reconciliation associate a
new immutable record with the existing native row after insertion or completion changes. Do not use
the list index when items can be inserted, removed, or reordered.

Every row has the same shape, so `contentType = { "task" }` declares a shared reuse family. Use
different content types when a collection mixes structurally different headers, controls, and data
rows.

The completion handler maps the list and copies only the matching record. Publishing the new list
is what invalidates its observers; mutating a hidden mutable collection in place would bypass this
state boundary.

## 3. Validate input before publishing a task

The action is disabled while the field is blank, then trims the title again inside the event. This
keeps validation correct even if the input changes between frames. `clearText()` updates the same
`TextFieldState`, so the mounted Android editor and declarative owner remain synchronized.

For data that must survive Activity recreation or process restoration, replace the ad-hoc list and
ID owners with an application state holder backed by `rememberSaveable`, a ViewModel, or persistent
storage. This chapter intentionally keeps ownership local while teaching the collection update
path.

## 4. Run automated and manual verification

Compile the app and its instrumentation source:

```bash
./gradlew verifyTutorialSamples
./gradlew :samples:task-list:assembleDebug
./gradlew :samples:task-list:compileDebugAndroidTestKotlin
```

On a connected device or emulator, run the behavior test:

```bash
./gradlew :samples:task-list:connectedDebugAndroidTest
```

The test enters `Write a device test`, adds it, verifies that the summary changes from `1 of 2`
to `1 of 3`, then completes the first task and expects `2 of 3 complete`.

Manual verification should also cover a whitespace-only title: the add action remains disabled and
no empty row appears.

## Where to go next

The next task-list chapter will add semantic theming and list/detail navigation. Until that chapter
lands, use the [Theming guide](../guides/theming.md) and [Navigation guide](../guides/navigation.md)
as reference material rather than treating them as steps in this tutorial.
