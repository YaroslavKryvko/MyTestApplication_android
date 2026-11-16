package com.example.mytestapplication.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mytestapplication.MainDispatcherRule
import com.example.mytestapplication.data.FakeTodoApi
import com.example.mytestapplication.data.InMemoryTodoRepository
import com.example.mytestapplication.domain.Priority
import com.example.mytestapplication.domain.TodoContent
import com.example.mytestapplication.presentation.details.*
import com.example.mytestapplication.presentation.list.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val models = mutableListOf<ViewModel>()
    private var failRequests = false
    private var requests = 0
    private val repository = InMemoryTodoRepository(FakeTodoApi(
        latency = { requests++; delay(500) },
        shouldFail = { failRequests },
    ))

    private fun listModel() = TodoListViewModel(repository).also { models.add(it) }
    private fun detailsModel(id: Long? = null) = TodoDetailsViewModel(repository, id).also { models.add(it) }

    @After
    fun cleanUp() {
        models.forEach { it.viewModelScope.cancel() }
    }

    @Test
    fun listErrorWaitsForManualRetryAndDuplicateRetryIsIgnored() = runTest {
        failRequests = true
        val model = listModel()
        assertEquals(TodoListContent.Loading, model.state.value.content)
        advanceUntilIdle()
        assertEquals(TodoListContent.Error, model.state.value.content)
        val requestCount = requests
        advanceTimeBy(10_000)
        assertEquals(requestCount, requests)
        failRequests = false
        model.onAction(TodoListAction.Retry)
        model.onAction(TodoListAction.Retry)
        advanceUntilIdle()
        assertEquals(TodoListContent.Loaded(emptyList()), model.state.value.content)
        assertEquals(requestCount + 1, requests)
    }

    @Test
    fun creationRefreshMakesOneLoadAndRecoversFromInitialError() = runTest {
        failRequests = true
        val list = listModel()
        advanceUntilIdle()
        assertEquals(TodoListContent.Error, list.state.value.content)
        failRequests = false
        val editor = detailsModel()
        editor.onAction(TodoDetailsAction.TitleChanged("Created"))
        editor.onAction(TodoDetailsAction.Save)
        advanceUntilIdle()
        assertEquals(DetailsOutcome.TODO_CREATED, editor.state.value.outcome)
        val beforeRefresh = requests

        list.onAction(TodoListAction.TodoCreated)
        assertEquals(TodoListContent.Loading, list.state.value.content)
        advanceUntilIdle()
        val todos = (list.state.value.content as TodoListContent.Loaded).todos
        assertEquals("Created", todos.single().title)
        assertEquals(beforeRefresh + 1, requests)
        advanceTimeBy(10_000)
        assertEquals(beforeRefresh + 1, requests)
    }

    @Test
    fun failedCreationRefreshStopsUntilExplicitRetry() = runTest {
        val list = listModel()
        advanceUntilIdle()
        repository.createTodo(TodoContent("Created"))
        val beforeRefresh = requests
        failRequests = true

        list.onAction(TodoListAction.TodoCreated)
        advanceUntilIdle()
        assertEquals(TodoListContent.Error, list.state.value.content)
        assertEquals(beforeRefresh + 1, requests)
        failRequests = false
        advanceTimeBy(10_000)
        assertEquals(TodoListContent.Error, list.state.value.content)
        assertEquals(beforeRefresh + 1, requests)

        list.onAction(TodoListAction.Retry)
        advanceUntilIdle()
        assertEquals("Created", (list.state.value.content as TodoListContent.Loaded).todos.single().title)
        assertEquals(beforeRefresh + 2, requests)
    }

    @Test
    fun completionWaitsForSuccessAndRetainsOtherFields() = runTest {
        val todo = repository.createTodo(TodoContent("Task", "Note", Priority.HIGH))
        val model = listModel()
        advanceUntilIdle()
        model.onAction(TodoListAction.SetDone(todo.id, true))
        model.onAction(TodoListAction.SetDone(todo.id, true))
        runCurrent()
        assertEquals(setOf(todo.id), model.state.value.pendingIds)
        assertFalse((model.state.value.content as TodoListContent.Loaded).todos.single().done)
        advanceUntilIdle()
        val completed = (model.state.value.content as TodoListContent.Loaded).todos.single()
        assertEquals(todo.copy(done = true), completed)
        assertTrue(model.state.value.pendingIds.isEmpty())
        model.onAction(TodoListAction.SetDone(todo.id, false))
        advanceUntilIdle()
        assertEquals(todo, (model.state.value.content as TodoListContent.Loaded).todos.single())
    }

    @Test
    fun failedCompletionKeepsPreviousValueAndCanBeRetried() = runTest {
        val todo = repository.createTodo(TodoContent("Task"))
        val model = listModel()
        advanceUntilIdle()
        failRequests = true
        model.onAction(TodoListAction.SetDone(todo.id, true))
        advanceUntilIdle()
        assertFalse((model.state.value.content as TodoListContent.Loaded).todos.single().done)
        assertTrue(model.state.value.showSaveError)
        assertTrue(model.state.value.pendingIds.isEmpty())
        model.onAction(TodoListAction.ErrorShown)
        failRequests = false
        model.onAction(TodoListAction.SetDone(todo.id, true))
        advanceUntilIdle()
        assertTrue((model.state.value.content as TodoListContent.Loaded).todos.single().done)
        assertFalse(model.state.value.showSaveError)
    }

    @Test
    fun priorityFilterIsLocalAndPreservesOrderAndCompletion() = runTest {
        val low = repository.createTodo(TodoContent("Low task", priority = Priority.LOW))
        val high = repository.createTodo(TodoContent("High task", priority = Priority.HIGH, done = true))
        val medium = repository.createTodo(TodoContent("Medium task"))
        val secondHigh = repository.createTodo(TodoContent("Second high", priority = Priority.HIGH))
        val model = listModel()
        advanceUntilIdle()
        val all = listOf(low, high, medium, secondHigh)
        val beforeFilter = requests
        assertNull(model.state.value.selectedPriority)
        assertEquals(all, model.state.value.visibleTodos)

        Priority.entries.forEach { priority ->
            model.onAction(TodoListAction.FilterPriority(priority))
            assertEquals(all.filter { it.priority == priority }, model.state.value.visibleTodos)
        }
        model.onAction(TodoListAction.FilterPriority(null))
        assertEquals(all, model.state.value.visibleTodos)
        assertEquals(beforeFilter, requests)

        model.onAction(TodoListAction.FilterPriority(Priority.HIGH))
        repository.editTodo(high.id, TodoContent("Moved to low", priority = Priority.LOW, done = true))
        advanceUntilIdle()
        assertEquals(listOf(secondHigh), model.state.value.visibleTodos)
        model.onAction(TodoListAction.SetDone(secondHigh.id, true))
        advanceUntilIdle()
        assertEquals(listOf(secondHigh.copy(done = true)), model.state.value.visibleTodos)
        assertEquals(Priority.HIGH, model.state.value.selectedPriority)
    }

    @Test
    fun prioritySelectionSurvivesLoadingErrorsAndRetryWithoutExtraRequests() = runTest {
        val low = repository.createTodo(TodoContent("Low task", priority = Priority.LOW))
        val model = listModel()
        model.onAction(TodoListAction.FilterPriority(Priority.HIGH))
        assertEquals(TodoListContent.Loading, model.state.value.content)
        advanceUntilIdle()
        assertTrue(model.state.value.visibleTodos.isEmpty())

        failRequests = true
        model.onAction(TodoListAction.TodoCreated)
        advanceUntilIdle()
        val beforeFilter = requests
        model.onAction(TodoListAction.FilterPriority(Priority.LOW))
        advanceUntilIdle()
        assertEquals(TodoListContent.Error, model.state.value.content)
        assertEquals(beforeFilter, requests)

        failRequests = false
        model.onAction(TodoListAction.Retry)
        advanceUntilIdle()
        assertEquals(Priority.LOW, model.state.value.selectedPriority)
        assertEquals(listOf(low), model.state.value.visibleTodos)
        assertEquals(beforeFilter + 1, requests)
    }

    @Test
    fun newFormDefaultsAndTitleValidation() = runTest {
        val model = detailsModel()
        assertTrue(model.state.value.isNew)
        assertFalse(model.state.value.done)
        assertEquals(Priority.MEDIUM, model.state.value.priority)
        assertFalse(model.state.value.canSave)
        model.onAction(TodoDetailsAction.TitleChanged("  "))
        model.onAction(TodoDetailsAction.Save)
        advanceUntilIdle()
        assertEquals(0, requests)
        assertFalse(model.state.value.canSave)
        model.onAction(TodoDetailsAction.TitleChanged("Task"))
        assertTrue(model.state.value.canSave)
        assertEquals("", model.state.value.note)
    }

    @Test
    fun failedSaveRetainsFormAndRepeatedSaveCreatesOnlyOneTodo() = runTest {
        val model = detailsModel()
        model.onAction(TodoDetailsAction.TitleChanged("  Task  "))
        model.onAction(TodoDetailsAction.NoteChanged("Note"))
        model.onAction(TodoDetailsAction.PriorityChanged(Priority.LOW))
        failRequests = true
        model.onAction(TodoDetailsAction.Save)
        advanceUntilIdle()
        assertEquals(DetailsMessage.SAVE_ERROR, model.state.value.message)
        assertEquals("  Task  ", model.state.value.title)
        assertEquals("Note", model.state.value.note)
        assertEquals(Priority.LOW, model.state.value.priority)
        assertNull(model.state.value.outcome)
        model.onAction(TodoDetailsAction.MessageShown)
        failRequests = false
        model.onAction(TodoDetailsAction.Save)
        model.onAction(TodoDetailsAction.Save)
        assertFalse(model.state.value.canSave)
        advanceUntilIdle()
        assertEquals(DetailsOutcome.TODO_CREATED, model.state.value.outcome)
        assertEquals("Task", repository.loadAllTodos().first().single().title)
        model.onAction(TodoDetailsAction.OutcomeHandled)
        assertNull(model.state.value.outcome)
    }

    @Test
    fun editingPreservesCompletionAndReturnsToList() = runTest {
        val todo = repository.createTodo(TodoContent("Task", done = true))
        val editor = detailsModel(todo.id)
        advanceUntilIdle()
        assertTrue(editor.state.value.done)
        editor.onAction(TodoDetailsAction.TitleChanged("Edited"))
        editor.onAction(TodoDetailsAction.Save)
        advanceUntilIdle()
        assertEquals(todo.copy(title = "Edited"), repository.loadAllTodos().first().single())
        assertEquals(DetailsOutcome.RETURN_TO_LIST, editor.state.value.outcome)
    }

    @Test
    fun deletionReturnsToListAndDuplicateSubmissionIsIgnored() = runTest {
        val todo = repository.createTodo(TodoContent("Task"))
        val deleter = detailsModel(todo.id)
        advanceUntilIdle()
        val beforeDelete = requests
        deleter.onAction(TodoDetailsAction.Delete)
        deleter.onAction(TodoDetailsAction.Delete)
        advanceUntilIdle()
        assertEquals(DetailsOutcome.RETURN_TO_LIST, deleter.state.value.outcome)
        assertEquals(beforeDelete + 1, requests)
        assertTrue(repository.loadAllTodos().first().isEmpty())
    }

    @Test
    fun detailsLoadCanRetryAndMissingTodoIsHandled() = runTest {
        val todo = repository.createTodo(TodoContent("Task"))
        failRequests = true
        val model = detailsModel(todo.id)
        advanceUntilIdle()
        assertEquals(DetailsStatus.NETWORK_ERROR, model.state.value.status)
        failRequests = false
        model.onAction(TodoDetailsAction.Retry)
        advanceUntilIdle()
        assertEquals(DetailsStatus.READY, model.state.value.status)
        val missing = detailsModel(100)
        advanceUntilIdle()
        assertEquals(DetailsStatus.NOT_FOUND, missing.state.value.status)
    }

    @Test
    fun backConfirmsDirtyFormAndCanKeepOrDiscardEdits() = runTest {
        val model = detailsModel()
        model.onAction(TodoDetailsAction.NoteChanged("Draft"))
        model.onAction(TodoDetailsAction.Back)
        assertTrue(model.state.value.showDiscardDialog)
        assertNull(model.state.value.outcome)
        model.onAction(TodoDetailsAction.KeepEditing)
        assertFalse(model.state.value.showDiscardDialog)
        assertEquals("Draft", model.state.value.note)
        model.onAction(TodoDetailsAction.Back)
        model.onAction(TodoDetailsAction.ConfirmDiscard)
        assertEquals(DetailsOutcome.RETURN_TO_LIST, model.state.value.outcome)
        assertFalse(model.state.value.showDiscardDialog)
    }

    @Test
    fun backWithoutChangesReturnsWithoutCreatingTodo() = runTest {
        val model = detailsModel()
        model.onAction(TodoDetailsAction.Back)
        assertEquals(DetailsOutcome.RETURN_TO_LIST, model.state.value.outcome)
        assertFalse(model.state.value.showDiscardDialog)
        advanceUntilIdle()
        assertEquals(0, requests)
        model.onAction(TodoDetailsAction.OutcomeHandled)
        assertNull(model.state.value.outcome)
    }

    @Test
    fun createdOutcomeBlocksFurtherActionsUntilNavigationHandlesIt() = runTest {
        val model = detailsModel()
        model.onAction(TodoDetailsAction.TitleChanged("Task"))
        model.onAction(TodoDetailsAction.Save)
        advanceUntilIdle()
        val completedState = model.state.value
        val completedRequests = requests

        assertEquals(DetailsOutcome.TODO_CREATED, completedState.outcome)
        assertFalse(completedState.isBusy)
        assertFalse(completedState.canSave)
        model.onAction(TodoDetailsAction.Back)
        model.onAction(TodoDetailsAction.TitleChanged("Another task"))
        model.onAction(TodoDetailsAction.Save)
        advanceUntilIdle()

        assertEquals(completedState, model.state.value)
        assertEquals(completedRequests, requests)
        assertEquals(1, repository.loadAllTodos().first().size)
    }

    @Test
    fun cancelledSaveDoesNotCommitOrEmitNetworkError() = runTest {
        val model = detailsModel()
        model.onAction(TodoDetailsAction.TitleChanged("Task"))
        model.onAction(TodoDetailsAction.Save)
        advanceTimeBy(200)
        model.viewModelScope.cancel()
        advanceUntilIdle()
        assertNull(model.state.value.message)
        assertNull(model.state.value.outcome)
        assertTrue(repository.loadAllTodos().first().isEmpty())
    }
}
