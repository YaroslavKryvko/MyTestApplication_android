package com.example.mytestapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.mytestapplication.domain.TodoRepository
import com.example.mytestapplication.presentation.TodoApp
import com.example.mytestapplication.ui.theme.MyTestApplicationTheme

/** Debug-only host: reinstalls real app content after recreation with a test-owned repository. */
class TodoTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val testRepository = checkNotNull(repository) { "Tests must supply a repository before launch" }
        setContent {
            MyTestApplicationTheme { TodoApp(testRepository) }
        }
    }

    companion object {
        // The test owns this reference and clears it after closing its ActivityScenario.
        var repository: TodoRepository? = null
    }
}
