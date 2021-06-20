#!/usr/bin/env bash

# !!!!!!!!!!!!!!!!!!  IMPORTANT  !!!!!!!!!!!!!!!!!!!!!!!!
# whenever this file is edited, ALWAYS rebuild the app to copy changes to git hook

log_info() {
  echo ""
  echo "***********************************************"
  echo "    $1    "
  echo "***********************************************"
  echo ""
}

run_detekt() {
  OUTPUT="/tmp/detekt-$(date +%s)"
  ./gradlew detekt >$OUTPUT
  EXIT_CODE=$?
  if [ $EXIT_CODE -ne 0 ]; then
    cat $OUTPUT
    rm $OUTPUT
    log_info "Detekt failed"
    start app/build/reports/detekt/detekt.html
    exit 1
  fi
  rm $OUTPUT
  log_info "detekt passed"
}

log_info "Autoformatting using ktlint"

./gradlew app:ktlintFormat

log_info "ktlint Autoformatting done"

git add .

log_info "Running ktlint check"
./gradlew app:ktlintCheck --daemon

status=$?

if [ "$status" = 0 ]; then
  log_info "ktlint passed. Running detekt"

  run_detekt
  exit 0
else
  log_info "ktlint Failed. Please fix issues"
  exit 1
fi
