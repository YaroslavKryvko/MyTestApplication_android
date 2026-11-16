package com.example.mytestapplication.domain

import kotlinx.coroutines.flow.Flow

/**
 * Main-safe access to todos, shared by the list and details destinations.
 *
 * Titles must be non-blank (enforced by [TodoContent]) and are trimmed on create/edit.
 * Notes are preserved verbatim. Edits replace every content field, including completion.
 * Expected operational failures use [TodoFailure]; cancellation propagates to the caller.
 *
 * The app binds this contract to a process-only fake server: a new process starts empty.
 * Persistence, caching, offline queuing, and automatic retries are not provided.
 */
interface TodoRepository {
    /**
     * Each collection performs a delayed, fallible load, then observes current snapshots
     * in insertion order. Slow collectors may skip intermediate snapshots.
     *
     * A [TodoFailure.Network] terminates that collection; collecting again retries.
     * Cancelling collection stops observation without changing stored todos.
     */
    fun loadAllTodos(): Flow<List<Todo>>

    /**
     * Appends a todo with a new ID. A [TodoFailure.Network] leaves storage unchanged.
     * The fake server commits only after its cancellable simulated request completes.
     */
    suspend fun createTodo(content: TodoContent): Todo

    /**
     * Removes a todo, or throws [TodoFailure.NotFound] if its ID is absent.
     * The fake server delays deletion but does not simulate network failure for it.
     */
    suspend fun deleteTodo(id: Long)

    /**
     * Replaces all content while preserving identity and insertion position.
     * Throws [TodoFailure.NotFound] for an absent ID or [TodoFailure.Network] on a failed
     * simulated request; either failure leaves storage unchanged.
     */
    suspend fun editTodo(id: Long, content: TodoContent): Todo
}
