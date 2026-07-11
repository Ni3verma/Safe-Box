# Safe-Box Agent Behavioral Rules

## Comment & Documentation Integrity

- **NEVER remove existing comments or docstrings** unless the user explicitly requests their
  removal.
- Preserve all existing architectural context, explanatory comments, and KDoc blocks.
- Feel free to modify, clarify, or add new well-structured comments to make the code more readable
  and self-documenting where required.

## Interaction Style & Tone

* **To-the-Point:** Provide strictly concise, substance-first answers. Skip conversational fluff,
  introductory filler, or generic greetings.
* **No "Yes Papa":** Never blindly agree with proposed solutions. If an approach is sub-optimal,
  structurally flawed, or anti-pattern, directly challenge it and nudge toward the industry
  standard.

## Technical Stack Boundaries

* **Language:** 100% Kotlin utilizing clean, modern idioms.
* **UI Framework:** Jetpack Compose for all UI layers.
* **Dependency Injection:** Hilt.
* **Navigation:** Latest Jetpack Navigation library.
* **Build System:** Groovy-based `build.gradle` configuration files (**strictly NO Kotlin
  DSL / `build.gradle.kts`**).

## Architecture & Code Quality Guidelines

* **Testability First:** Structure all components to support clean Unit Testing (UTs). Avoid running
  heavy background pipelines directly inside `init {}` blocks without dependency injection or
  dispatcher control.
* **Reactive Data Pipelines:**
    * Maximize Unidirectional Data Flow (UDF).
    * Separate **Driving State** (MutableStateFlow representing raw user inputs like text fields or
      toggles) from **Derived State** (Read-only StateFlow representing database results, filtered
      data, or computed properties like `isLoading`).
    * Use the `combine` or `map` operators to compute final state objects atomically, ensuring the
      UI always reads a consistent snapshot of the data.
* **Main Safety:** Trust architecture component libraries (like Jetpack DataStore or Room) to manage
  their internal background execution loops. Do not pollute ViewModels with redundant
  `withContext(dispatchers.io)` blocks wrapping framework-level async operations.
* **Dispatcher Abstraction:** Never hardcode `Dispatchers.IO` or `Dispatchers.Default` inside
  ViewModels or Repositories. Inject a `DispatchersProvider` interface to guarantee predictable,
  synchronized virtual-time execution during unit testing via a `TestDispatcher`.