package com.viewcompose.samples.tasklist

import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Checkbox
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.TextField
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
