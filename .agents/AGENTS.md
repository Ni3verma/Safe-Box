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
* **No "Yes Papa" & Proactive Pushback:** Never follow user prompts, review comments, or
  ideas blindly. This rule applies universally across all tasks, decisions, and architectural
  aspects. Whenever any requested approach,
  suggestion, feature, test, dependency, optimization, refactoring, or design change is
  anti-pattern, structurally flawed, premature, out of scope, or deviates from Android industry
  standards / established app architecture, immediately and proactively challenge it upfront in
  the chat. Detail the concrete technical drawbacks or trade-offs, explain the rationale, and steer
  toward the standard solution before writing or modifying any code.
* **Architecture Consistency & Advance Notification:** Always adhere strictly to established app
  patterns. If there is a compelling reason to deviate from the
  established app architecture or standard, proactively push back and inform the user upfront in the
  chat before making changes.

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
* **Test-Only Methods & Visibility:** Avoid adding production methods solely for testing. If
  there is no clean, viable alternative to exposing an internal hook or state for testing, always
  annotate it explicitly with `@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)` (or
  `PACKAGE_PRIVATE`) and document its purpose.
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

## Knowledge Base Maintenance

The project keeps a durable, committed knowledge base so that hard-won findings survive across
sessions instead of being rediscovered. Treat it as part of the deliverable, not an afterthought.

### Read before you work

* **Always read [`PROJECT_FACTS.md`](PROJECT_FACTS.md) before starting any non-trivial task.** It is
  short by design and will usually save several rounds of exploration.
* Load the relevant `skills/<name>/SKILL.md` before running builds, tests or release tooling rather
  than rediscovering commands and flags.
* Before declaring something impossible or proposing a large change of approach, check
  [`docs/decisions/`](../docs/decisions/README.md). It may already have been investigated and
  settled, with evidence.

### Write after you learn

Whenever you discover something durable, record it **in the same change** that produced it:

| What you learned | Where it goes |
|---|---|
| A verifiable invariant about the repo | `.agents/PROJECT_FACTS.md` |
| A command, flag, environment quirk or trap | the relevant `.agents/skills/<name>/SKILL.md` |
| An explanation, design or data-format detail | `docs/` |
| A decision that was expensive to reach, or an approach that was ruled out | a new ADR in `docs/decisions/` |

A finding qualifies as durable if re-deriving it would cost more than a couple of minutes, or if a
future reader would plausibly reach the wrong conclusion without it. Debugging noise and one-off
observations do not qualify.

### Curate, do not accumulate

Creating new files is encouraged **when the topic is genuinely new**. Otherwise the default is to
extend or correct an existing file. An unmaintained knowledge base is worse than none, because it
is confidently wrong.

* **Update in place over appending.** If a fact changes, fix the existing entry; never leave two
  entries that contradict each other.
* **Delete what is no longer true.** Stale guidance actively misleads.
* **Budget the always-read file.** `PROJECT_FACTS.md` stays under ~150 lines. If it grows past
  that, move detail into `docs/` or a skill and leave a one-line pointer behind.
* **Every fact must carry its evidence** — the command, file path or observation that establishes
  it — and a date when it could plausibly go stale.
* **Prefer enforcement over prose.** If a rule can be expressed as a lint rule, a detekt/ktlint
  rule, a unit test or a CI check, do that instead of documenting it. Executable knowledge cannot
  drift; a paragraph can. Documentation is the fallback, not the first choice.
* **Split a file when it exceeds roughly 300 lines** or covers two unrelated topics. New files must
  be linked from [`docs/README.md`](../docs/README.md) or from the relevant index, otherwise nobody
  will find them.
* **Validate before committing:** the pre-commit hook greps staged markdown for closing tags from
  the agent's own tool-call format leaking into file tails, which has shipped before. Broken
  relative links are checked in CI by [lychee](https://lychee.cli.rs); to check them locally,
  install it and run
  `lychee --offline '.agents/**/*.md' 'docs/**/*.md' 'upgrade-test/**/*.md'`.