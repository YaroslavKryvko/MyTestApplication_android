package com.example.mytestapplication.data

import com.example.mytestapplication.domain.Priority
import com.example.mytestapplication.domain.Todo
import com.example.mytestapplication.domain.TodoContent
import com.example.mytestapplication.domain.TodoFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryTodoRepositoryTest {
    @Test
    fun loadIsDelayedAndNewApiStartsEmpty() = runTest {
        val repository = InMemoryTodoRepository(FakeTodoApi(latency = { delay(500) }, shouldFail = { false }))
        val load = async { repository.loadAllTodos().first() }
        advanceTimeBy(499)
        assertFalse(load.isCompleted)
        advanceUntilIdle()
        assertTrue(load.await().isEmpty())
        repository.createTodo(TodoContent("Task"))
        val freshRepository = InMemoryTodoRepository(FakeTodoApi(latency = {}, shouldFail = { false }))
        assertTrue(freshRepository.loadAllTodos().first().isEmpty())
    }

    @Test
    fun observerSeesCreateEditAndDeleteInStableOrder() = runTest {
        val repository = InMemoryTodoRepository(FakeTodoApi(latency = {}, shouldFail = { false }))
        var observed = emptyList<Todo>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.loadAllTodos().collect { observed = it }
        }
        val first = repository.createTodo(TodoContent("First", "Note", Priority.HIGH))
        val second = repository.createTodo(TodoContent("Second"))
        assertEquals(listOf(first.id, second.id), observed.map { it.id })
        repository.editTodo(first.id, TodoContent("Updated", "Note", Priority.LOW, done = true))
        assertEquals(listOf(first.id, second.id), observed.map { it.id })
        assertTrue(observed.first().done)
        assertEquals(Priority.LOW, observed.first().priority)
        repository.deleteTodo(first.id)
        assertEquals(listOf(second.id), observed.map { it.id })
    }

    @Test
    fun failedSaveDoesNotChangeDataAndNextCollectionCanRetryLoad() = runTest {
        var fail = false
        val repository = InMemoryTodoRepository(FakeTodoApi(latency = {}, shouldFail = { fail }))
        val original = repository.createTodo(TodoContent("Original"))
        fail = true
        try {
            repository.editTodo(original.id, TodoContent("Changed"))
            fail("Expected save failure")
        } catch (_: TodoFailure.Network) { }
        try {
            repository.loadAllTodos().first()
            fail("Expected load failure")
        } catch (_: TodoFailure.Network) { }
        fail = false
        assertEquals(listOf(original), repository.loadAllTodos().first())
    }

    @Test
    fun cancelledWriteNeverCommitsAndConcurrentCreatesHaveUniqueIds() = runTest {
        val repository = InMemoryTodoRepository(FakeTodoApi(latency = { delay(500) }, shouldFail = { false }))
        val cancelled = launch { repository.createTodo(TodoContent("Cancelled")) }
        advanceTimeBy(200)
        cancelled.cancel()
        val jobs = (1..10).map { number -> async { repository.createTodo(TodoContent("Task $number")) } }
        advanceUntilIdle()
        val ids = jobs.map { it.await().id }
        assertEquals(10, ids.toSet().size)
        assertEquals(10, repository.loadAllTodos().first().size)
    }

    @Test
    fun editAndDeleteMissingTaskReportNotFound() = runTest {
        val repository = InMemoryTodoRepository(FakeTodoApi(latency = {}, shouldFail = { false }))
        try {
            repository.editTodo(42, TodoContent("Missing"))
            fail("Expected missing task")
        } catch (_: TodoFailure.NotFound) { }
        try {
            repository.deleteTodo(42)
            fail("Expected missing task")
        } catch (_: TodoFailure.NotFound) { }
    }

    @Test(expected = IllegalArgumentException::class)
    fun whitespaceTitleIsRejectedAtDomainBoundary() {
        TodoContent("   ")
    }
}
