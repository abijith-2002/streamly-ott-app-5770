#!/bin/bash
cd /home/kavia/workspace/code-generation/streamly-ott-app-5770/mobile_frontend
./gradlew lint
LINT_EXIT_CODE=$?
if [ $LINT_EXIT_CODE -ne 0 ]; then
   exit 1
fi

