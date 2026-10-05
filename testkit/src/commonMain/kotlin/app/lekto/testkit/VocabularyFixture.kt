package app.lekto.testkit

import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import app.lekto.core.vocabulary.VocabularyEntry

/**
 * A [VocabularyEntry] for tests (issue #71): deterministic, so the desktop and
 * Android UI suites render the same word and their assertions can be read
 * against one another. The field a test varies is its parameter.
 */
@Suppress("LongParameterList") // A fixture's fields are independent; a bundle would only hide that.
fun testVocabularyEntry(
    language: String? = "en",
    surface: String = "lantern",
    key: String = surface,
    translation: String? = "lanterne",
    contextSentence: String? = "The lantern burned all night.",
    mastery: MasteryLevel = MasteryLevel.MASTERED,
): VocabularyEntry = VocabularyEntry(
    key = WordKey(language, key),
    surface = surface,
    translation = translation,
    contextSentence = contextSentence,
    mastery = mastery,
)
