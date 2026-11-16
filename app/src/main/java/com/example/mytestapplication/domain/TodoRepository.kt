package com.example.mytestapplication.domain

import kotlinx.coroutines.flow.Flow

interface TodoRepository {
    /** Performs an asynchronous load, then observes successful changes in insertion order.
     * A failed load terminates this collection; collecting again explicitly retries.
     */
    fun loadAllTodos(): Flow<List<Todo>>
    suspend fun createTodo(content: TodoContent): Todo
    suspend fun deleteTodo(id: Long)
    /** Replaces this task's content; its identity and list position remain unchanged. */
    suspend fun editTodo(id: Long, content: TodoContent): Todo
}
