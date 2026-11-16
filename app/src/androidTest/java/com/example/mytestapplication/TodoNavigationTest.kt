package com.example.mytestapplication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytestapplication.data.FakeTodoApi
import com.example.mytestapplication.data.InMemoryTodoRepository
import com.example.mytestapplication.presentation.TodoApp
import com.example.mytestapplication.ui.theme.MyTestApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Navigation integration using a deterministic repository; component rendering is tested separately. */
@RunWith(AndroidJUnit4::class)
class TodoNavigationTest {
    @get:Rule val compose = createComposeRule()

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
        compose.onNodeWithText("Title").performTextReplacement("Weekly shopping")
        compose.onNodeWithText("Note").performTextReplacement("Milk, bread, and eggs")
        compose.onNode(isSelectable().and(hasText("High"))).performClick()
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Completed: Weekly shopping").assertIsOn()
        compose.onNodeWithText("Shopping").assertDoesNotExist()
        compose.onNodeWithText("Weekly shopping").performClick()
        compose.onNodeWithText("Milk, bread, and eggs").assertIsDisplayed()
        compose.onNode(isSelectable().and(hasText("High"))).assertIsSelected()
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
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Draft").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard", useUnmergedTree = true).performClick()
        compose.onNodeWithText("No todos yet").assertIsDisplayed()
    }
}
