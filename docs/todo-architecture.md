# Todo architecture

The app stays in one Gradle module. Packages enforce the intended dependency direction:
presentation -> domain <- data. TodoApplication is the composition root and owns one
repository/fake API for the process; ViewModels are scoped to navigation destinations.
Manual constructor injection avoids adding a DI framework for two screens.

## Contracts and source of truth

TodoRepository has four operations: loadAllTodos, createTodo, editTodo, deleteTodo.
The domain contains immutable task/content models, priority and typed failures, with
no Android types. A blank/whitespace-only title is invalid; titles are trimmed on save.
Notes may be empty. New tasks use Medium priority and done=false. Editing preserves
done; the list checkbox updates it in either direction. IDs are monotonically allocated
by the fake API. Tasks remain in insertion order, including when completed.

FakeTodoApi owns the only task collection. DTOs stay in data and are mapped by the
repository. Successful changes publish immutable snapshots through StateFlow.
loadAllTodos is a cold Flow: each collection first makes a delayed, fallible read,
then observes subsequent successful changes without further simulated requests.
Details obtains its task by ID from the first snapshot of the same operation, keeping
the requested four-operation contract. This is suitable for a small in-memory list;
a real backend would usually add a get-by-ID operation.

Requests suspend for 300–800 ms without blocking a thread. Reads and saves fail
independently with 15% probability before changing storage. Delete has latency but no
random failure, matching the requested load/save failure scope. There is no actual
HTTP transport, network permission, disk storage, cache, offline queue, or automatic
retry. A failed list load stays in its error state until the user presses Retry.
A fresh process starts empty; rotation keeps tasks and drafts through Application and
ViewModel lifetimes. Process-death restoration of drafts/tasks is intentionally absent.
A restored edit destination whose task is gone shows a not-found message with Back.

## State, concurrency and navigation

Each screen has immutable UiState, a single onAction entry point, and a destination
ViewModel. Rendering composables receive values and callbacks only. The navigation
boundary creates ViewModels, collects state with lifecycle awareness, shows toasts,
intercepts Back, and handles completed-operation navigation.

Navigation Compose was chosen for the requested two-screen navigation with
destination-scoped ViewModels. Route strings and optional ID parsing are confined to
TodoApp; domain/presentation state contains no navigation framework types.
See [Navigation Compose](https://developer.android.com/develop/ui/compose/navigation).
No shared architecture framework, database, or extra Gradle modules are needed.

viewModelScope owns requests/observations; leaving a destination cancels its work.
Fake requests use suspending delay and need no IO dispatcher. Cancellation propagates
without becoming a network error. A mutex protects ID allocation and atomic mutations;
latency occurs outside the mutex. Failures and cancellations before commit do not mutate.
Different tasks may update concurrently; duplicate writes to one task and duplicate
form submissions are blocked. Cards cannot open while their completion update is pending,
so an edit cannot race that checkbox update. Completion is shown after server success.

Create stays visible in loading, empty, content and error states. Initial/detail load
failures have explicit Retry. Save/delete disable form actions while pending. Failed
saves retain entered values and show “Error saving the todo”; failed completion leaves
the previous value and shows the same toast. Success returns to List. The list continues
observing the source while Details is open and receives successful changes directly.
If its initial subscription failed, it still requires manual Retry on return.

Navigation outcomes and toast requests remain in UiState until acknowledged at the
lifecycle boundary. This avoids dropping outcomes while the Activity is stopped.
These acknowledgements are process-local, not durable delivery guarantees.
Back confirms discard when the draft differs from its baseline. During a mutation,
Back is consumed until the operation finishes, avoiding ambiguous interrupted writes.

## Verification

JVM coroutine tests use injected latency/failure policies and virtual time to cover
delays, Flow updates, ordering, failed writes, explicit retries, cancellation, unique
IDs, missing tasks, validation, completion, submission deduplication and dirty drafts.
Compose tests cover toolbar availability, manual Retry, checkbox/card actions,
new-form defaults, navigation through create/complete/delete, and discard confirmation.
Previews cover list content/empty/loading/error, completed cards, and create/edit/error
details. Build, JVM tests, lint and connected tests are the relevant Gradle checks.
