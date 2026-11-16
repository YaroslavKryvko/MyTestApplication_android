package com.example.mytestapplication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytestapplication.data.FakeTodoApi
import com.example.mytestapplication.data.InMemoryTodoRepository
import com.example.mytestapplication.domain.Priority
import com.example.mytestapplication.domain.Todo
import com.example.mytestapplication.domain.TodoContent
import com.example.mytestapplication.domain.TodoRepository
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoLifecycleTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<TodoTestActivity>
    private lateinit var repository: TodoRepository
    private val gate = AtomicReference<CompletableDeferred<Unit>?>(null)
    private val requests = AtomicInteger()

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        TodoTestActivity.repository = null
        gate.getAndSet(null)?.cancel()
    }

    @Test
    fun recreationPreservesDraftAndDoesNotDuplicatePendingSave() {
        launchApp()
        enterDraft()
        var originalActivity: TodoTestActivity? = null
        scenario.onActivity { originalActivity = it }

        scenario.recreate()

        scenario.onActivity { assertNotSame(originalActivity, it) }
        compose.onNodeWithText("Draft").assertIsDisplayed()
        compose.onNodeWithText("Draft note").assertIsDisplayed()
        compose.onNode(isSelectable().and(hasText("High"))).assertIsSelected()
        val beforeSave = requests.get()
        val pendingSave = holdRequests()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        assertEquals(beforeSave + 1, requests.get())

        scenario.recreate()

        compose.onNodeWithText("Draft").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        assertEquals(beforeSave + 1, requests.get())
        releaseRequests(pendingSave)
        awaitList()

        // One create and one post-creation reload, even across recreation.
        assertEquals(beforeSave + 2, requests.get())
        val saved = snapshot().single()
        assertEquals("Draft", saved.title)
        assertEquals("Draft note", saved.note)
        assertEquals(Priority.HIGH, saved.priority)
    }

    @Test
    fun saveCompletedWhileStoppedReturnsToListOnceOnResume() {
        launchApp()
        enterDraft()
        val pendingSave = holdRequests()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()

        scenario.moveToState(Lifecycle.State.CREATED)
        releaseRequests(pendingSave)
        // Wait for the actual commit while the Activity is stopped; do not sleep.
        val saved = runBlocking {
            withTimeout(5_000) { repository.loadAllTodos().first { it.isNotEmpty() } }
        }
        assertEquals("Draft", saved.single().title)
        scenario.moveToState(Lifecycle.State.RESUMED)

        awaitList()
        compose.onNodeWithText("Draft").assertIsDisplayed()
        // A later lifecycle restart must not replay the previous close request.
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithText("Title").performTextInput("Second draft")
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("New todo").assertIsDisplayed()
        compose.onNodeWithText("Second draft").assertIsDisplayed()
        assertEquals(1, snapshot().size)
    }

    @Test
    fun systemBackIsConsumedDuringSaveAndDelete() {
        launchApp()
        enterDraft()
        val pendingSave = holdRequests()
        compose.onNodeWithText("Save").performClick()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithText("New todo").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        compose.onNodeWithText("Discard changes?").assertDoesNotExist()
        releaseRequests(pendingSave)
        awaitList()

        compose.onNodeWithText("Draft").performClick()
        compose.onNodeWithText("Delete").assertIsEnabled()
        val pendingDelete = holdRequests()
        compose.onNodeWithText("Delete").performClick()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithText("Edit todo").assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        compose.onNodeWithText("Discard changes?").assertDoesNotExist()
        releaseRequests(pendingDelete)
        awaitList()
        compose.onNodeWithText("No todos yet").assertIsDisplayed()
        assertTrue(snapshot().isEmpty())
    }

    @Test
    fun pendingCompletionBlocksCardAndCheckboxUntilSuccess() {
        launchApp(TodoContent("Task"))
        val beforeUpdate = requests.get()
        val pendingUpdate = holdRequests()
        compose.onNodeWithContentDescription("Completed: Task").performClick()

        compose.onNodeWithText("Task").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Completed: Task").assertIsNotEnabled()
        // Send physical clicks: disabled semantics alone would not prove navigation is blocked.
        compose.onNodeWithText("Task").performTouchInput { click() }
        compose.onNodeWithContentDescription("Completed: Task").performTouchInput { click() }
        compose.onNodeWithText("Edit todo").assertDoesNotExist()
        compose.onNodeWithText("Todos").assertIsDisplayed()
        assertEquals(beforeUpdate + 1, requests.get())

        releaseRequests(pendingUpdate)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Task").fetchSemanticsNodes()
                .any { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
        }
        compose.onNodeWithContentDescription("Completed: Task").assertIsOn().assertIsEnabled()
        compose.onNodeWithText("Task").assertIsEnabled().performClick()
        compose.onNodeWithText("Edit todo").assertIsDisplayed()
        assertTrue(snapshot().single().done)
    }

    @Test
    fun priorityFilterSurvivesRecreationWithoutReloading() {
        launchApp(TodoContent("High task", priority = Priority.HIGH))
        val beforeFilter = requests.get()
        compose.onNodeWithText("Filter: All").performClick()
        compose.onNodeWithText("Low").performClick()
        compose.onNodeWithText("No todos match this priority").assertIsDisplayed()

        scenario.recreate()

        compose.onNodeWithText("Filter: Low").assertIsDisplayed()
        compose.onNodeWithText("No todos match this priority").assertIsDisplayed()
        assertEquals(beforeFilter, requests.get())
        compose.onNodeWithText("Filter: Low").performClick()
        compose.onNodeWithText("All").performClick()
        compose.onNodeWithText("High task").assertIsDisplayed()
        assertEquals(beforeFilter, requests.get())
    }

    private fun launchApp(seed: TodoContent? = null) {
        repository = InMemoryTodoRepository(FakeTodoApi(
            latency = {
                requests.incrementAndGet()
                gate.get()?.await()
            },
            shouldFail = { false },
        ))
        if (seed != null) runBlocking { repository.createTodo(seed) }
        TodoTestActivity.repository = repository
        scenario = ActivityScenario.launch(TodoTestActivity::class.java)
        awaitList()
    }

    private fun enterDraft() {
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithText("Title").performTextInput("Draft")
        compose.onNodeWithText("Note").performTextInput("Draft note")
        compose.onNode(isSelectable().and(hasText("High"))).performClick()
    }

    private fun holdRequests(): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().also { gate.set(it) }

    private fun releaseRequests(pending: CompletableDeferred<Unit>) {
        gate.set(null)
        pending.complete(Unit)
    }

    private fun awaitList() {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Todos").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Create").assertIsEnabled()
    }

    private fun snapshot(): List<Todo> = runBlocking {
        withTimeout(5_000) { repository.loadAllTodos().first() }
    }
}
