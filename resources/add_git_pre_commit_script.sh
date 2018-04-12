#!/bin/bash

PRE_COMMIT_HOOK_FILE=".git/hooks/pre-commit"

echo "#!/bin/bash" >> $PRE_COMMIT_HOOK_FILE
echo "echo \"Running linter\"" >> $PRE_COMMIT_HOOK_FILE
echo "mvn antrun:run@ktlint" >> $PRE_COMMIT_HOOK_FILE

chmod +x $PRE_COMMIT_HOOK_FILE
