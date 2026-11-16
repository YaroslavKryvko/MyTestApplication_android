# Todo application

A two-screen Android app built with Kotlin, Jetpack Compose, and Material 3.
Create tasks, organize them by priority, and track completion.

<p align="center">
  <img src="docs/todo-list.png" width="22%" alt="Dark-mode empty list with priority filter and Create action">&nbsp;
  <img src="docs/todo-create.png" width="22%" alt="Dark-mode new todo form with Medium priority selected">&nbsp;
  <img src="docs/todo-list-sample.png" width="22%" alt="Dark-mode list showing Plan the week with High priority and its completion checkbox unchecked">&nbsp;
  <img src="docs/todo-sample.png" width="22%" alt="Dark-mode saved todo with a title, notes, and High priority">
</p>

Captured from the app on Pixel 3 / Android 11.

- Create, edit, delete, and complete todos; filter the list by priority.
- Recover from load/save failures with explicit retry and retained form input.
- Confirm before discarding unsaved changes and prevent duplicate submissions.
- Keep drafts and pending operations through Activity recreation.

The app uses MVI presentation state, destination-scoped ViewModels, and a
`presentation → domain ← data` dependency direction with manual constructor injection.
An in-memory fake server simulates latency and failures; tasks live for the current process.

Licensed under [Apache 2.0](LICENSE).
