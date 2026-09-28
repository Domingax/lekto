# Project skills

The skills this project ships are a decision, not an accident of what happened to be
installed. This file is the ledger: what ships, what is deferred and why, and what was
considered and rejected. The set is maintained with `/write-a-project-skill`.

Two kinds of skill live under `.agents/skills/`:

- **Vendored engineering skills** — the general engineering flow, sourced from
  `mattpocock/skills` and pinned to a hash in `skills-lock.json`. That lock is the
  source of truth for the vendored set; this file only records verdicts on it.
- **Project skills** — skills that encode a task *this* repository repeats. They have
  no upstream, so their source of truth is this file.

## Vendored engineering skills

The vendored set is kept whole, with one cut: a skill is dropped when it encodes a
**build toolchain** or an **artifact type** this repository does not have. Workflow
adapters for other harnesses stay, since a contributor may run one. Everything not
listed below is kept.

| Dropped | Why |
| ------- | --- |
| `setup-ts-deep-modules` | TypeScript and dependency-cruiser; the modules here are Gradle/Kotlin. |
| `migrate-to-shoehorn` | TypeScript `as`-assertion migration; the tests here are Kotlin. |
| `setup-pre-commit` | Husky, lint-staged and Prettier; this repo's hooks live in `.githooks/`. |
| `scaffold-exercises` | Course exercise scaffolding; this repo ships an app, not lessons. |
| `writing-beats` | Article prose; this repo writes ADRs, skills and docs, not articles. |
| `writing-fragments` | Article prose, same reason. |
| `writing-shape` | Article prose, same reason. |

## Project skills

### Shipped

| Skill | Encodes | Exercised |
| ----- | ------- | --------- |
| `write-a-project-skill` | How a Lekto project skill is added, changed or dropped, and how this ledger is kept. | Ticket #4 ran both halves on the real repo: **Add** — created `SKILL.md` and `agents/openai.yaml`, set invocation, and registered this row; **Drop** — deleted the seven vendored skills above, removed their `skills-lock.json` entries, and recorded them. |

### Deferred

Recurring work whose subject does not exist yet. Each is written — and exercised —
when what it waits on lands; until then, shipping it would describe a seam that is
not there and rot into sediment.

| Candidate | Waiting on | Why it recurs |
| --------- | ---------- | ------------- |
| `add-a-sync-driver` | #26 (the `SyncTarget` seam), #27 (the first driver and its contract suite) | WebDAV is first, Dropbox is next; each driver repeats the same seam, capability query and contract run. |
| `add-a-dictionary-language-pair` | #17 (the pack pipeline) | EN↔FR ships first; further pairs are a build-time trim of the same pipeline. |
| `release-the-dictionary-pack` | #17, and the licence/attribution confirmation the spec flags | A separate CC BY-SA 4.0 artifact released on its own cadence. |
| `cut-a-release` | #29 (desktop packaging), #7 (CI lanes) | Every app release repeats signing, packaging and the licence check. |
| `add-a-parser-format` | #10 and #11 (the parser spikes), then the vault's parser seam | PDF is the immediate follow-up to EPUB/TXT, behind the same seam. |
| `add-a-test-seam-and-fake` | #5 (the harness) | Every seam gets an in-memory fake in `testkit`; the pattern is the harness's whole point. |
| `add-a-ci-lane` | #7 (the first lanes) | The golden, WebDAV-container and nightly-emulator lanes are each added once and repeated on other workflows. |
| `add-a-gradle-module` | The next module (none pending) | The next integration or tool repeats settings wiring, the module table and the boundary rules; with no module to add, there is no instance to exercise it on. |

### Rejected

| Candidate | Why not |
| --------- | ------- |
| `land-a-ticket` | Duplicates `/implement` plus the Contributing rules in `AGENTS.md`; with those in context it changes no behaviour. |
| `run-the-build` | `docs/build.md` is the source of truth; a skill would be a cache that goes stale. |
| `spec-a-ticket` | `/to-spec` and `/to-tickets` already own it. |
| `record-an-adr` | `/domain-modeling` owns the procedure, and `AGENTS.md` names the trigger and the numbering. |
| `update-the-glossary` | Same: `/domain-modeling` owns it, and `AGENTS.md` names when it is required. |
| `audit-the-skill-set` | Folded into `write-a-project-skill`, whose register and drop steps are the audit. |
