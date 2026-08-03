---
title: Animate completion and add bounded gestures
sidebar_position: 6
---

# Animate completion and add bounded gestures

This chapter makes task rows more expressive while preserving deterministic operation. A completed
label enters and exits from observable state; tapping a row toggles it and long-pressing requests
deletion. Visible buttons expose the same operations for accessibility, discoverability, tests, and
pointer environments without long-press support.

## Prerequisites and module baseline

Complete [Confirm deletion and host a native Android View](./task-list-overlays-and-android-views.md).
This chapter adds `viewcompose-animation` and `viewcompose-gesture`, both at `0.1.0-alpha01` in the
verified sample.

## 1. Animate state, not an imperative View

`AnimatedVisibility` owns the temporary transition host. The application owns only the semantic
`completed` value; content remains mounted while an exit transition finishes and is removed after
the animation settles.

## 2. Share actions between gestures and controls

The row forwards gesture and button events to the same callbacks. Long-press opens the deletion
dialog from the previous chapter; it never performs destructive work directly.

{/* tutorial-sample source="samples/task-list/src/main/java/com/viewcompose/samples/tasklist/TaskListScreens.kt" region="task-list-animation-gestures" */}
```kotlin
private fun UiTreeBuilder.AnimatedTaskRow(
    task: TaskItem,
    onToggle: (TaskItem) -> Unit,
    onRequestDelete: (TaskItem) -> Unit,
    onOpenDetails: (TaskItem) -> Unit,
) {
    Surface(
        key = task.id,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onToggle(task) },
                onLongClick = { onRequestDelete(task) },
            ),
    ) {
        Column(spacing = 8.dp, modifier = Modifier.padding(12.dp)) {
            Text(
                text = task.title,
                style = TextDefaults.titleMediumStyle(),
            )
            AnimatedVisibility(visible = task.completed) {
                Text(text = "Completed", color = Theme.colors.primary)
            }
            Button(
                text = if (task.completed) "Reopen ${task.title}" else "Complete ${task.title}",
                onClick = { onToggle(task) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(spacing = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Button(
                    text = "Details ${task.title}",
                    variant = ButtonVariant.Outlined,
                    onClick = { onOpenDetails(task) },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    text = "Delete ${task.title}",
                    variant = ButtonVariant.Outlined,
                    onClick = { onRequestDelete(task) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
```
{/* tutorial-sample-end */}

`combinedClickable` delegates timing, movement slop, competition, and callback ordering to the
renderer. Keep its key and callbacks stable while recognition should continue. A row click and its
explicit completion button intentionally call the same update function; nested controls consume
their own gestures before the row recognizer.

The stable task ID is used by both `Surface` and the surrounding `LazyColumn`. This lets an animated
row keep identity when the immutable task record is replaced.

## 3. Verify interruption and fallback behavior

Rapidly toggle one task while the completed label is entering or exiting; the visible state must
converge on the latest task value. Long-press the row to open delete confirmation, dismiss it, then
use the visible delete button to open the same request. Use the explicit details button to verify
that navigation still works independently of the row gesture.

```bash
./gradlew verifyTutorialSamples
./gradlew :samples:task-list:assembleDebug
```

## Continue the application

Finally, [set collection policies and inspect render diagnostics](./task-list-performance-and-diagnostics.md).
