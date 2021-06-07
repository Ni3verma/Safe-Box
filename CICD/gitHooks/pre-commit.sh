#!/usr/bin/env bash

# !!!!!!!!!!!!!!!!!!  IMPORTANT  !!!!!!!!!!!!!!!!!!!!!!!!
# whenever this file is edited, ALWAYS rebuild the app to copy changes to git hook

echo "Running detekt check..."
OUTPUT="/tmp/detekt-$(date +%s)"
./gradlew detekt >$OUTPUT
EXIT_CODE=$?
if [ $EXIT_CODE -ne 0 ]; then
  cat $OUTPUT
  rm $OUTPUT
  echo ""
  echo "***********************************************"
  echo "                 Detekt failed                 "
  echo " Please fix the above issues before committing "
  echo "***********************************************"
  echo ""
  exit $EXIT_CODE
fi
rm $OUTPUT
echo "**********detekt passed"

echo "Running ktlint check"
./gradlew app:ktlintCheck --daemon

status=$?

if [ "$status" = 0 ]; then
  echo "**********ktlint passed"
else
  echo ""
  echo "***********************************************"
  echo "                 ktlint failed                 "
  echo " Running ktlint format to automatically fix issues "
  echo "***********************************************"
  echo ""

  ./gradlew app:ktlintFormat

  echo ""
  echo "***********************************************"
  echo "             ktlint Autoformatting done        "
  echo "***********************************************"
  echo ""

  status=$?
  if [ "$status" = 0 ]; then
    echo ""
    echo ""
    echo "***********************************************"
    echo "                 ktlint PASSED               "
    echo " Please commit your changes again after git add "
    echo "***********************************************"
    echo ""

  else
    echo ""
    echo "***********************************************"
    echo "                 ktlint failed AGAIN               "
    echo " Please manually fix issues that could not be automatically fixed by ktlintFormat "
    echo "***********************************************"
    echo ""
    exit 0
  fi
  exit 1
fi
