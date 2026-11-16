package com.example.mytestapplication.presentation.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.mytestapplication.domain.Priority
import com.example.mytestapplication.domain.Todo
import com.example.mytestapplication.ui.theme.MyTestApplicationTheme

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
