package com.example.mytestapplication.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mytestapplication.R
import com.example.mytestapplication.domain.Priority
import com.example.mytestapplication.domain.Todo
import com.example.mytestapplication.presentation.labelResource
import com.example.mytestapplication.ui.theme.MyTestApplicationTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    state: TodoListUiState,
    onAction: (TodoListAction) -> Unit,
    onCreate: () -> Unit,
    onOpenDetails: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.todos)) },
                actions = { TextButton(onClick = onCreate) { Text(stringResource(R.string.create)) } },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (val content = state.content) {
                TodoListContent.Loading -> CircularProgressIndicator()
                TodoListContent.Error -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.network_error))
                    Button(onClick = { onAction(TodoListAction.Retry) }) {
                        Text(stringResource(R.string.retry))
                    }
                }
                is TodoListContent.Loaded -> if (content.todos.isEmpty()) {
                    Text(stringResource(R.string.no_todos))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    ) {
                        items(content.todos, key = { it.id }) { todo ->
                            TodoCard(
                                todo = todo,
                                enabled = todo.id !in state.pendingIds,
                                onClick = { onOpenDetails(todo.id) },
                                onDoneChange = { onAction(TodoListAction.SetDone(todo.id, it)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TodoCard(
    todo: Todo,
    enabled: Boolean,
    onClick: () -> Unit,
    onDoneChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val checkboxLabel = stringResource(R.string.completion_for, todo.title)
    Card(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                )
                Text(
                    text = stringResource(R.string.priority_value, stringResource(todo.priority.labelResource())),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(
                checked = todo.done,
                onCheckedChange = onDoneChange,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = checkboxLabel },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TodoListContentPreview() {
    MyTestApplicationTheme {
        TodoListScreen(
            TodoListUiState(TodoListContent.Loaded(listOf(
                Todo(1, "Plan the week", "", Priority.HIGH, false),
                Todo(2, "Buy groceries", "", Priority.MEDIUM, true),
                Todo(3, "Read a chapter", "", Priority.LOW, false),
            ))),
            {}, {}, {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TodoListEmptyPreview() {
    MyTestApplicationTheme {
        TodoListScreen(TodoListUiState(TodoListContent.Loaded(emptyList())), {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun TodoListLoadingPreview() {
    MyTestApplicationTheme { TodoListScreen(TodoListUiState(), {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun TodoListErrorPreview() {
    MyTestApplicationTheme {
        TodoListScreen(TodoListUiState(TodoListContent.Error), {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun TodoCardPreview() {
    MyTestApplicationTheme {
        TodoCard(Todo(1, "Completed task", "", Priority.HIGH, true), true, {}, {})
    }
}
