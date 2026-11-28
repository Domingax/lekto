# Lekto — Agent Guide

Lekto is an open-source, local-first immersive reading app for language learning:
import a book, read it with words coloured by mastery, tap a word to look it up,
save vocabulary. Android is the first-class client and desktop is second; there
is no backend and all user data lives on the device in a **Vault** (ADR-0002).

The build is Gradle: `./gradlew`.

## Start here

| Document | Answers | Normative |
| -------- | ------- | --------- |
| [`README.md`](README.md) | What is the project, and how do I build and run it? | Yes, for the landing page |
| [`CONTEXT.md`](CONTEXT.md) | What does this domain term mean? | Yes, for vocabulary |
| [`docs/adr/`](docs/adr/) | Why was this decision made, and what did we reject? | Yes, for decisions |
| [`docs/research/`](docs/research/) | What did we investigate? | No |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Which modules exist and which dependencies are allowed? | Yes |
| [`docs/development.md`](docs/development.md) | How is the project built, tested and gated? | Yes |
| [`docs/platforms.md`](docs/platforms.md) | How are the Android and desktop clients built and packaged? | Yes |
| [`docs/dictionary-pipeline.md`](docs/dictionary-pipeline.md) | How are dictionary packs and the sample catalogue produced? | Yes |
| [`docs/contributing.md`](docs/contributing.md) | How do commits, pull requests, ADRs and `CONTEXT.md` work? | Yes |
| [`docs/dependencies.md`](docs/dependencies.md) | Which licences are allowed, and how are alerts triaged? | Yes |
| [`docs/design.md`](docs/design.md) | What does the product look and feel like? | Yes |
| [`guide/AGENTS.md`](guide/AGENTS.md) | How do agents update the end-user guide? | Yes, for the guide |

## Agent skills

- **Issue tracker** — issues and specs live in GitHub Issues:
  `docs/agents/issue-tracker.md`.
- **Triage labels** — five canonical roles: `docs/agents/triage-labels.md`.
- **Domain docs** — single-context, one `CONTEXT.md` and `docs/adr/`:
  `docs/agents/domain.md`.
