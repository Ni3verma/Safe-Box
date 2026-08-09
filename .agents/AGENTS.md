# Safe-Box Agent Behavioral Rules

## Comment & Documentation Integrity

- **NEVER remove existing comments or docstrings** unless the user explicitly requests their
  removal.
- Preserve all existing architectural context, explanatory comments, and KDoc blocks.
- **KDoc & Documentation for New Files:** Always add clear, informative class-level KDoc comments to
  all newly created classes, interfaces, objects, and enums explaining their architectural purpose.
  Add method-level docstrings with `@param` / `@return` explanations for non-obvious methods,
  cryptographic algorithms, parsing logic, or complex mathematical transformations (avoid redundant
  comments on trivial getters/setters).
- Feel free to modify, clarify, or add new well-structured comments to make the code more readable
  and self-documenting where required.

## Interaction Style & Tone

* **To-the-Point:** Provide strictly concise, substance-first answers. Skip conversational fluff,
  introductory filler, or generic greetings.
* **No "Yes Papa" & Proactive Pushback:** Never follow user prompts or ideas blindly. If a
  requested approach or change (e.g., running UI tests on minified/QA builds, using hacky sleeps, or
  sub-optimal architecture) is anti-pattern, structurally flawed, or deviates from Android industry
  standards, immediately and proactively challenge it upfront, explain the concrete technical
  drawbacks, and steer toward the standard solution before writing any code.

## Technical Stack Boundaries

* **Language:** 100% Kotlin utilizing clean, modern idioms.
* **UI Framework:** Jetpack Compose for all UI layers.
* **Dependency Injection:** Hilt.
* **Navigation:** Latest Jetpack Navigation library.
* **Build System:** Groovy-based `build.gradle` configuration files (**strictly NO Kotlin
  DSL / `build.gradle.kts`**).
* **Best Practices Reference:** Consult and apply official Android guidance and patterns
  from [android/skills](https://github.com/android/skills).

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
* **No Fully Qualified Names in Code:** Never inline fully qualified package names in code (e.g.,
  `java.nio.ByteBuffer.allocate` or `com.andryoga.safebox...`). Always add an `import` statement at
  the top of the file and use clean, short type names.
* **Analytics & Tracking:** Always define and log appropriate new analytics events using
  `AnalyticsHelper.kt` and `AnalyticsKey.kt` whenever implementing a new feature or user action.
    * **Dialog & UI Event Analytics Rule:** Whenever creating or displaying any user-facing dialog
      (such as rationale, confirmation, or security alert dialogs), always define and log specific
      analytics events in `AnalyticsKey.kt` and `AnalyticsHelper.kt` for dialog display (`*_SHOW`),
      primary action clicks (`*_ALLOW_CLICK`, `*_OPEN_SETTINGS_CLICK`), and dismissal/cancellation
      (`*_CANCEL_CLICK`).
    * **Analytics Unit Verification Rule:** Always write unit tests in the corresponding ViewModel
      test suites asserting that each UI event action correctly invokes
      `analyticsHelper.logEvent(AnalyticsKey.X)`.

## Code Formatting & Style Rules

* **No Extra Empty/Blank Lines:** Never introduce unnecessary empty or blank lines within import
  blocks, in-between code blocks, or multiple trailing empty lines at the end of files.
* **Trailing Commas:** Always include a trailing comma (`,`) for multi-line parameters, arguments,
  enum entries, and collection literals as per modern Kotlin best practices.

## Test Naming & Conventions

* **No Test Case Numbers in Method Names:** NEVER add test case numbers or arbitrary numerical
  prefixes/suffixes (e.g., `_TC1`, `_TC3`, `test01_`) in unit or UI test method names.
* **Clean & Descriptive:** Test names must be clean, readable, concise, and self-descriptive (e.g.,
  `emptyPasswordError_shouldShowBlankValidationText` or
  `clickTogglePasswordIcon_shouldToggleVisualTransformation`).