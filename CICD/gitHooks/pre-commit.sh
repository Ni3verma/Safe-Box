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
  ./gradlew detekt --auto-correct >$OUTPUT
  EXIT_CODE=$?
  if [ $EXIT_CODE -ne 0 ]; then
    cat $OUTPUT
    rm $OUTPUT
    log_info "Detekt failed"
    DETEKT_REPORT_PATH="app/build/reports/detekt/detekt.html"

    # Check OS and open detekt failure report
    case "$(uname -s)" in
      Linux*)     xdg-open "$DETEKT_REPORT_PATH";;
       Darwin*)    open "$DETEKT_REPORT_PATH";;
      CYGWIN*|MINGW32*|MSYS*|MINGW*) start "" "$DETEKT_REPORT_PATH";;
      *)          echo "Cannot auto-open report on this OS. Please open $DETEKT_REPORT_PATH manually.";;
    esac

    exit 1 # fail the commit
  fi
  rm $OUTPUT
  log_info "detekt passed"
}

restricted_files=( releaseKeyStore.properties app/releaseKeyStore.jks app/google-services.json )
# added IFS so that space in a changed file doesnt cause issues in script
# as given in https://github.com/koalaman/shellcheck/wiki/SC2066#loop-over-each-line-with-globbing-hello-world-my-catpng
IFS='
'
for changedFile in $(git diff --name-only --cached); do
	for restricted_file in "${restricted_files[@]}"; do
		echo "$restricted_file"
	done | grep "$changedFile"
	if [ $? -eq 0 ]; then
		echo "ERROR : you cannot commit $changedFile as it contains your secret data"
		exit 1
	fi
done

log_info "Running detekt with auto correction"
run_detekt
exit 0
