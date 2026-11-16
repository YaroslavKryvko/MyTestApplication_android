package com.example.mytestapplication.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mytestapplication.domain.Priority
import com.example.mytestapplication.domain.TodoContent
import com.example.mytestapplication.domain.TodoFailure
import com.example.mytestapplication.domain.TodoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DetailsStatus { LOADING, READY, NETWORK_ERROR, NOT_FOUND }
enum class DetailsMessage { SAVE_ERROR, DELETE_ERROR }

data class TodoDetailsUiState(
    val isNew: Boolean,
    val status: DetailsStatus = DetailsStatus.READY,
    val title: String = "",
    val note: String = "",
    val priority: Priority = Priority.MEDIUM,
    val done: Boolean = false,
    val isBusy: Boolean = false,
    val isDirty: Boolean = false,
    val showDiscardDialog: Boolean = false,
    val message: DetailsMessage? = null,
    val closeRequested: Boolean = false,
) {
    val canSave: Boolean
        get() = status == DetailsStatus.READY && title.isNotBlank() && !isBusy && !closeRequested
}

sealed interface TodoDetailsAction {
    data class TitleChanged(val value: String) : TodoDetailsAction
    data class NoteChanged(val value: String) : TodoDetailsAction
    data class PriorityChanged(val value: Priority) : TodoDetailsAction
    data object Save : TodoDetailsAction
    data object Delete : TodoDetailsAction
    data object Retry : TodoDetailsAction
    data object Back : TodoDetailsAction
    data object ConfirmDiscard : TodoDetailsAction
    data object KeepEditing : TodoDetailsAction
    data object MessageShown : TodoDetailsAction
    data object CloseHandled : TodoDetailsAction
}

class TodoDetailsViewModel(
    private val repository: TodoRepository,
    private val todoId: Long?,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        TodoDetailsUiState(
            isNew = todoId == null,
            status = if (todoId == null) DetailsStatus.READY else DetailsStatus.LOADING,
        ),
    )
    val state = mutableState.asStateFlow()
    private var original: TodoContent? = null

    init {
        if (todoId != null) load()
    }

    fun onAction(action: TodoDetailsAction) {
        when (action) {
            is TodoDetailsAction.TitleChanged -> edit { copy(title = action.value) }
            is TodoDetailsAction.NoteChanged -> edit { copy(note = action.value) }
            is TodoDetailsAction.PriorityChanged -> edit { copy(priority = action.value) }
            TodoDetailsAction.Save -> save()
            TodoDetailsAction.Delete -> delete()
            TodoDetailsAction.Retry -> if (state.value.status == DetailsStatus.NETWORK_ERROR) load()
            TodoDetailsAction.Back -> if (!state.value.isBusy) {
                mutableState.update {
                    if (it.isDirty) it.copy(showDiscardDialog = true)
                    else it.copy(closeRequested = true)
                }
            }
            TodoDetailsAction.ConfirmDiscard -> mutableState.update {
                it.copy(showDiscardDialog = false, closeRequested = true)
            }
            TodoDetailsAction.KeepEditing -> mutableState.update { it.copy(showDiscardDialog = false) }
            TodoDetailsAction.MessageShown -> mutableState.update { it.copy(message = null) }
            TodoDetailsAction.CloseHandled -> mutableState.update { it.copy(closeRequested = false) }
        }
    }

    private fun edit(transform: TodoDetailsUiState.() -> TodoDetailsUiState) {
        if (state.value.isBusy || state.value.status != DetailsStatus.READY) return
        mutableState.update { current ->
            val edited = current.transform()
            val baseline = original
            val dirty = if (baseline == null) {
                edited.title.isNotEmpty() || edited.note.isNotEmpty() ||
                    edited.priority != Priority.MEDIUM
            } else {
                edited.title != baseline.title || edited.note != baseline.note ||
                    edited.priority != baseline.priority
            }
            edited.copy(isDirty = dirty)
        }
    }

    private fun load() {
        mutableState.update { it.copy(status = DetailsStatus.LOADING) }
        viewModelScope.launch {
            try {
                val todo = repository.loadAllTodos().first().firstOrNull { it.id == todoId }
                if (todo == null) {
                    mutableState.update { it.copy(status = DetailsStatus.NOT_FOUND) }
                } else {
                    original = TodoContent(todo.title, todo.note, todo.priority, todo.done)
                    mutableState.update {
                        it.copy(
                            status = DetailsStatus.READY,
                            title = todo.title,
                            note = todo.note,
                            priority = todo.priority,
                            done = todo.done,
                        )
                    }
                }
            } catch (_: TodoFailure) {
                mutableState.update { it.copy(status = DetailsStatus.NETWORK_ERROR) }
            }
        }
    }

    private fun save() {
        val snapshot = state.value
        if (!snapshot.canSave) return
        mutate(DetailsMessage.SAVE_ERROR) {
            val content = TodoContent(
                snapshot.title.trim(), snapshot.note, snapshot.priority, snapshot.done,
            )
            if (todoId == null) repository.createTodo(content)
            else repository.editTodo(todoId, content)
        }
    }

    private fun delete() {
        if (todoId == null || state.value.isBusy || state.value.closeRequested ||
            state.value.status != DetailsStatus.READY
        ) return
        mutate(DetailsMessage.DELETE_ERROR) { repository.deleteTodo(todoId) }
    }

    private fun mutate(error: DetailsMessage, operation: suspend () -> Unit) {
        mutableState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                operation()
                // Retained until the route handles navigation, including across lifecycle gaps.
                mutableState.update { it.copy(isBusy = false, isDirty = false, closeRequested = true) }
            } catch (_: TodoFailure) {
                mutableState.update { it.copy(isBusy = false, message = error) }
            }
        }
    }
}
