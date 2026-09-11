#!/bin/bash

PRE_COMMIT_HOOK_FILE=".git/hooks/pre-commit"

echo "#!/bin/bash" >> $PRE_COMMIT_HOOK_FILE
echo "echo \"Running linter\"" >> $PRE_COMMIT_HOOK_FILE
echo "mvn exec:exec@ktlint-check" >> $PRE_COMMIT_HOOK_FILE

chmod +x $PRE_COMMIT_HOOK_FILE
