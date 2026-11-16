package com.example.mytestapplication.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mytestapplication.domain.Todo
import com.example.mytestapplication.domain.TodoContent
import com.example.mytestapplication.domain.TodoFailure
import com.example.mytestapplication.domain.TodoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TodoListContent {
    data object Loading : TodoListContent
    data object Error : TodoListContent
    data class Loaded(val todos: List<Todo>) : TodoListContent
}

data class TodoListUiState(
    val content: TodoListContent = TodoListContent.Loading,
    val pendingIds: Set<Long> = emptySet(),
    val showSaveError: Boolean = false,
)

sealed interface TodoListAction {
    data object Retry : TodoListAction
    data class SetDone(val id: Long, val done: Boolean) : TodoListAction
    data object ErrorShown : TodoListAction
}

class TodoListViewModel(private val repository: TodoRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(TodoListUiState())
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onAction(action: TodoListAction) {
        when (action) {
            TodoListAction.Retry -> if (state.value.content is TodoListContent.Error) load()
            is TodoListAction.SetDone -> setDone(action)
            TodoListAction.ErrorShown -> mutableState.update { it.copy(showSaveError = false) }
        }
    }

    private fun load() {
        loadJob?.cancel()
        mutableState.update { it.copy(content = TodoListContent.Loading) }
        loadJob = viewModelScope.launch {
            try {
                repository.loadAllTodos().collect { todos ->
                    mutableState.update { it.copy(content = TodoListContent.Loaded(todos)) }
                }
            } catch (_: TodoFailure) {
                mutableState.update { it.copy(content = TodoListContent.Error) }
            }
        }
    }

    private fun setDone(action: TodoListAction.SetDone) {
        if (action.id in state.value.pendingIds) return
        val todo = (state.value.content as? TodoListContent.Loaded)
            ?.todos?.firstOrNull { it.id == action.id } ?: return
        if (todo.done == action.done) return
        mutableState.update { it.copy(pendingIds = it.pendingIds + todo.id) }
        viewModelScope.launch {
            try {
                repository.editTodo(
                    todo.id,
                    TodoContent(todo.title, todo.note, todo.priority, action.done),
                )
            } catch (_: TodoFailure) {
                mutableState.update { it.copy(showSaveError = true) }
            } finally {
                mutableState.update { it.copy(pendingIds = it.pendingIds - todo.id) }
            }
        }
    }
}
