#!/usr/bin/env bash

# !!!!!!!!!!!!!!!!!!  IMPORTANT  !!!!!!!!!!!!!!!!!!!!!!!!
# Editing this file does NOT update the hook git runs. The installed copy lives at
# .git/hooks/pre-commit and must be refreshed by hand:
#
#   cp CICD/gitHooks/pre-commit.sh .git/hooks/pre-commit && chmod +x .git/hooks/pre-commit
#   git hook run pre-commit
#
# There used to be a `copyGitHooks` Gradle task for this, defined in CICD/cicd.gradle, but that
# file stopped being applied by any build script in 331ee64 ("Compose UI - v2.x"). The task no
# longer exists, which is why an installed hook can silently drift years behind this file.
# Check for drift with: diff .git/hooks/pre-commit CICD/gitHooks/pre-commit.sh

# Exit immediately if a command exits with a non-zero status
set -e
# Treat unset variables as an error
set -u
# The exit status of a pipeline is the exit status of the last command that exited with a non-zero status
set -o pipefail

# --- Configuration Variables ---
# Determine the absolute path to gradlew for robust execution
# Git hooks execute from the repository root, so $(pwd) will be the root.
DETEKT_GRADLE_COMMAND="$(pwd)/gradlew" # Absolute path to gradlew
DETEKT_GRADLE_TASK="detekt"             # The specific Gradle task

# Path to the Detekt HTML report relative to the project root
DETEKT_REPORT_DIR="app/build/reports/detekt"
DETEKT_HTML_REPORT_PATH="${DETEKT_REPORT_DIR}/detekt.html"

# Validates the committed documentation set: stray agent tool markup and broken relative links.
# Cheap enough (well under a second, no Gradle) to run on every commit.
DOCS_CHECK_SCRIPT="scripts/check_docs.py"

# Prefix for log messages from this script
LOG_PREFIX="[PRE-COMMIT-HOOK]"

# --- Restricted Files Configuration ---
# These files should not be commited to VCS
declare -a RESTRICTED_FILE_PATTERNS=(
  "releaseKeyStore.properties"
  "app/releaseKeyStore.jks"
  "app/google-services.json"
#  "app/src/main/java/com/andryoga/safebox/secrets.kt" # Example: an actual secret file
#  "credentials/api_keys.properties"                    # Example: a credentials file
#  "*.env"                                             # Example: any .env file
)

# --- Helper Functions ---

# Logs informational messages to stdout
log_info() {
  echo "$LOG_PREFIX INFO: $*"
}

# Logs error messages to stderr
log_error() {
  echo "$LOG_PREFIX ERROR: $*" >&2
}

# Attempts to open the specified report file in the default web browser
open_report_in_browser() {
  local report_path="$1"
  log_info "Attempting to open report: $report_path"

  if [ ! -f "$report_path" ]; then
    log_error "Report not found at '$report_path'. Please check Detekt configuration or path."
    return 1 # Indicate failure to open
  fi

  # Attempt to open the report without exiting the script on failure
  case "$(uname -s)" in
    Linux*)     xdg-open "$report_path" &> /dev/null || log_error "Failed to open report with xdg-open. Is a desktop environment running?";;
    Darwin*)    open "$report_path" &> /dev/null || log_error "Failed to open report with 'open' command.";;
    CYGWIN*|MINGW32*|MSYS*|MINGW*) start "" "$report_path" &> /dev/null || log_error "Failed to open report with 'start' command.";;
    *)          log_error "Cannot auto-open report on this OS. Please open $report_path manually.";;
  esac
}

# --- Pre-requisite Check: Restricted Files ---
# Checks if any staged changes contain restricted files/patterns.
# Returns 0 if no restricted files found, 1 if restricted files are found.
check_restricted_files() {
  log_info "Checking for restricted files in staged changes..."
  local restricted_file_found=0 # Flag to indicate if a restricted file is found

  # Loop over staged files (git diff --name-only --cached) line by line
  # using 'while IFS= read -r' with process substitution to ensure 'return' works.
  while IFS= read -r changed_file; do
    # log_info "Checking staged file: $changed_file" # Uncomment for detailed debug
    for restricted_pattern in "${RESTRICTED_FILE_PATTERNS[@]}"; do
      # Use bash's [[ ... == pattern ]] for glob matching
      if [[ "$changed_file" == $restricted_pattern ]]; then
        log_error "RESTRICTED FILE DETECTED: You cannot commit '$changed_file' as it matches restricted pattern '$restricted_pattern'."
        log_error "Please remove this file from your staged changes before committing."
        restricted_file_found=1 # Set the flag
        break 2 # Break both inner (for) and outer (while) loops
      fi
    done
  done < <(git diff --name-only --cached) # Process substitution feeds 'while' loop

  return "$restricted_file_found" # Return the flag
}

# --- Documentation Check Logic ---
# Runs scripts/check_docs.py over the curated markdown set.
# Returns 0 on success or when the check cannot be run, 1 when the check itself fails.
#
# Deliberately non-fatal when python3 or the script is missing: a contributor without python3
# should not be blocked from committing Kotlin, and this check guards documentation only.
run_docs_check() {
  if [ ! -f "$DOCS_CHECK_SCRIPT" ]; then
    log_info "Skipping documentation check, $DOCS_CHECK_SCRIPT not found."
    return 0
  fi

  if ! command -v python3 &> /dev/null; then
    log_info "Skipping documentation check, python3 is not on PATH."
    return 0
  fi

  log_info "Validating documentation..."

  # The script prints the offending file, line and reason, so let its output through unfiltered.
  if python3 "$DOCS_CHECK_SCRIPT"; then
    return 0
  fi

  log_error "Documentation check failed. Fix the problems listed above, or re-run with:"
  log_error "  python3 $DOCS_CHECK_SCRIPT"
  return 1
}

# --- Main Detekt Check Logic ---
# Runs Detekt workflow with auto-correction and validation.
# Returns 0 on success, 1 on failure (if unfixable issues remain).
run_detekt_workflow() {
  local temp_output_file
  temp_output_file=$(mktemp) # Secure way to create a temporary file
  local detekt_auto_correct_exit_code
  local detekt_validation_exit_code

  log_info "Running Detekt with auto-correction (first pass)..."

  # 1. Run Detekt with auto-correct and capture its output and exit code
  # We use 'eval' to allow for complex commands if needed, but for simple paths it's fine.
  if "$DETEKT_GRADLE_COMMAND" "$DETEKT_GRADLE_TASK" --auto-correct &> "$temp_output_file"; then
    detekt_auto_correct_exit_code=0 # Command succeeded
  else
    detekt_auto_correct_exit_code=$? # Command failed (non-zero exit code)
  fi

  if [ "$detekt_auto_correct_exit_code" -ne 0 ]; then
    # A missing task is not a code-quality failure, and must not block the commit. The Detekt
    # plugin is declared with `apply false` and CICD/cicd.gradle - which registers the task and
    # points it at CICD/detekt.yml - is not applied by any build script, so on this tree
    # `./gradlew detekt` fails because the task does not exist. Treat that as "unavailable".
    #
    # Gradle words this two different ways depending on how the task was named, so match both:
    #   bare name      -> "Task 'detekt' not found in root project ... and its subprojects."
    #   qualified path -> "Cannot locate tasks that match ':app:detekt'"
    if grep -qE "Cannot locate tasks that match|Task '.*' not found in root project" "$temp_output_file"; then
      log_error "Detekt task is not available in this build, skipping the Detekt check."
      log_error "CICD/cicd.gradle is not applied by any build script, so the task is never registered."
      rm -f "$temp_output_file"
      return 0
    fi

    log_info "Detekt --auto-correct detected fixable issues (first pass exit code: $detekt_auto_correct_exit_code)."
    log_info "Attempting to stage auto-corrected changes and validate (second pass)..."

    # 2. Stage any changes made by auto-correct (modifies staged files)
    # This is crucial for the second Detekt pass to operate on the *fixed* code.
    git add -u # Only stages modified/deleted files, not new untracked files

    # 3. Run Detekt again WITHOUT auto-correct to validate the (now staged) code
    # We redirect output of this second run to /dev/null as we only care about its exit status.
    if "$DETEKT_GRADLE_COMMAND" "$DETEKT_GRADLE_TASK" &> /dev/null; then
      detekt_validation_exit_code=0 # Command succeeded
    else
      detekt_validation_exit_code=$? # Command failed (non-zero exit code)
    fi

    if [ "$detekt_validation_exit_code" -ne 0 ]; then
      log_error "Detekt still found unfixable issues after auto-correction (second pass exit code: $detekt_validation_exit_code)."
      log_error "----------------------------------------------------"
      log_error "Detekt Output (from first pass --auto-correct, showing remaining issues):"
      cat "$temp_output_file" >&2 # Print the full output from the initial auto-correct run
      log_error "----------------------------------------------------"
      open_report_in_browser "$DETEKT_HTML_REPORT_PATH" # Only open report on final failure
      rm -f "$temp_output_file" # Clean up temporary file
      return 1 # Indicate that unfixable issues remain, so the commit should fail
    else
      # Detekt passed on the second run, meaning auto-correct fixed everything.
      log_info "Detekt auto-correction fixed all detected issues."
      log_info "The corrected changes have been automatically staged."
      rm -f "$temp_output_file" # Clean up temporary file
      return 0 # Indicate success, commit can proceed
    fi
  else
    # Initial --auto-correct run passed (exit code 0), meaning no issues were found or all were fixed immediately.
    log_info "Detekt auto-correction found no issues or fixed all immediately."
    rm -f "$temp_output_file" # Clean up temporary file
    return 0 # Indicate success, commit can proceed
  fi
}

# --- Script Execution Flow ---

# 1. Check for restricted files first
log_info "Starting pre-commit checks..."
if ! check_restricted_files; then
  log_error "Restricted file check failed. Aborting commit."
  exit 1 # Fail the Git commit
fi

# 2. Validate documentation. Ordered before Detekt because it takes milliseconds and needs no
# Gradle daemon, so a documentation problem surfaces without paying for a build first.
if ! run_docs_check; then
  log_error "Documentation pre-commit check failed. Aborting commit."
  exit 1 # Fail the Git commit
fi

# 3. Proceed with Detekt checks
if ! run_detekt_workflow; then
  log_error "Detekt pre-commit check failed. Aborting commit."
  exit 1 # Fail the Git commit
fi

# If both checks pass, allow the commit
log_info "All pre-commit checks passed successfully. Allowing commit."
exit 0
