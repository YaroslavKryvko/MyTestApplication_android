package com.example.mytestapplication.domain

enum class Priority { LOW, MEDIUM, HIGH }

data class Todo(
    val id: Long,
    val title: String,
    val note: String,
    val priority: Priority,
    val done: Boolean,
)

data class TodoContent(
    val title: String,
    val note: String = "",
    val priority: Priority = Priority.MEDIUM,
    val done: Boolean = false,
) {
    init {
        require(title.isNotBlank()) { "A todo needs a non-blank title" }
    }
}

sealed class TodoFailure : Exception() {
    class Network : TodoFailure()
    class NotFound : TodoFailure()
}
