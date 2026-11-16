package com.example.mytestapplication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytestapplication.data.FakeTodoApi
import com.example.mytestapplication.data.InMemoryTodoRepository
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoCreationRefreshTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<TodoTestActivity>
    private val attempts = AtomicInteger()

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        TodoTestActivity.repository = null
    }

    @Test
    fun creationReloadsOnceAfterInitialListFailureAndDoesNotReplayOnReturn() {
        launchApp(failingAttempt = 1)
        compose.onNodeWithText("Network error").assertIsDisplayed()
        createTask()

        awaitText("Created task")
        assertEquals(3, attempts.get()) // Initial load, create, one reload.
        scenario.recreate()
        compose.onNodeWithText("Created task").assertIsDisplayed()
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Created task").assertIsDisplayed()
        assertEquals(3, attempts.get())

        // Merely opening and leaving a blank create form must not reload.
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Created task").assertIsDisplayed()
        assertEquals(3, attempts.get())
    }

    @Test
    fun failedPostCreationReloadWaitsForUserRetryAcrossLifecycleChanges() {
        launchApp(failingAttempt = 3)
        compose.onNodeWithText("No todos yet").assertIsDisplayed()
        createTask()

        awaitText("Network error")
        assertEquals(3, attempts.get()) // Creation succeeded; only its reload failed.
        compose.mainClock.advanceTimeBy(10_000)
        scenario.recreate()
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Network error").assertIsDisplayed()
        assertEquals(3, attempts.get())

        compose.onNodeWithText("Retry").performClick()
        awaitText("Created task")
        assertEquals(4, attempts.get())
    }

    private fun launchApp(failingAttempt: Int) {
        TodoTestActivity.repository = InMemoryTodoRepository(FakeTodoApi(
            latency = {},
            shouldFail = { attempts.incrementAndGet() == failingAttempt },
        ))
        scenario = ActivityScenario.launch(TodoTestActivity::class.java)
    }

    private fun createTask() {
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithText("Title").performTextInput("Created task")
        compose.onNodeWithText("Save").performClick()
    }

    private fun awaitText(text: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text).assertIsDisplayed()
    }
}
