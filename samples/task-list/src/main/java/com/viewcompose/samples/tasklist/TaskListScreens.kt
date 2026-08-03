package com.viewcompose.samples.tasklist

import android.widget.TextView
import com.viewcompose.animation.AnimatedVisibility
import com.viewcompose.gesture.combinedClickable
import com.viewcompose.host.android.AndroidView
import com.viewcompose.navigation.NavHost
import com.viewcompose.navigation.NavHostController
import com.viewcompose.navigation.rememberNavHostController
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavValue
import com.viewcompose.overlay.android.host.AndroidOverlayHost
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.ButtonVariant
import com.viewcompose.widget.core.Checkbox
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Dialog
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.RenderStats
import com.viewcompose.widget.core.Surface
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.TextField
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.widget.core.remember
import com.viewcompose.widget.core.rememberTextFieldState

// DOCS_REGION_START(task-item)
internal data class TaskItem(
    val id: Long,
    val title: String,
    val completed: Boolean = false,
)
// DOCS_REGION_END(task-item)

// DOCS_REGION_START(task-list-foundations)
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
// DOCS_REGION_END(task-list-foundations)

// DOCS_REGION_START(task-list-input)
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
// DOCS_REGION_END(task-list-input)

private const val TASKS_ROUTE = "tasks"
private const val TASK_DETAILS_ROUTE = "task-details"
private const val TASK_ID_ARGUMENT = "task-id"

// DOCS_REGION_START(task-list-theme-navigation)
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
// DOCS_REGION_END(task-list-theme-navigation)

private fun UiTreeBuilder.TaskListNavigationHome(
    tasks: List<TaskItem>,
    controller: NavHostController,
) {
    Column(
        spacing = 12.dp,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = "Task list",
            style = TextDefaults.titleLargeStyle(),
            color = Theme.colors.primary,
        )
        tasks.forEach { task ->
            Button(
                text = "Open ${task.title}",
                onClick = {
                    controller.navigate(
                        NavRoute(
                            name = TASK_DETAILS_ROUTE,
                            arguments = mapOf(
                                TASK_ID_ARGUMENT to NavValue.LongValue(task.id),
                            ),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun UiTreeBuilder.TaskDetailsScreen(
    task: TaskItem?,
    onBack: () -> Unit,
) {
    Column(
        spacing = 12.dp,
        modifier = Modifier
            .fillMaxSize()
            .backgroundColor(Theme.colors.background)
            .padding(24.dp),
    ) {
        Text(
            text = task?.title ?: "Task unavailable",
            style = TextDefaults.titleLargeStyle(),
            color = Theme.colors.primary,
        )
        Text(text = if (task?.completed == true) "Completed" else "Open")
        Button(text = "Back to tasks", onClick = onBack)
    }
}

// DOCS_REGION_START(task-list-overlay-interop)
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
// DOCS_REGION_END(task-list-overlay-interop)

// DOCS_REGION_START(task-list-animation-gestures)
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
// DOCS_REGION_END(task-list-animation-gestures)

// DOCS_REGION_START(task-list-performance-diagnostics)
private val TaskListPrefetchPolicy = LazyLayoutPrefetchPolicy(
    initialPrefetchItemCount = 4,
    itemViewCacheSize = 4,
)
private val TaskListReusePolicy = CollectionReusePolicy(sharePool = true)

private fun UiTreeBuilder.RenderDiagnosticsControl(
    latestStats: () -> RenderStats,
    diagnostics: String,
    onDiagnosticsChange: (String) -> Unit,
) {
    Button(
        text = "Sample render stats",
        variant = ButtonVariant.Outlined,
        onClick = {
            val stats = latestStats()
            onDiagnosticsChange(
                "Render stats: ${stats.inserts} inserts, " +
                    "${stats.reuses} reuses, ${stats.patchedNodes} patches",
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(text = diagnostics)
}
// DOCS_REGION_END(task-list-performance-diagnostics)

internal fun UiTreeBuilder.TaskListCompleteScreen(
    latestStats: () -> RenderStats,
) {
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
    val pendingDelete = remember { mutableStateOf<TaskItem?>(null) }
    val diagnostics = remember { mutableStateOf("Render stats not sampled") }
    val controller = rememberNavHostController(NavRoute(TASKS_ROUTE))

    NavHost(
        controller = controller,
        debug = true,
        debugTag = "TaskListNavigation",
        overlayHostFactory = ::AndroidOverlayHost,
        modifier = Modifier
            .fillMaxSize()
            .backgroundColor(Theme.colors.background),
    ) { entry ->
        when (entry.route.name) {
            TASKS_ROUTE -> Column(
                spacing = 12.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                Text(
                    text = "Task list",
                    style = TextDefaults.titleLargeStyle(),
                    color = Theme.colors.primary,
                )
                TaskListSummaryAndDeleteDialog(
                    tasks = tasks.value,
                    pendingDelete = pendingDelete.value,
                    onDismissDelete = { pendingDelete.value = null },
                    onConfirmDelete = { task ->
                        tasks.value = tasks.value.filterNot { it.id == task.id }
                        pendingDelete.value = null
                    },
                )
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
                    prefetchPolicy = TaskListPrefetchPolicy,
                    reusePolicy = TaskListReusePolicy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { task ->
                    AnimatedTaskRow(
                        task = task,
                        onToggle = { selected ->
                            tasks.value = tasks.value.map { current ->
                                if (current.id == selected.id) {
                                    current.copy(completed = !current.completed)
                                } else {
                                    current
                                }
                            }
                        },
                        onRequestDelete = { pendingDelete.value = it },
                        onOpenDetails = { selected ->
                            controller.navigate(
                                NavRoute(
                                    name = TASK_DETAILS_ROUTE,
                                    arguments = mapOf(
                                        TASK_ID_ARGUMENT to NavValue.LongValue(selected.id),
                                    ),
                                ),
                            )
                        },
                    )
                }
                RenderDiagnosticsControl(
                    latestStats = latestStats,
                    diagnostics = diagnostics.value,
                    onDiagnosticsChange = { diagnostics.value = it },
                )
            }
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
