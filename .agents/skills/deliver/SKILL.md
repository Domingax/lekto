---
name: deliver
description: "Take a ticket to a landed change: test-first, at the repo's test levels, reviewed, docs synced, committed."
disable-model-invocation: true
---

# Deliver

Take one ticket to a landed change. This is Lekto's fork of the `implement`
spine: its lines kept verbatim, plus a test-level gate and a doc sync.

Implement the work described by the user in the spec or tickets. *Done when every
acceptance criterion maps to a change you can describe.*

Use `/tdd` where possible, at pre-agreed seams. *Done when each seam has a test
that goes red for the right reason, then green.*

Prove the tests at the repo's levels: classify every change surface against
[the test levels](docs/testing.md#test-levels) — domain, seam, driver, parser, UI,
glue, bug — and for every level that table demands, show a red → green. *Done when
every surface is classified and every demanded level has its red → green; "some
tests were added" is not done.*

Run typechecking regularly, single test files regularly, and the full test suite
once at the end. *Done when `./gradlew check` is green.*

Once done, use `/code-review` to review the work. *Done when both axes have
reported and each finding is fixed or explicitly accepted.*

Sync the docs with `/sync-docs`. *Done when every documented surface the diff
touched is reported updated or unaffected.*

Commit your work to the current branch. *Done when the working tree is clean.*
