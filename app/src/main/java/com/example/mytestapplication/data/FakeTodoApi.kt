package com.example.mytestapplication.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

internal data class TodoDto(
    val id: Long,
    val title: String,
    val note: String,
    val priority: String,
    val done: Boolean,
)

internal class FakeNetworkException : Exception()
internal class MissingTodoException : Exception()

/** Process-only fake server. Hooks make latency and failures deterministic in tests. */
internal class FakeTodoApi(
    private val latency: suspend () -> Unit = { delay(Random.nextLong(300, 801)) },
    private val shouldFail: () -> Boolean = { Random.nextDouble() < 0.15 },
) {
    private val todos = MutableStateFlow<List<TodoDto>>(emptyList())
    private val mutex = Mutex()
    private var nextId = 1L

    fun loadAll(): Flow<List<TodoDto>> = flow {
        request()
        // Subscribe to the current snapshot after the request so concurrent writes cannot be lost.
        emitAll(todos)
    }

    suspend fun create(content: TodoDto): TodoDto {
        request()
        return mutex.withLock {
            val todo = content.copy(id = nextId++)
            todos.value = todos.value + todo
            todo
        }
    }

    suspend fun edit(id: Long, content: TodoDto): TodoDto {
        request()
        return mutex.withLock {
            if (todos.value.none { it.id == id }) throw MissingTodoException()
            val todo = content.copy(id = id)
            todos.value = todos.value.map { if (it.id == id) todo else it }
            todo
        }
    }

    suspend fun delete(id: Long) {
        // Only load/save simulate network failures, as requested.
        latency()
        mutex.withLock {
            if (todos.value.none { it.id == id }) throw MissingTodoException()
            todos.value = todos.value.filterNot { it.id == id }
        }
    }

    private suspend fun request() {
        latency()
        if (shouldFail()) throw FakeNetworkException()
    }
}
