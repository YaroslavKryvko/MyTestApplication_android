# Todo architecture

## Structure and dependency ownership

The app has one Gradle module, `:app`, with package dependencies organized as
`presentation → domain ← data`. Packages do not enforce this direction; Kotlin
`internal` visibility applies to the whole module. Separate Gradle modules would enforce
boundaries but add configuration overhead for this two-screen app.

| Area | Responsibility |
| --- | --- |
| Domain | Immutable Todo/TodoContent models, Priority, typed failures, and the TodoRepository contract; no Android types |
| Data | Fake server storage, DTO mapping, title normalization, and API-error mapping |
| Presentation | Screen state, actions, ViewModels, Compose rendering, and navigation integration |
| Application/Activity | Construct dependencies and install the application UI |

`TodoApplication` is the composition root. Its lazy `todoRepository` property creates
one InMemoryTodoRepository and FakeTodoApi per process. Both destinations share that
source of truth. MainActivity passes the repository to TodoApp; destination factories
inject it into ViewModel constructors. ViewModels do not look up the Application.

Application ownership preserves tasks across Activity recreation, not process death.
It is sufficient for this small app without a DI framework or a separate dependency
container. A larger dependency graph could justify a container later without changing
this lifetime choice.

## Product scope and data contract

The intended workflow is one user editing one todo at a time, with sequential
interactions and no external changes or concurrent editors. No synchronization or
conflict-resolution layer is needed.

TodoRepository exposes four operations: loadAllTodos, createTodo, editTodo, and deleteTodo.
Titles must be non-blank; the repository trims them on create/edit. Notes are preserved
verbatim and may be empty. New forms default to Medium priority and incomplete.
Edits replace all content while preserving identity and insertion position; the details
form preserves completion, while the list checkbox changes it in either direction.

FakeTodoApi owns the only task collection and allocates increasing IDs. Its DTOs,
string priority representation, and infrastructure exceptions remain inside data.
The repository maps them into domain models and failures. This boundary keeps fake API
details out of presentation without introducing pass-through use cases or base classes.

## Fake server and Flow behavior

Every simulated request suspends for 300–800 ms. Loads and saves independently fail
with 15% probability before mutating data. Delete has latency but no random failure.
There is no HTTP transport, database, disk cache, offline queue, or automatic retry.

`loadAllTodos()` returns a cold Flow. Each collection makes one delayed, fallible
request, then subscribes to the fake's StateFlow for current and subsequent snapshots.
The subscription starts after the delay, so writes made during the request are included.
A failed initial request terminates that collection; collecting again retries it.

The list keeps collecting successful snapshots. Details uses `first()` to obtain one
snapshot, finds its todo by ID, and stops collecting so later emissions do not replace
the user's draft. This reuses the four-operation contract for the small in-memory list.

StateFlow uses equality and conflation: equal states are suppressed and slow collectors
may skip intermediate snapshots. Immutable lists and state copies represent current
rendering state, not an event history. StateFlow `update` lambdas only calculate state.

The repository's `mapApiErrors` helper maps FakeNetworkException to TodoFailure.Network
and MissingTodoException to TodoFailure.NotFound for mutations. The load Flow maps its
network exception separately. Other exceptions, including cancellation, propagate.

## State and rendering

Each destination has immutable UiState, read-only StateFlow, a sealed action contract,
and one `onAction` entry point. ViewModels own asynchronous work and state transitions.
Screen composables receive state and callbacks without obtaining a ViewModel or navigator.

The list represents loading, error, and loaded content explicitly. Priority filtering
is local: All, Low, Medium, or High. It derives visible todos from the full snapshot,
preserves ordering/completion, and never makes a request. No matches has a separate empty
message. The selected filter survives navigation and Activity recreation through the
list ViewModel; menu expansion remains local composable state.

Create remains available in loading, empty, filtered-empty, content, and error states.
Load errors offer Retry. Save failures retain the draft and show a toast; a failed
completion update keeps its previous value. Cards and checkboxes are disabled for todos
with pending completion updates. Save/delete disable form actions while running.

Back checks the draft against its original values and asks before discarding changes.
During a mutation, Back is consumed until the operation finishes.

Previews live separately in `TodoListPreviews.kt` and `TodoDetailsPreviews.kt`, alongside
their screen files. They render sample state with MyTestApplicationTheme and cover list
content/empty/loading/error, completed cards, and new/edit/error details. The details
toolbar uses a Material Symbols back icon with a localized label and RTL mirroring.

## Navigation, reloads, and retained outcomes

TodoApp owns Navigation Compose routes, ID parsing, destination ViewModel factories,
lifecycle collection, toast display, and back-stack operations. Only the todo ID is
passed to an edit destination; the form loads current content from the repository.

Details has one nullable `DetailsOutcome`:

| Value | Route behavior |
| --- | --- |
| null | Stay on Details; no pending return |
| TODO_CREATED | Request one fresh list load, then return to List |
| RETURN_TO_LIST | Return after editing, deleting, Back, or discard without a new load |

Successful mutations publish the outcome together with cleared busy/dirty state in one
update. Further edits, saves, and Back actions cannot overwrite a pending outcome.
The route acknowledges it with OutcomeHandled before popping Details. Toast requests
are also retained until acknowledged.

The post-creation load cancels the list's previous observation and starts a fresh
collection. It also recovers from an initial list-load failure. A failed reload shows
the error until explicit Retry; recomposition and lifecycle restarts do not retry it.
Editing and deletion reach an already-observing list through snapshots. Opening or
discarding a create form does not reload the list. Filtering does not change this policy.

## Coroutine ownership and lifecycle

`viewModelScope` owns requests and repository observations. Covering List with Details
retains the list back-stack entry and ViewModel, so its observation continues. Removing
a destination entry clears its ViewModel and cancels its work.

`collectAsStateWithLifecycle()` and `repeatOnLifecycle(STARTED)` pause UI collection
below STARTED. They do not cancel independently running ViewModel work. A save can
finish while the Activity is stopped; its retained outcome is handled when collection
restarts. Acknowledgement prevents replay across lifecycle restarts, but is not a
durable delivery guarantee across process death.

Suspending `delay` does not block a thread and needs no IO dispatcher. A mutex protects
ID allocation and snapshot mutation; simulated latency occurs outside its lock.
Pending-action guards suppress duplicate submissions. This does not introduce a global
mutation queue or multi-user consistency policy.

## Process death and missing IDs

A fresh process starts with an empty fake server. Tasks, drafts, filter selection, and
pending outcomes are not persisted. Activity recreation tests establish configuration
retention, not process-death survival.

Within the supported single-user flow, an old ID is only expected to become missing
after process death resets storage while navigation restores an edit destination.
Details detects an absent ID in its loaded snapshot and displays NOT_FOUND with Back.
Repository mutations also defensively report NotFound for absent IDs passed directly
to them; that is a contract check, not a separate external-deletion workflow.

## Test boundaries and validation

| Suite | Responsibility |
| --- | --- |
| InMemoryTodoRepositoryTest | Mapping, normalization, storage ordering, latency, failures, cancellation, and absent-ID contracts |
| TodoViewModelTest | Actions, state transitions, filters, retry policy, request counts, dirty drafts, and retained outcomes |
| TodoUiTest | Component rendering and callback emission with explicit state |
| TodoNavigationTest | Full app navigation, edit journey, Retry wiring, and discard confirmation |
| TodoCreationRefreshTest | Exactly one creation reload and no lifecycle replay |
| TodoLifecycleTest | Activity recreation, stop/resume, pending-operation guards, drafts, and filter retention |
| MainActivitySmokeTest | Production Activity/Application wiring, opening Create, and returning to List |

JVM tests use virtual time and injected failure/latency policies. Device lifecycle tests
hold requests with gates instead of sleeps. ViewModel tests reuse the deterministic
in-memory repository rather than duplicating storage logic in another fake.

The debug-only, non-exported TodoTestActivity reinstalls TodoApp after recreation using
a test-owned repository. Tests clear its dependency hook after closing their scenario.
The debug manifest registers this host; both are excluded from release builds.
MainActivitySmokeTest complements it by launching the real production entry point
without replacing dependencies. It accepts either initial random load outcome and
performs no random writes. Generated template tests have been removed.

Run the relevant checks from the repository root:

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The connected task requires a device or emulator. See [verification evidence](todo-verification.md)
for recorded results and limitations. Documentation updates do not imply a new test run.
