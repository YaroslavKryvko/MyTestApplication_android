package com.example.mytestapplication

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytestapplication.data.FakeTodoApi
import com.example.mytestapplication.data.InMemoryTodoRepository
import com.example.mytestapplication.domain.Priority
import com.example.mytestapplication.domain.Todo
import com.example.mytestapplication.presentation.TodoApp
import com.example.mytestapplication.presentation.details.TodoDetailsScreen
import com.example.mytestapplication.presentation.details.TodoDetailsUiState
import com.example.mytestapplication.presentation.list.TodoListAction
import com.example.mytestapplication.presentation.list.TodoListContent
import com.example.mytestapplication.presentation.list.TodoListScreen
import com.example.mytestapplication.presentation.list.TodoListUiState
import com.example.mytestapplication.ui.theme.MyTestApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun emptyListStillAllowsCreate() {
        var creates = 0
        compose.setContent {
            MyTestApplicationTheme {
                TodoListScreen(
                    TodoListUiState(TodoListContent.Loaded(emptyList())),
                    {}, { creates++ }, {},
                )
            }
        }
        compose.onNodeWithText("No todos yet").assertIsDisplayed()
        compose.onNodeWithText("Create").assertIsEnabled().performClick()
        assertEquals(1, creates)
    }

    @Test
    fun loadErrorShowsManualRetryAndKeepsCreateVisible() {
        val actions = mutableListOf<TodoListAction>()
        compose.setContent {
            MyTestApplicationTheme {
                TodoListScreen(TodoListUiState(TodoListContent.Error), actions::add, {}, {})
            }
        }
        compose.onNodeWithText("Network error").assertIsDisplayed()
        compose.onNodeWithText("Create").assertIsEnabled()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(listOf(TodoListAction.Retry), actions)
    }

    @Test
    fun checkboxEmitsCompletionWithoutOpeningDetailsAndCardEmitsId() {
        val actions = mutableListOf<TodoListAction>()
        var openedId: Long? = null
        compose.setContent {
            MyTestApplicationTheme {
                TodoListScreen(
                    TodoListUiState(TodoListContent.Loaded(listOf(
                        Todo(7, "Task", "", Priority.HIGH, true),
                    ))),
                    actions::add, {}, { openedId = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Completed: Task").assertIsOn().performClick()
        assertEquals(listOf(TodoListAction.SetDone(7, false)), actions)
        assertEquals(null, openedId)
        compose.onNodeWithText("Task").performClick()
        assertEquals(7L, openedId)
    }

    @Test
    fun newFormHasDisabledSaveNoDeleteAndMediumSelected() {
        compose.setContent {
            MyTestApplicationTheme {
                TodoDetailsScreen(TodoDetailsUiState(isNew = true), {})
            }
        }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Delete").assertDoesNotExist()
        compose.onNode(isSelectable().and(hasText("Medium"))).assertIsSelected()
    }

    @Test
    fun createEditCompleteAndDeleteThroughNavigation() {
        val repository = InMemoryTodoRepository(FakeTodoApi(latency = {}, shouldFail = { false }))
        compose.setContent { MyTestApplicationTheme { TodoApp(repository) } }
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithText("Title").performTextInput("Shopping")
        compose.onNodeWithText("Note").performTextInput("Milk and bread")
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.onNodeWithText("Shopping").assertIsDisplayed()
        compose.onNodeWithContentDescription("Completed: Shopping").performClick().assertIsOn()
        compose.onNodeWithText("Shopping").performClick()
        compose.onNodeWithText("Milk and bread").assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsEnabled().performClick()
        compose.onNodeWithText("No todos yet").assertIsDisplayed()
    }

    @Test
    fun failedLoadRequiresRetryClick() {
        var fail = true
        val repository = InMemoryTodoRepository(FakeTodoApi(latency = {}, shouldFail = { fail }))
        compose.setContent { MyTestApplicationTheme { TodoApp(repository) } }
        compose.onNodeWithText("Network error").assertIsDisplayed()
        compose.runOnIdle { fail = false }
        compose.onNodeWithText("Network error").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("No todos yet").assertIsDisplayed()
    }

    @Test
    fun unsavedChangesCanBeKeptOrDiscarded() {
        val repository = InMemoryTodoRepository(FakeTodoApi(latency = {}, shouldFail = { false }))
        compose.setContent { MyTestApplicationTheme { TodoApp(repository) } }
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithText("Title").performTextInput("Draft")
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Draft").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Discard", useUnmergedTree = true).performClick()
        compose.onNodeWithText("No todos yet").assertIsDisplayed()
    }
}
