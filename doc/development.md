# Jibri Development
## Style
Code should follow the Kotlin style guide.  This style is enforced with ktlint in the project itself, linting can be executed by running `mvn verify` (which will build, run tests and lint) or `mvn antrun:run@ktlint` (which will run just the linting).  Jibri is a Kotlin codebase, so Kotlin should be used for development (save for extreme circumstances where falling back to Java is acceptable).

## Versioning
Jibri uses (annotated) tagged versions and follows semantic versioning.  Adding an annotated tag is done as follows:
```
git tag -a v1.4 -m "my version 1.4"
```
Tags are not pushed by default when doing `git push`, but a tag can be pushed like a remote branch:
```
git push origin v1.4
```
NOTE: Tagging should not be done as part of a PR, it will be handled separately.
