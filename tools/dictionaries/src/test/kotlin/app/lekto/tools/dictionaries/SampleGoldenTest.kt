package app.lekto.tools.dictionaries

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The sample transform's reproducibility evidence (issue #297; ADR-0049). The same
 * committed fixture yields the same canonical catalogue every time, pinned in
 * `golden/sample-en.json`; the bytes are hashed on two runs so a change that
 * reorders or rewrites the output fails here even if the JSON were rewritten
 * identically by hand.
 */
class SampleGoldenTest {

    @Test
    fun `the fixture yields the committed catalogue, byte-identically twice`() {
        val first = catalogue()
        val second = catalogue()

        assertEquals(first, second, "the transform must be deterministic")
        assertEquals(SampleFixtures.golden(), first, "update the golden only when the transform changes on purpose")
        assertEquals(SHA256, digest(first), "the pinned bytes moved; update only with a deliberate transform change")
        assertEquals(digest(first), digest(second))
    }

    private fun catalogue(): String = SampleCatalogueDocument(
        SampleFormat.VERSION,
        listOf(SampleFixtures.content()),
    ).canonicalJson()

    private fun digest(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray())
        return digest.joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private companion object {
        const val SHA256: String = "8ecbb12967a9b4f38009c5dfb3861c47979e94405ff594d12517e720b79ef820"
    }
}
