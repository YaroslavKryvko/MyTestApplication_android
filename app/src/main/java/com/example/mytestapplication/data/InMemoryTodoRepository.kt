package com.example.mytestapplication.data

import com.example.mytestapplication.domain.Priority
import com.example.mytestapplication.domain.Todo
import com.example.mytestapplication.domain.TodoContent
import com.example.mytestapplication.domain.TodoFailure
import com.example.mytestapplication.domain.TodoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal class InMemoryTodoRepository(private val api: FakeTodoApi) : TodoRepository {
    override fun loadAllTodos(): Flow<List<Todo>> = api.loadAll()
        .map { todos -> todos.map { it.toDomain() } }
        .catch { cause ->
            if (cause is FakeNetworkException) throw TodoFailure.Network()
            throw cause
        }

    override suspend fun createTodo(content: TodoContent): Todo =
        translate { api.create(content.toDto()).toDomain() }

    override suspend fun editTodo(id: Long, content: TodoContent): Todo =
        translate { api.edit(id, content.toDto()).toDomain() }

    override suspend fun deleteTodo(id: Long) {
        translate { api.delete(id) }
    }

    private suspend fun <T> translate(block: suspend () -> T): T = try {
        block()
    } catch (_: FakeNetworkException) {
        throw TodoFailure.Network()
    } catch (_: MissingTodoException) {
        throw TodoFailure.NotFound()
    }

    private fun TodoDto.toDomain() = Todo(id, title, note, Priority.valueOf(priority), done)
    private fun TodoContent.toDto() = TodoDto(0, title.trim(), note, priority.name, done)
}
