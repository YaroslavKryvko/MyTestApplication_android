package com.example.mytestapplication.presentation.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.mytestapplication.R
import com.example.mytestapplication.domain.Priority
import com.example.mytestapplication.presentation.labelResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoDetailsScreen(
    state: TodoDetailsUiState,
    onAction: (TodoDetailsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.new_todo else R.string.edit_todo)) },
                navigationIcon = {
                    IconButton(
                        onClick = { onAction(TodoDetailsAction.Back) },
                        enabled = !state.isBusy,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onAction(TodoDetailsAction.Save) },
                        enabled = state.canSave,
                    ) { Text(stringResource(R.string.save)) }
                    if (!state.isNew) {
                        TextButton(
                            onClick = { onAction(TodoDetailsAction.Delete) },
                            enabled = state.status == DetailsStatus.READY && !state.isBusy,
                        ) { Text(stringResource(R.string.delete)) }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding)
                .consumeWindowInsets(padding).imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            when (state.status) {
                DetailsStatus.LOADING -> CircularProgressIndicator()
                DetailsStatus.NETWORK_ERROR -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.network_error))
                    Button(onClick = { onAction(TodoDetailsAction.Retry) }) {
                        Text(stringResource(R.string.retry))
                    }
                }
                DetailsStatus.NOT_FOUND -> Text(stringResource(R.string.todo_not_found))
                DetailsStatus.READY -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (state.isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = { onAction(TodoDetailsAction.TitleChanged(it)) },
                        label = { Text(stringResource(R.string.title)) },
                        singleLine = true,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = { onAction(TodoDetailsAction.NoteChanged(it)) },
                        label = { Text(stringResource(R.string.note)) },
                        minLines = 4,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.priority))
                    Column(modifier = Modifier.selectableGroup()) {
                        Priority.entries.forEach { priority ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .selectable(
                                        selected = state.priority == priority,
                                        enabled = !state.isBusy,
                                        role = Role.RadioButton,
                                        onClick = { onAction(TodoDetailsAction.PriorityChanged(priority)) },
                                    ).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = state.priority == priority,
                                    onClick = null,
                                    enabled = !state.isBusy,
                                    modifier = Modifier.padding(12.dp),
                                )
                                Text(stringResource(priority.labelResource()))
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { onAction(TodoDetailsAction.KeepEditing) },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_message)) },
            confirmButton = {
                TextButton(onClick = { onAction(TodoDetailsAction.ConfirmDiscard) }) {
                    Text(stringResource(R.string.discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(TodoDetailsAction.KeepEditing) }) {
                    Text(stringResource(R.string.keep_editing))
                }
            },
        )
    }
}
