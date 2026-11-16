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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.mytestapplication.R
import com.example.mytestapplication.domain.Priority
import com.example.mytestapplication.domain.Todo
import com.example.mytestapplication.presentation.labelResource

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
                actions = {
                    PriorityFilter(
                        selectedPriority = state.selectedPriority,
                        onPrioritySelected = { onAction(TodoListAction.FilterPriority(it)) },
                    )
                    TextButton(onClick = onCreate) { Text(stringResource(R.string.create)) }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (state.content) {
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
                is TodoListContent.Loaded -> if (state.visibleTodos.isEmpty()) {
                    Text(stringResource(
                        if (state.selectedPriority == null) R.string.no_todos else R.string.no_matching_todos,
                    ))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    ) {
                        items(state.visibleTodos, key = { it.id }) { todo ->
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
private fun PriorityFilter(
    selectedPriority: Priority?,
    onPrioritySelected: (Priority?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = stringResource(selectedPriority?.labelResource() ?: R.string.priority_all)
    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.priority_filter, selectedLabel))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (listOf<Priority?>(null) + Priority.entries).forEach { priority ->
                DropdownMenuItem(
                    text = { Text(stringResource(priority?.labelResource() ?: R.string.priority_all)) },
                    modifier = Modifier.semantics { selected = priority == selectedPriority },
                    onClick = {
                        expanded = false
                        onPrioritySelected(priority)
                    },
                )
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
