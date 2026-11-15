# Repository Guidelines

## Required Skills

- For new features or architecture changes, use `android-feature-mvi`.
- For Compose screen creation or refactoring, use `compose-screen`.
- For explicitly requested local code reviews, use `review-local`.
- For shared library design or public API changes, use `shared-library-design`.
- For REST/local persistence and synchronization work, use `android-data-reliability`.
- Read each applicable `SKILL.md` before starting the work.

## Engineering Expectations

- Before implementation, identify relevant requirements, failure cases, and existing project conventions. State important assumptions.
- Prefer the smallest solution that meets the requirements. Explain meaningful tradeoffs without introducing unnecessary abstractions.
- For asynchronous work, define coroutine ownership, cancellation, dispatcher responsibility, and error handling.
- For persisted or remote data, define the source of truth, offline behavior, refresh policy, and migration implications.
- Treat public APIs as contracts. Review compatibility before changing exposed types, signatures, defaults, or behavior.
- Validate behavioral changes with appropriate tests and relevant checks. Distinguish verified results from assumptions and unavailable checks.
- Review generated code as critically as handwritten code. Never claim a command or test ran unless it actually ran.
- Keep credentials and sensitive user data out of source code and logs.
- Document significant architectural decisions with their context, alternatives, and consequences.

## Project Structure & Module Organization

This is a single-module Android application built with Kotlin, Jetpack Compose, and Material 3.

- `app/src/main/java/com/example/mytestapplication/` contains `MainActivity.kt` and composables; `ui/theme/` holds colors, typography, and the application theme.
- `app/src/main/res/` contains strings, themes, launcher icons, and XML configuration. The manifest is `app/src/main/AndroidManifest.xml`.
- `app/src/test/` holds local JVM tests; `app/src/androidTest/` holds device tests.
- `app/build.gradle.kts` configures the application. Shared dependency versions and aliases live in `gradle/libs.versions.toml`.

## Build, Test, and Development Commands

Run commands from the repository root using the Gradle wrapper:

- `./gradlew :app:assembleDebug` builds the debug APK under `app/build/outputs/apk/debug/`.
- `./gradlew :app:testDebugUnitTest` runs local unit tests.
- `./gradlew :app:connectedDebugAndroidTest` runs instrumented tests on a connected device or emulator.
- `./gradlew :app:lintDebug` runs Android Lint.
- `./gradlew :app:installDebug` installs the debug application; launch it from the device launcher or Android Studio.

Configure the Android SDK in Android Studio or `local.properties`. The project compiles against SDK 37, supports API 29+, and specifies a Gradle daemon JVM toolchain of Java 25; Java source compatibility is 11.

## Coding Style & Naming Conventions

Use four-space indentation and follow existing Kotlin formatting. Use `PascalCase` for classes and UI composables, `camelCase` for ordinary functions and properties, and `snake_case` for Android resource names. Keep packages under `com.example.mytestapplication`. Reuse `MyTestApplicationTheme` and provide previews for new composables where useful. Manage dependencies through the version catalog. No dedicated formatter or Kotlin style linter is configured; use Android Studio formatting and Android Lint.

## Testing Guidelines

Use JUnit 4 for local logic tests and AndroidJUnit4 with AndroidX Test for instrumentation. Compose UI testing and Espresso dependencies are available. Name test classes `*Test` and use descriptive method names. Add tests for changed behavior in the appropriate source set. No coverage threshold is configured.

## Commit & Pull Request Guidelines
Pull requests should explain the change, link related issues when applicable, and report validation commands and results. Include screenshots for UI changes. Keep generated build output, machine-specific SDK paths, and credentials out of commits.

Never run `git commit`, `git reset`, or `git push` automatically. Run these commands only when the user explicitly requests them; the final decision to commit, reset, or push belongs to the user. Other Git commands (such as `git diff`, `git log`, and `git status`) may be used as needed within the task's scope.
