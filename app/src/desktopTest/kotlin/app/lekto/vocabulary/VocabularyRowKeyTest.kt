package app.lekto.vocabulary

import app.lekto.core.text.WordKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The vocabulary row's lazy-list key (issue #69). Compose saves a lazy item's
 * state in a `Bundle`, so on Android the key must be a type the platform can
 * store — the entry's [WordKey] is a data class and is not. This pins that the
 * key is a `String` (the `val key: String` will not compile if the type regresses
 * to `WordKey`) and that two words never share one; the call site the crash came
 * from is guarded by the Bundle-strict registry in `VocabularyScreenSemanticsTest`.
 */
class VocabularyRowKeyTest {

    @Test
    fun `the row key is a string Android can save`() {
        val key: String = WordKey("en", "another").rowKey()

        assertTrue(key.contains("en"))
        assertTrue(key.contains("another"))
    }

    @Test
    fun `two different words never share a row key`() {
        assertNotEquals(WordKey("en", "another").rowKey(), WordKey("fr", "manger").rowKey())
    }

    @Test
    fun `a word with no language still gets a row key`() {
        val key: String = WordKey(null, "lantern").rowKey()

        assertTrue(key.contains("lantern"))
    }

    @Test
    fun `the same word always keys the same`() {
        assertEquals(WordKey("en", "another").rowKey(), WordKey("en", "another").rowKey())
    }
}
