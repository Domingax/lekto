package app.lekto.core.language

import app.lekto.testkit.testOfferedArtifact
import app.lekto.testkit.testOfferedLanguage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The embedded roster and the one predicate both surfaces share (issue #298):
 * the guide's first screen and the **Languages** surface return the same list
 * for the same inputs, so the agreement is asserted here once, not once per
 * caller.
 */
class SampleRosterTest :
    FunSpec({

        test("the roster carries the eight shipped samples, named by the build") {
            SampleRoster.LANGUAGES shouldBe listOf("de", "en", "es", "fr", "it", "ja", "pt", "tr")
        }

        test("a roster language is named and carries no artefact until a manifest is read") {
            sampleRosterLanguages() shouldBe SampleRoster.LANGUAGES.map { language ->
                testOfferedLanguage(language)
            }
        }

        test("with no manifest read the list is the roster alone") {
            val roster = listOf(testOfferedLanguage("en"), testOfferedLanguage("fr"))

            offeredLanguages(roster, null) shouldBe roster
        }

        test("with no manifest cached the list is the roster alone") {
            val roster = listOf(testOfferedLanguage("en"), testOfferedLanguage("fr"))

            offeredLanguages(roster, LanguageCatalogState.Unavailable) shouldBe roster
        }

        test("with a manifest read the list is the intersection, in roster order") {
            val roster = listOf(testOfferedLanguage("en"), testOfferedLanguage("fr"), testOfferedLanguage("de"))
            val manifest = listOf(
                testOfferedLanguage(
                    "de",
                    difficulty = testOfferedArtifact("lekto-difficulty-de.sqlite.gz"),
                ),
                testOfferedLanguage("en"),
                testOfferedLanguage("xx"),
            )

            offeredLanguages(roster, manifest) shouldBe listOf(
                testOfferedLanguage("en"),
                testOfferedLanguage(
                    "de",
                    difficulty = testOfferedArtifact("lekto-difficulty-de.sqlite.gz"),
                ),
            )
        }

        test("a language the manifest removed disappears without a rebuild") {
            val roster = listOf(testOfferedLanguage("en"), testOfferedLanguage("fr"))

            offeredLanguages(roster, listOf(testOfferedLanguage("en"))) shouldBe listOf(testOfferedLanguage("en"))
        }

        test("a language the manifest added is not offered until a build ships its sample") {
            val roster = listOf(testOfferedLanguage("en"))

            offeredLanguages(roster, listOf(testOfferedLanguage("en"), testOfferedLanguage("ko"))) shouldBe
                listOf(testOfferedLanguage("en"))
        }

        test("an empty intersection is empty, so the caller can say so") {
            val roster = listOf(testOfferedLanguage("en"))

            offeredLanguages(roster, emptyList()) shouldBe emptyList()
            offeredLanguages(roster, LanguageCatalogState.Available(emptyList())) shouldBe emptyList()
        }

        test("a refused or malformed manifest leaves the roster's list intact") {
            val roster = listOf(testOfferedLanguage("en"), testOfferedLanguage("fr"))

            offeredLanguages(roster, LanguageCatalogState.Unavailable) shouldBe roster
        }

        test("the intersection keeps the manifest's artefacts, never the roster's emptiness") {
            val roster = listOf(testOfferedLanguage("en"))
            val manifest = listOf(
                testOfferedLanguage(
                    "en",
                    difficulty = testOfferedArtifact("lekto-difficulty-en.sqlite.gz"),
                    dictionaries = listOf(
                        OfferedDictionary("fr", testOfferedArtifact("lekto-dictionary-en-fr.sqlite.gz")),
                    ),
                ),
            )

            offeredLanguages(roster, manifest) shouldBe manifest
        }
    })
