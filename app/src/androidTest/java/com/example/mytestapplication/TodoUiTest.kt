package com.example.mytestapplication

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytestapplication.domain.Priority
import com.example.mytestapplication.domain.Todo
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
    fun priorityDropdownFiltersShowsEmptyMatchesAndRestoresAll() {
        val high = Todo(1, "Urgent task", "", Priority.HIGH, true)
        val medium = Todo(2, "Regular task", "", Priority.MEDIUM, false)
        var state by mutableStateOf(TodoListUiState(TodoListContent.Loaded(listOf(high, medium))))
        val actions = mutableListOf<TodoListAction>()
        compose.setContent {
            MyTestApplicationTheme {
                TodoListScreen(state, { action ->
                    actions.add(action)
                    if (action is TodoListAction.FilterPriority) {
                        state = state.copy(selectedPriority = action.priority)
                    }
                }, {}, {})
            }
        }
        compose.onNodeWithText("Filter: All").performClick()
        compose.onNodeWithText("All").assertIsSelected()
        compose.onNodeWithText("High").performClick()
        compose.onNodeWithText("Urgent task").assertIsDisplayed()
        compose.onNodeWithText("Regular task").assertDoesNotExist()
        compose.onNodeWithContentDescription("Completed: Urgent task").assertIsOn()

        compose.onNodeWithText("Filter: High").performClick()
        compose.onNodeWithText("High").assertIsSelected()
        compose.onNodeWithText("Low").performClick()
        compose.onNodeWithText("No todos match this priority").assertIsDisplayed()
        compose.onNodeWithText("Urgent task").assertDoesNotExist()
        compose.onNodeWithText("Create").assertIsEnabled()

        compose.onNodeWithText("Filter: Low").performClick()
        compose.onNodeWithText("All").performClick()
        compose.onNodeWithText("Urgent task").assertIsDisplayed()
        compose.onNodeWithText("Regular task").assertIsDisplayed()
        assertEquals(listOf(
            TodoListAction.FilterPriority(Priority.HIGH),
            TodoListAction.FilterPriority(Priority.LOW),
            TodoListAction.FilterPriority(null),
        ), actions)
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

}
