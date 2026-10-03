# Test EPUB fixtures

## `pg1952.epub`

*The Yellow Wallpaper* by Charlotte Perkins Gilman, downloaded from Project
Gutenberg as eBook #1952 (<https://www.gutenberg.org/ebooks/1952>). The text is
in the public domain in the United States.

It is committed rather than fetched so `RealEpubGoldenTest` is deterministic and
runs offline. It is a real, older EPUB 2 produced by Ebookmaker, which is why it
carries the quirks the golden guards: a Project Gutenberg boilerplate chunk in
the spine, a DTD reference, a cover item, nested `div`s and a `<br/>`.

**Do not edit the file.** It is an input, not a fixture to reformat: changing it
invalidates `core/src/commonTest/resources/golden/pg1952.txt`.
