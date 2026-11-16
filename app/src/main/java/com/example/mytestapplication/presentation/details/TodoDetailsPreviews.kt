package com.example.mytestapplication.presentation.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.mytestapplication.domain.Priority
import com.example.mytestapplication.ui.theme.MyTestApplicationTheme

@Preview(showBackground = true)
@Composable
private fun NewTodoPreview() {
    MyTestApplicationTheme { TodoDetailsScreen(TodoDetailsUiState(isNew = true), {}) }
}

@Preview(showBackground = true)
@Composable
private fun EditTodoPreview() {
    MyTestApplicationTheme {
        TodoDetailsScreen(
            TodoDetailsUiState(
                isNew = false,
                title = "Plan the week",
                note = "Choose three things to focus on.",
                priority = Priority.HIGH,
            ),
            {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TodoDetailsErrorPreview() {
    MyTestApplicationTheme {
        TodoDetailsScreen(TodoDetailsUiState(isNew = false, status = DetailsStatus.NETWORK_ERROR), {})
    }
}
