# The mastery scale reads recognized < familiar < learned, and its persisted names break without a shim

CONTEXT.md's five-point **Mastery level** had its middle names out of order: level 1
was `FAMILIAR` and level 2 was `RECOGNIZED`, but one recognises a word before it
becomes familiar to them. Issue #140 renames the ladder so it reads in the order a
learner acquires a word. The numbers and `highlighted` do not move — only the names
and their display labels:

| Level | Was | Now |
| ----- | --- | --- |
| 0 | `UNKNOWN` | `UNKNOWN` |
| 1 | `FAMILIAR` | `RECOGNIZED` |
| 2 | `RECOGNIZED` | `FAMILIAR` |
| 3 | `MASTERED` | `LEARNED` |
| 4 | `KNOWN` | `KNOWN` |

The palette keeps one colour per level — level 1 stays yellow, level 2 light orange,
level 3 green — so the rename moves names, not colours.

## The persisted names are deliberately broken, with no shim

`MasteryLevel` is `@Serializable` and is persisted **by name** inside every
vocabulary record (ADR-0020). A record therefore carries `"FAMILIAR"` or
`"MASTERED"` as text, and that text travels through sync and the whole-vault export
(ADR-0003, ADR-0014). A plain rename is not backward compatible with a vault written
before it: an old `"MASTERED"` no longer decodes, and an old `"FAMILIAR"` silently
becomes the *other* level, because the two names swapped places.

We accept that: **the vault is reset**. Lekto is pre-release, a single-user vault
here has no history worth carrying, and this is the moment to fix the vocabulary
before it is frozen. So there is **no migration**: no `@SerialName` mapping, no
`schemaVersion` or `formatVersion` bump, no compatibility mapping at all.

We rejected attaching `@SerialName("FAMILIAR")` to `RECOGNIZED` and `@SerialName("RECOGNIZED")`
to `FAMILIAR`: that mapping is exactly the inversion the rename exists to undo, and
it would silently reinterpret an old vault rather than admitting the break. We
rejected a version bump with a drop-old-records step (ADR-0003's `schemaVersion`,
ADR-0014's `formatVersion`): it is a migration path for data we have decided not to
carry, and it would add a version to reason about for a break the reset already
covers.

**Consequences**: a vault written before this change may mislabel or drop its
vocabulary entries; that is accepted and the vault is reset. The rename is a
one-way door for one value vocabulary — the persisted names of one enum in
vocabulary records — and its blast radius is a pre-release vault's saved words.
CONTEXT.md's ladder, the UX specification's palette table, the end-user guide and
the palette's value names all follow. This blocks #64, whose derived-mastery rule
names these levels.
