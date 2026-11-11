package app.lekto.core.sample

import app.lekto.core.CefrLevel
import app.lekto.core.MasteryLevel
import app.lekto.testkit.testSample
import app.lekto.testkit.testSampleCatalog
import app.lekto.testkit.testSampleGlossary
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The **Sample catalogue** reader (issue #297): a pure function from the bytes a
 * build ships to the samples the guide demonstrates. Its handshake is strict, and
 * an entry that cannot answer the guide — no text, fewer than six maps, a blank
 * word, a meaning without a definition — is malformed rather than a sample with
 * missing colours. Unknown keys at the same version are tolerated, as the
 * **Language manifest** reader does.
 */
class SampleCatalogReadTest :
    FunSpec({

        test("reads one sample's text, three companions and its fixed identity") {
            val sample = testSample()

            val read = readSampleCatalog(testSampleCatalog(listOf(sample)))

            read shouldBe SampleCatalogRead.Read(SampleCatalogContent(listOf(sample)))
            (read as SampleCatalogRead.Read).catalogue.sampleOf("en")?.identity shouldBe "sample:en"
        }

        test("a language this build ships no sample for reads as absent") {
            val catalogue = (readSampleCatalog(testSampleCatalog()) as SampleCatalogRead.Read).catalogue

            catalogue.sampleOf("fr") shouldBe null
        }

        test("a map equals the band the catalogue carries, for every band") {
            val levels = mapOf("boat" to MasteryLevel.UNKNOWN, "water" to MasteryLevel.KNOWN)
            val maps = CefrLevel.entries.associateWith { band ->
                levels.mapValues { (_, level) -> if (band == CefrLevel.C2) level else MasteryLevel.RECOGNIZED }
            }
            val sample = testSample(
                difficulty = mapOf("boat" to null, "water" to CefrLevel.A1),
                colourMaps = maps,
            )

            val read = readSampleCatalog(testSampleCatalog(listOf(sample))) as SampleCatalogRead.Read

            CefrLevel.entries.forEach { band ->
                read.catalogue.sampleOf("en")?.colourMap(band) shouldBe maps.getValue(band)
            }
        }

        test("an entry without a paragraph, a title or the six maps is malformed") {
            val noParagraph = testSample(paragraphs = emptyList())
            val noTitle = testSample(title = " ")
            val fiveMaps = testSample(colourMaps = testSample().colourMaps - CefrLevel.C1)

            readSampleCatalog(testSampleCatalog(listOf(noParagraph))) shouldBe SampleCatalogRead.Malformed
            readSampleCatalog(testSampleCatalog(listOf(noTitle))) shouldBe SampleCatalogRead.Malformed
            readSampleCatalog(testSampleCatalog(listOf(fiveMaps))) shouldBe SampleCatalogRead.Malformed
        }

        test("a sample with no optional author, part of speech or pronunciation reads") {
            val entries = SampleGlossary(
                mapOf(
                    "boat" to listOf(
                        SampleGlossaryEntry(
                            lemma = "boat",
                            partOfSpeech = null,
                            pronunciation = null,
                            senses = listOf(SampleSense("A boat.", emptyList())),
                        ),
                    ),
                ),
            )
            val sample = testSample(author = null, attribution = null, glossaries = mapOf("fr" to entries))

            val read = readSampleCatalog(testSampleCatalog(listOf(sample))) as SampleCatalogRead.Read

            read.catalogue.samples.single() shouldBe sample
            read.catalogue.samples.single().text shouldBe "A calm boat waited near the rocks."
        }

        test("a minimal catalogue reads, with its absent optional fields empty") {
            val bytes = """
                {"formatVersion":1,"samples":[{"language":"en","title":"T","paragraphs":["A boat."],
                "difficulty":{"boat":null},"forms":{},"glossaries":{},
                "colourMaps":{"A1":{"boat":"UNKNOWN"},"A2":{"boat":"UNKNOWN"},"B1":{"boat":"UNKNOWN"},
                "B2":{"boat":"UNKNOWN"},"C1":{"boat":"UNKNOWN"},"C2":{"boat":"UNKNOWN"}}}]}
            """.trimIndent().encodeToByteArray()

            val read = readSampleCatalog(bytes)

            read shouldBe SampleCatalogRead.Read(
                SampleCatalogContent(
                    listOf(
                        testSample(
                            title = "T",
                            paragraphs = listOf("A boat."),
                            author = null,
                            attribution = null,
                            difficulty = mapOf("boat" to null),
                            glossaries = emptyMap(),
                        ),
                    ),
                ),
            )
        }

        test("a sample with no map for a band colours nothing and answers no unknown word") {
            val sample = testSample(colourMaps = emptyMap())

            sample.colourMap(CefrLevel.A1) shouldBe emptyMap()
            sample.glossaries.getValue("fr").entriesOf("missing") shouldBe emptyList()
        }

        test("a blank text field, word key or form is malformed") {
            val malformed = listOf(
                testSample(language = " "),
                testSample(paragraphs = listOf("a", " ")),
                testSample(difficulty = mapOf(" " to null)),
                testSample(forms = mapOf(" " to "boat")),
                testSample(forms = mapOf("boats" to " ")),
                testSample(glossaries = mapOf(" " to testSampleGlossary())),
            )

            malformed.forEach { sample ->
                readSampleCatalog(testSampleCatalog(listOf(sample))) shouldBe SampleCatalogRead.Malformed
            }
        }

        test("a glossary with a blank key, a blank lemma, no entry or no sense is malformed") {
            val valid = SampleGlossaryEntry("boat", "noun", null, listOf(SampleSense("A boat.", emptyList())))
            val malformed = listOf(
                SampleGlossary(mapOf(" " to listOf(valid))),
                SampleGlossary(mapOf("boat" to emptyList())),
                SampleGlossary(mapOf("boat" to listOf(valid.copy(lemma = " ")))),
                SampleGlossary(mapOf("boat" to listOf(valid.copy(senses = emptyList())))),
                SampleGlossary(mapOf("boat" to listOf(valid.copy(senses = listOf(SampleSense(" ", emptyList())))))),
            )

            malformed.forEach { glossary ->
                readSampleCatalog(testSampleCatalog(listOf(testSample(glossaries = mapOf("fr" to glossary))))) shouldBe
                    SampleCatalogRead.Malformed
            }
        }

        test("a form that points at a word the sample does not carry is malformed") {
            val dangling = testSample(forms = mapOf("boats" to "missing"))

            readSampleCatalog(testSampleCatalog(listOf(dangling))) shouldBe SampleCatalogRead.Malformed
        }

        test("two samples for one language are malformed, because the identity is fixed") {
            readSampleCatalog(testSampleCatalog(listOf(testSample(), testSample()))) shouldBe
                SampleCatalogRead.Malformed
        }

        test("a sense without translations reads, and a missing required field is malformed") {
            val noTranslations = """
                {"formatVersion":1,"samples":[{"language":"en","title":"T","paragraphs":["A boat."],
                "difficulty":{"boat":null},"forms":{},"glossaries":{"fr":{"entries":{"boat":[
                {"lemma":"boat","senses":[{"definition":"A boat."}]}]}}},
                "colourMaps":{"A1":{"boat":"UNKNOWN"},"A2":{"boat":"UNKNOWN"},"B1":{"boat":"UNKNOWN"},
                "B2":{"boat":"UNKNOWN"},"C1":{"boat":"UNKNOWN"},"C2":{"boat":"UNKNOWN"}}}]}
            """.trimIndent().encodeToByteArray()
            val missingMaps = """{"formatVersion":1,"samples":[{"language":"en","title":"T"}]}"""
                .encodeToByteArray()

            readSampleCatalog(noTranslations) shouldBe SampleCatalogRead.Read(
                SampleCatalogContent(
                    listOf(
                        testSample(
                            title = "T",
                            paragraphs = listOf("A boat."),
                            author = null,
                            attribution = null,
                            difficulty = mapOf("boat" to null),
                            glossaries = mapOf(
                                "fr" to SampleGlossary(
                                    mapOf(
                                        "boat" to listOf(
                                            SampleGlossaryEntry(
                                                "boat",
                                                null,
                                                null,
                                                listOf(SampleSense("A boat.", emptyList())),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
            readSampleCatalog(missingMaps) shouldBe SampleCatalogRead.Malformed
        }

        test("a catalogue whose format this build does not read is refused") {
            val bytes = testSampleCatalog(formatVersion = SampleCatalog.FORMAT_VERSION + 1)

            readSampleCatalog(bytes) shouldBe SampleCatalogRead.Refused(SampleCatalog.FORMAT_VERSION + 1)
        }

        test("bytes that are not a catalogue are malformed, not refused") {
            readSampleCatalog("not a catalogue".encodeToByteArray()) shouldBe SampleCatalogRead.Malformed
        }

        test("a build that ships no sample reads as an empty catalogue, not a failure") {
            readSampleCatalog(testSampleCatalog(emptyList())) shouldBe
                SampleCatalogRead.Read(SampleCatalogContent(emptyList()))
        }

        test("an unknown key at the same format version is tolerated") {
            val bytes = buildJsonObject {
                put("formatVersion", JsonPrimitive(SampleCatalog.FORMAT_VERSION))
                put("samples", kotlinx.serialization.json.JsonArray(emptyList()))
                put("publisher", JsonPrimitive("someone"))
            }.toString().encodeToByteArray()

            readSampleCatalog(bytes) shouldBe SampleCatalogRead.Read(SampleCatalogContent(emptyList()))
        }
    })
