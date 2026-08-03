---
title: Confirm deletion and host a native Android View
sidebar_position: 5
---

# Confirm deletion and host a native Android View

This chapter adds two Android integration boundaries without abandoning the list/detail structure:
a dialog overlay confirms destructive work, and an existing `TextView` displays a summary driven by
the same observable task collection.

## What you will build

- an explicit `AndroidOverlayHost` installed for the sample and its navigation destinations;
- a custom `Dialog` keyed as one logical request;
- dismissal and confirmation paths that converge on application state;
- an `AndroidView` whose factory runs once per mounted identity and whose update runs with current
  declarative inputs.

## Prerequisites and module baseline

Complete [Add semantic theming and list-detail navigation](./task-list-theme-and-navigation.md).
This chapter adds `viewcompose-overlay-android` `0.1.0-alpha01`; `AndroidView` remains part of
`viewcompose-host-android` `0.1.0-alpha01`.

## 1. Install the Android overlay implementation

The overlay artifact is discoverable through `ServiceLoader`, but the tutorial sample uses an
explicit factory so the runtime contract is visible:

```kotlin
setUiContent(overlayHostFactory = ::AndroidOverlayHost) {
    TaskListCompleteScreen(latestRenderStats::get)
}
```

The final navigation host also passes `overlayHostFactory = ::AndroidOverlayHost`, because each
destination is rendered in its own native host container.

## 2. Bind one native view and one dialog to state

The native summary derives from the current immutable list. The dialog receives the selected task,
does not delete on dismissal, and publishes the deletion only from its confirmation callback.

{/* tutorial-sample source="samples/task-list/src/main/java/com/viewcompose/samples/tasklist/TaskListScreens.kt" region="task-list-overlay-interop" */}
```kotlin
private fun UiTreeBuilder.TaskListSummaryAndDeleteDialog(
    tasks: List<TaskItem>,
    pendingDelete: TaskItem?,
    onDismissDelete: () -> Unit,
    onConfirmDelete: (TaskItem) -> Unit,
) {
    val completedCount = tasks.count(TaskItem::completed)
    AndroidView(
        key = "task-native-summary",
        factory = { context -> TextView(context) },
        update = { view ->
            (view as TextView).text =
                "Native summary: $completedCount of ${tasks.size} complete"
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Dialog(
        visible = pendingDelete != null,
        requestKey = "delete-task-dialog",
        onDismissRequest = onDismissDelete,
    ) {
        Surface(modifier = Modifier.fillMaxWidth()) {
            Column(
                spacing = 12.dp,
                modifier = Modifier.padding(20.dp),
            ) {
                Text(
                    text = "Delete ${pendingDelete?.title}?",
                    style = TextDefaults.titleMediumStyle(),
                )
                Row(spacing = 8.dp, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        text = "Cancel",
                        variant = ButtonVariant.Outlined,
                        onClick = onDismissDelete,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        text = "Delete",
                        enabled = pendingDelete != null,
                        onClick = {
                            pendingDelete?.let(onConfirmDelete)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
```
{/* tutorial-sample-end */}

Keep application ownership outside `TextView`. `factory` allocates the native object; `update`
fully rebinds the property that depends on state. If the view registers listeners or owns other
resources, use `onReset` and `onRelease` to clear recycled or final ownership respectively.

`requestKey` identifies the dialog across renders. Setting `pendingDelete` to `null` removes the
request; the overlay host then dismisses the native window and releases its child render session.

## 3. Verify both paths

Run the final sample, add or complete a task, and verify that the `Native summary` text changes.
Open a delete dialog, cancel it, then open it again and confirm. The task must remain after cancel
and disappear only after confirmation.

```bash
./gradlew :samples:task-list:connectedDebugAndroidTest
```

The device test verifies the native summary and confirmed deletion through real Android Views.

## Continue the application

Next, [animate completion and add bounded gestures](./task-list-animation-and-gestures.md).
