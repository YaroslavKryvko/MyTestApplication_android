package com.example.mytestapplication.presentation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mytestapplication.R
import com.example.mytestapplication.domain.TodoRepository
import com.example.mytestapplication.presentation.details.DetailsMessage
import com.example.mytestapplication.presentation.details.TodoDetailsAction
import com.example.mytestapplication.presentation.details.TodoDetailsScreen
import com.example.mytestapplication.presentation.details.TodoDetailsViewModel
import com.example.mytestapplication.presentation.list.TodoListAction
import com.example.mytestapplication.presentation.list.TodoListScreen
import com.example.mytestapplication.presentation.list.TodoListViewModel

/** Navigation and lifecycle integration boundary; rendering components have no ViewModel access. */
@Composable
fun TodoApp(repository: TodoRepository) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "todos") {
        composable("todos") {
            val model: TodoListViewModel = viewModel(factory = viewModelFactory {
                initializer { TodoListViewModel(repository) }
            })
            val state = model.state.collectAsStateWithLifecycle().value
            val context = LocalContext.current
            val lifecycle = LocalLifecycleOwner.current
            LaunchedEffect(model, lifecycle) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    model.state.collect { current ->
                        if (current.showSaveError) {
                            Toast.makeText(context, R.string.error_saving_todo, Toast.LENGTH_SHORT).show()
                            model.onAction(TodoListAction.ErrorShown)
                        }
                    }
                }
            }
            TodoListScreen(
                state = state,
                onAction = model::onAction,
                onCreate = { navController.navigate("todo") { launchSingleTop = true } },
                onOpenDetails = { id ->
                    navController.navigate("todo?todoId=$id") { launchSingleTop = true }
                },
            )
        }
        composable(
            route = "todo?todoId={todoId}",
            arguments = listOf(navArgument("todoId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) { entry ->
            val id = entry.arguments?.getString("todoId")?.toLongOrNull()
            val model: TodoDetailsViewModel = viewModel(factory = viewModelFactory {
                initializer { TodoDetailsViewModel(repository, id) }
            })
            val state = model.state.collectAsStateWithLifecycle().value
            val context = LocalContext.current
            val lifecycle = LocalLifecycleOwner.current
            LaunchedEffect(model, lifecycle) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    model.state.collect { current ->
                        current.message?.let { message ->
                            val resource = when (message) {
                                DetailsMessage.SAVE_ERROR -> R.string.error_saving_todo
                                DetailsMessage.DELETE_ERROR -> R.string.error_deleting_todo
                            }
                            Toast.makeText(context, resource, Toast.LENGTH_SHORT).show()
                            model.onAction(TodoDetailsAction.MessageShown)
                        }
                        if (current.closeRequested) {
                            model.onAction(TodoDetailsAction.CloseHandled)
                            navController.popBackStack()
                        }
                    }
                }
            }
            BackHandler { model.onAction(TodoDetailsAction.Back) }
            TodoDetailsScreen(state = state, onAction = model::onAction)
        }
    }
}
