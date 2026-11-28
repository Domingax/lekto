# Contributing

## Commits

Conventional Commits, enforced by `.githooks/commit-msg`:

```text
<type>(<scope>): <description>
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`,
`chore`, `revert`. The scope is optional and names the module or area. A breaking
change takes `!` before the colon and a `BREAKING CHANGE:` footer. The subject is
imperative, lower-case and has no trailing period; the hook checks the shape, a
reviewer checks the prose.

Install the hook once per clone with `./scripts/setup.sh`.

## Pull requests

Keep the change small and single-purpose. Link the issue with `Closes #<n>` and
follow `.github/pull_request_template.md`:

- **Summary** — the smallest view that makes the change clear.
- **Evidence** — the red → green.
- **Merge Danger** — one-way or two-way door, and blast radius.

Run [the landing gate](development.md#commands) once on the final diff. CI's
`fast` lane also runs `koverXmlReport` and verifies the Roborazzi goldens; a local
`check` alone does not. The pull request is ready when CI is green.

## ADRs

Write an ADR in `docs/adr/NNNN-slug.md` when a decision is hard to reverse,
surprising without context, and the result of a real trade-off: a module
boundary, the on-disk format, the sync contract, the reader substrate, the
licence, or a reversal of an existing ADR. Number new ADRs in sequence (the next
free number); supersede a merged ADR rather than editing it.

## CONTEXT.md

Update `CONTEXT.md` in the same change that resolves or renames a domain term.
It holds definitions only, never implementation detail. If code uses a term the
glossary does not define, one of the two is wrong.
