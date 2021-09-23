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

restricted_files=( releaseKeyStore.properties app/releaseKeyStore.jks )
for changedFile in `git diff --name-only --cached`; do
	for restricted_file in "${restricted_files[@]}"; do
		echo "$restricted_file"
	done | grep "$changedFile"
	if [ $? -eq 0 ]; then
		echo "ERROR : you cannot commit $changedFile as it contains your secret data"
		exit 1
	fi
done

log_info "Running ktlint check"
./gradlew app:ktlintCheck --daemon
status=$?

if [ "$status" = 0 ]; then
  log_info "ktlint passed. Running detekt"
  run_detekt
  exit 0
else
  log_info "ktlint failed, auto-formatting"
  ./gradlew app:ktlintFormat
  status=$?
  log_info "ktlint Autoformatting done"

  if [ "$status" = 0 ]; then
    log_info "autoformatting done, commit changes again after adding required files"
    exit 1
  else
    log_info "Autoformatting failed, manual action required"
    exit 1
  fi
fi
