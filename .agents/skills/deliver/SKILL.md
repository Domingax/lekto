---
name: deliver
description: "Take a ticket to a landed change: test-first, at the repo's test levels, reviewed, docs synced, committed, PR landed, then summarised plainly."
disable-model-invocation: true
---

# Deliver

Take one ticket to a landed change. This is Lekto's fork of the `implement`
spine: its lines kept verbatim, plus a test-level gate, a doc sync, a landing
step and a plain-language close.

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

Open the pull request and wait on it. Push the branch and open the pull request
against `main`, with the body `.github/pull_request_template.md` prescribes and
`Closes #<n>`. Then wait for every check to settle, and take each failure back
through the steps above — the build, the SonarCloud gate, a review comment — and
wait again. *Done when every check is green and no finding is outstanding.*

Close with a novice summary, in the user's language. A short summary a
non-engineer can follow: what changed, why it matters, and what they must still
do. Write it in the language the user writes to you in — the conversation's
language, not the repository's. The repo speaks English (`AGENTS.md`, the docs,
the commit messages); that is not the target. When the messages are too short to
tell the language, fall back to the environment locale (`LANG`/`LC_ALL`, so
`fr_FR.UTF-8` means French); when that is still unclear, ask. *Done when the
summary is in the user's language and carries no jargon a beginner would have to
look up.*
