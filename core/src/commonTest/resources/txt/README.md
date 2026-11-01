# Test TXT fixtures

## `les-trois-mousquetaires.txt`

The opening of *Les Trois Mousquetaires* by Alexandre Dumas, downloaded from
Project Gutenberg as eBook #13951 (<https://www.gutenberg.org/ebooks/13951>) and
cut at the line before chapter V. The text is in the public domain.

It is committed rather than fetched so the golden runs offline and deterministically.
It is a real, ugly file, and that is the point: CRLF line endings, a Project
Gutenberg header, a table of contents whose entries are packed into one paragraph,
a hard-wrapped preface, and chapter headings written **on two lines** — the number
and the title in the same paragraph. The first chapter is spelled out
(`CHAPITRE PREMIER`), so the parser's list starts at `CHAPITRE II.`: a spelled-out
ordinal is a known limit of the inference (ADR-0041), not a bug the golden hides.
The title it reads is the Project Gutenberg header line, because ADR-0041 chose
not to recognise collection boilerplate — the title is what the reader edits at
**Import**, and the golden pins the honest result rather than a cleaned one.

**Do not edit the file.** It is an input, not a fixture to reformat: changing it
invalidates `core/src/commonTest/resources/golden/txt-les-trois-mousquetaires.txt`.
