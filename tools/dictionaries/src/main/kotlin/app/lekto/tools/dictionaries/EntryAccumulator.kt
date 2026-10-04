package app.lekto.tools.dictionaries

/**
 * The merge of every Wiktextract line that keys to one `(language, lemma, pos)`:
 * senses de-duplicated by definition and kept in Wiktionary order, each carrying
 * the French translations attached to it; the first pronunciation and audio win.
 */
internal class EntryAccumulator(
    private val lemma: String,
    private val language: String,
    private val partOfSpeech: String?,
) {
    var pronunciation: String? = null
    var audioUrl: String? = null

    private val senses = LinkedHashMap<String, MutableList<String>>()

    fun addDefinition(definition: String) {
        senses.getOrPut(definition) { mutableListOf() }
    }

    fun addTranslation(definition: String, translation: String) {
        val translations = senses.getOrPut(definition) { mutableListOf() }
        if (translation !in translations) translations.add(translation)
    }

    fun toPackEntry(): PackEntry = PackEntry(
        lemma = lemma,
        language = language,
        partOfSpeech = partOfSpeech,
        pronunciation = pronunciation,
        audioUrl = audioUrl,
        senses = senses.map { (definition, translations) -> PackSense(definition, translations) },
    )
}
