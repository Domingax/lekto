---
status: accepted
---

# Chapters are inferred when the file declares none

ADR-0037 decided that "a TXT has one chapter covering the whole text, because
there is no structure to invent". That made a TXT a second-class **Book**: no
**Chapter list**, so no way to jump; no chapter label in the chrome; and, because
the reader measures only the chapter holding the anchor (ADR-0040), the cost of
opening one still grows with the whole file. Yet a plain text carries the same
signals a reader sees: a numbered heading, alone on its line, above a blank line.

A **Book** whose original declares no structure now gets one **inferred** from
its text, and that inference feeds the same `Chapter` model the EPUB parser
fills. Nothing is written: the **Chapter list** stays a **Derived asset**
(ADR-0005), re-derived from the **Book original**, and never synced.

**The inference is conservative.** A line is a chapter start when it is short,
sits alone between blank lines, and opens with a numbering a reader would
recognise — `Chapter 4`, `Chapitre IV`, `Partie 2`, or a bare numeral. The
numbering must hold across the candidates, and at least two chapters must be
found; otherwise the book keeps the one chapter it had. A false chapter costs
more than a missing one: a wrong boundary splits a page mid-scene, while a book
with no list behaves exactly as it does today.

**The heading line stays in the text.** Only its block kind changes, from
paragraph to heading. The rendered characters are unchanged, so a **Reading
position** recorded before the change still names the same place — the failure
ADR-0037 could only warn about. A parser improvement re-parses every book on its
next open, because a derived asset is cheap to rebuild; a better heuristic
therefore repairs books already in a **Vault**.

**A TXT also yields a title, and the reader may correct it.** The first non-empty
line is taken as the title when it reads like one — short, no terminal period,
not a chapter heading — and the file name otherwise. A TXT declares no language,
and one is needed for a **Word token** to carry a **Word difficulty** and be
coloured (ADR-0030), so **Import** asks for the **Reading language** alongside
the title, prefilled from the guess and editable. Neither value is invented at
read time: they are stored on the **Book** like the ones an EPUB declares.

**Rejected alternatives**: converting the TXT to a real EPUB first, which gains
nothing — the reader never opens one, and the converted file would become the
**Book original**, against ADR-0016; an XPath the reader configures, as Calibre
offers, which exposes a conversion language to someone who only wanted to read;
no inference at all, the status quo; and a manual chapter editor, which stores a
user-authored structure against ADR-0005 for a large feature, when the honest
fallback is the single chapter that already works.

**Consequences**: `TxtParser` grows the heading heuristic and the title guess and
returns more than one `Chapter`; the parsed-text cache bumps its `formatVersion`,
so every TXT re-parses once; `BookLibrary.import` accepts a title and a reading
language that override what the parser found; `CONTEXT.md`'s **Chapter list**
no longer says the chapters are only the ones the file declares. PDF inherits the
same seam when it lands, and is expected to infer its chapters the same way.
