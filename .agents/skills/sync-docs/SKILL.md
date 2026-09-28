---
name: sync-docs
description: "Reconcile the repo's docs with a code change: the glossary, ADRs, and the build, test and module docs. Use after a change that adds or renames a domain term, records or reverses a decision, or moves a command, seam or test level."
---

# Sync docs

Bring the repository's documents back in line with what the code now does, and
leave a record when nothing needs to change. `AGENTS.md` owns the rules for when a
document must move — read them there; this skill only sequences the work.

## Documents and what they own

- `CONTEXT.md` — domain vocabulary only, never implementation detail.
- `docs/adr/` — hard-to-reverse, surprising, trade-off decisions.
- `docs/build.md` — the toolchain, the commands, the module boundaries.
- `docs/testing.md` — the harness, the seams, the test levels.
- `README.md` — the module table.

## Steps

1. **Read the diff** since the branch base (`git diff <base>...HEAD`) and list every
   documented surface it touches: a term, a decision, a command, a seam, a module.
2. **Classify each** against the rules in `AGENTS.md` — `CONTEXT.md` for a term, an
   ADR for a decision, a build or test doc for a command or seam.
3. **Update** each affected document, or record it as unaffected.

*Done when every documented surface named in the diff is either updated or
explicitly recorded as unaffected — not when the first matching document is found.*

For a large diff, dispatch this as a subagent so it reads the change with fresh eyes.
