---
name: sync-docs
description: "Reconcile Lekto's documentation with a code change: the glossary, ADRs, architecture, development, platform, dictionary, dependency and contribution docs. Use after a change that adds or renames a domain term, records or reverses a decision, or moves a command, seam, test level or module boundary."
---

# Sync docs

Keep the repository's documentation in step with the code, and leave a record
when nothing needs to change. `AGENTS.md` owns the document index; this skill
owns the editing contract and the sequence.

## Documents

`AGENTS.md`'s **Start here** table is the only index. Use it; do not copy it here.

## Contract

1. **Reference docs describe the present.** Write what is true now, in the
   present tense. Leave the history to `git log`, the ADRs and `docs/research/`.
2. **Decisions live in ADRs.** A reference doc links the ADR instead of
   retelling the trade-off. Research is linked, never treated as policy.
3. **One fact, one home.** Build and test commands live in
   `docs/development.md`, module boundaries in `ARCHITECTURE.md`, licence
   policy in `docs/dependencies.md`, vocabulary in `CONTEXT.md`. Other docs link.
4. **State the audience and scope in the first paragraph.** A doc that answers
   two unrelated questions is two docs.
5. **Prefer tables, commands and invariants to narrative.** Keep `AGENTS.md`
   under 60 lines and reference docs to at most 400; ADRs, research and the
   design language may be longer.
6. **Reference docs do not cite issue or ticket numbers.** Link an ADR, a test
   or a source file instead.
7. **Do not restate a skill.** Test-first discipline belongs to `tdd`; this
   skill owns where a change is proved, not how to write the test.

## Steps

1. **Read the diff** since the branch base (`git diff <base>...HEAD`) and list
   every documented surface it touches: a term, a decision, a command, a seam,
   a test level, a module boundary or a user-visible behaviour.
2. **Classify each** against the `AGENTS.md` table — `CONTEXT.md` for a term, an
   ADR for a decision, `docs/development.md` for a command or test level, the
   guide for a user-visible change.
3. **Update** each affected document, or record it as unaffected. When a
   document moves, update the `AGENTS.md` table and every code comment or
   cross-document link that names it.
4. **Check the links** (`git diff --check`; resolve every relative link and
   anchor).
5. **Check the guide** — a user-visible change updates `guide/` in the same pull
   request.

*Done when every documented surface named in the diff is either updated or
explicitly recorded as unaffected, every link resolves, and `AGENTS.md` still
names the documents correctly.*

For a large diff, dispatch this as a subagent so it reads the change with fresh
eyes.
