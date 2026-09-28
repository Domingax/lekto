# Lekto — Agent Guide

Lekto is an open-source, local-first immersive reading app for language learning:
import a book, read it with words coloured by mastery, tap a word to look it up,
save vocabulary. Android is the first-class client and a desktop client is second.
There is no backend, and all user data lives on the device in a **Vault**
(ADR-0002).

This file is the entry point for any agent arriving cold. It says where things
live, the one command that builds and tests, and the conventions a change must
satisfy. Read the sources below before changing behaviour.

## Required reading

- **`CONTEXT.md`** — the domain glossary. Use its terms exactly; do not invent
  synonyms. `Vault`, `Book`, `Word token`, `Mastery level`, `Vocabulary entry`,
  `Lemma`, `Derived asset` mean what it says they mean.
- **`docs/adr/`** — the architecture decisions. Each is authoritative: where code
  or a plan disagrees with an ADR, the ADR wins and the other one is wrong.
- **`docs/build.md`** — the pinned toolchain, the platform commands, and the
  Android-SDK detection rules.

## Where each module lives

`core/` (the domain), `testkit/` (contract suites and fakes, shared by tests),
`integrations/webdav/` (the first sync driver), `app/` (the Compose Multiplatform
application, Android + desktop) and `tools/dictionaries/` (the offline
dictionary-pack pipeline). Each module's responsibility is tabled in
[`docs/build.md`](docs/build.md#modules); what follows is the boundary between them,
which that table does not state.

## Build and test — one command

```sh
./gradlew check
```

It builds every module and runs the JVM test suites — the domain suite in `core`,
and the UI-semantics suite as it lands. A clean checkout needs only a JVM to
run the wrapper — Gradle, the pinned JDK and all dependencies are provisioned by
the build. The Android SDK is optional; without one the project builds as a
desktop/JVM project. See `docs/build.md` for the pinned versions, the
Android/desktop commands, and the dictionary pipeline.

`./gradlew check` also installs the git hooks that enforce the commit convention
below, so a fresh clone is guarded from its first build.

## Module boundaries you must not cross

Dependencies point inward. Reversing an arrow is an architectural change and needs
an ADR.

```
app ──────────────► core
integrations/* ───► core
testkit ──────────► core        (shared contract surface)
tools/dictionaries              (standalone)
core ─────────────► nothing     (no app module, no Android)
```

- `core` depends on no other Lekto module. It is pure Kotlin in `commonMain`.
- `integrations/*` depend on `core` through its published seams; `core` never
  names an integration.
- Everything may depend on `core`; nothing depends on `app`.
- `testkit` exists because a KMP `commonTest` set cannot be shared by any other
  means; it is test-scoped and must never reach production code.
- `tools/dictionaries` is standalone and stays off the application CI path.

Architecture tests enforce these rules.

## Contributing

### Commits — Conventional Commits

Every commit subject is `<type>(<scope>): <description>` — imperative, lower-case,
no trailing period:

```
feat(reader): colour word tokens by mastery
fix(core): keep the tombstone on delete
docs(adr): record the sync seam
```

Allowed types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`,
`ci`, `chore`, `revert`. The scope is optional and names the module or area (`core`,
`testkit`, `webdav`, `app`, `dictionaries`, `build`, `ci`, `docs`). A breaking change
takes a `!` before the colon and a `BREAKING CHANGE:` footer.

**Enforced** by `.githooks/commit-msg`, which checks the shape above: type, optional
lower-case scope, colon, non-empty description. The imperative, lower-case and
no-trailing-period rules are review points, not machine checks — a human or the PR
review holds those. `./gradlew check` installs the hook, as does, by hand:

```sh
git config --local core.hooksPath .githooks
```

### Branches

One branch per issue, named `<owner>/issue-<n>` (for example `Domingax/issue-3`).
Branch from `main` and open the pull request against `main`.

### Pull requests

Keep the change small and single-purpose, link its issue with `Closes #<n>`, and
get `./gradlew check` green first. The body follows
`.github/pull_request_template.md` and has three parts:

- **Summary** — the smallest view that makes the change clear: a diff sketch, a
  call tree, or a decision list.
- **Evidence** — the red → green: the failing test/output before, the passing one
  after.
- **Merge Danger** — is this a one-way or a two-way door, and how wide is the
  blast radius?

### When an ADR is required

Write an ADR in `docs/adr/NNNN-slug.md` when the decision is hard to reverse,
surprising without context, and the result of a real trade-off. In this repo that
covers a module boundary, the on-disk record format, the sync contract, the reader
substrate, the licence, or any change that reverses an existing ADR. Number ADRs
in sequence; supersede a merged ADR rather than editing it.

### When `CONTEXT.md` must be updated

Update `CONTEXT.md` in the same change that resolves or renames a domain term. The
glossary is the single source of truth for domain vocabulary: if code uses a term
the glossary does not define, one of the two is wrong. `CONTEXT.md` holds
definitions only, never implementation detail.

## Agent skills

### Issue tracker

Issues and specs live in this repo's GitHub Issues. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical roles, default strings. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

### Project skills

The skills this project ships, defers and rejects live in `docs/agents/skills.md`,
which is also the standard for adding one.

## Licence

Source is **AGPL-3.0** (ADR-0011). Every dependency, including test-scope, must be
AGPL-compatible. The dictionary pack is a separate CC BY-SA 4.0 artifact and is
never committed here.
