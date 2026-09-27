# A word's identity is (language, lemma), with the surface form as fallback

Mastery and vocabulary are keyed by `(language, lemma)` when a lemma is known, and by
`(language, normalised surface form)` otherwise. Vocabulary entries store both the
surface form and an optional `lemma` field.

We rejected keying on the surface form alone — inflected languages would fragment
"mangeais" / "mange" / "manger" into unrelated entries — and rejected making a
lemmatiser a hard MVP dependency, since per-language lemmatisers are heavy. The bundled
dictionary pack supplies lemma↔form mappings, so lemma keying is available without a
separate NLP stack.