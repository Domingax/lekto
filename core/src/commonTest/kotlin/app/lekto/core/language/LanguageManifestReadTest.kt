package app.lekto.core.language

import app.lekto.testkit.testLanguageManifest
import app.lekto.testkit.testOfferedLanguage
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/**
 * The **Language manifest** reader (issue #146): a pure function from the
 * published bytes to a catalogue. Its handshake is strict — a manifest this
 * build cannot read is refused rather than misread — and unknown keys at the
 * same format version are tolerated.
 */
@OptIn(ExperimentalKotest::class)
class LanguageManifestReadTest :
    FunSpec({

        test("reads the offered languages and the artefacts published for them") {
            val offered = listOf(
                testOfferedLanguage(
                    "en",
                    difficulty = "lekto-difficulty-en.sqlite.gz",
                    dictionaries = listOf(
                        OfferedDictionary("fr", "lekto-dictionary-en-fr.sqlite.gz"),
                    ),
                ),
            )

            readLanguageManifest(testLanguageManifest(offered)) shouldBe LanguageManifestRead.Read(offered)
        }

        test("a manifest whose format this build does not read is refused") {
            val bytes = testLanguageManifest(
                listOf(testOfferedLanguage("en")),
                formatVersion = LanguageManifest.FORMAT_VERSION + 1,
            )

            readLanguageManifest(bytes) shouldBe LanguageManifestRead.Refused(LanguageManifest.FORMAT_VERSION + 1)
        }

        test("bytes that are not a manifest are malformed, not refused") {
            readLanguageManifest("not a manifest at all".encodeToByteArray()) shouldBe LanguageManifestRead.Malformed
        }

        test("a run that published nothing reads as an empty catalogue, not a failure") {
            readLanguageManifest(testLanguageManifest(emptyList())) shouldBe LanguageManifestRead.Read(emptyList())
        }

        test("an unknown key at the same format version is tolerated") {
            val bytes =
                """{"formatVersion":1,"languages":[{"language":"en","publisher":"someone"}]}""".encodeToByteArray()

            readLanguageManifest(bytes) shouldBe LanguageManifestRead.Read(listOf(testOfferedLanguage("en")))
        }

        test("a manifest survives a write/read round-trip for arbitrary languages") {
            val codes = arrayOf("en", "fr", "de", "es", "pt", "xx")
            checkAll(
                PropTestConfig(seed = 2026146, iterations = 250),
                Arb.list(Arb.element(*codes), 0..6),
            ) { chosen ->
                val offered = chosen.map { code -> testOfferedLanguage(code) }

                readLanguageManifest(testLanguageManifest(offered)) shouldBe LanguageManifestRead.Read(offered)
            }
        }
    })
