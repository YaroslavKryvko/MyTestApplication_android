package com.example.mytestapplication

import android.app.Application
import com.example.mytestapplication.data.FakeTodoApi
import com.example.mytestapplication.data.InMemoryTodoRepository
import com.example.mytestapplication.domain.TodoRepository

class TodoApplication : Application() {
    // Application lifetime preserves tasks across Activity recreation, never across process death.
    val todoRepository: TodoRepository by lazy { InMemoryTodoRepository(FakeTodoApi()) }
}
