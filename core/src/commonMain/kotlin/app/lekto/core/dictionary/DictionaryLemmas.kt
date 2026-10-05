package app.lekto.core.dictionary

import app.lekto.core.text.LemmaLookup
import app.lekto.core.text.baseLanguage
import app.lekto.core.text.normaliseSurface

/**
 * The dictionary pack's reverse index as the [LemmaLookup] word identity reads
 * (ADR-0006): a surface form, in a language, onto its lemma, or `null` when the
 * pack is not installed or does not know the word.
 *
 * The pack is keyed by a base language (`en`, `fr`) and a normalised surface
 * form, so a BCP-47 tag such as `en-US` collapses and the form is normalised
 * before the query — the same normalisation the fallback key uses, so a known
 * lemma and an unknown surface cannot land on two keys. The pack is resolved
 * through [pack] on every call, so installing it later is seen without
 * re-creating the lookup.
 */
fun dictionaryLemmas(pack: () -> DictionaryPack?): LemmaLookup = LemmaLookup { surface, language ->
    val code = language?.let(::baseLanguage) ?: return@LemmaLookup null
    pack()?.lemmaOf(normaliseSurface(surface), code)
}
