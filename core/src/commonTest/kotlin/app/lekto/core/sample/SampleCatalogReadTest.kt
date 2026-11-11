package app.lekto.core.sample

import app.lekto.core.CefrLevel
import app.lekto.core.MasteryLevel
import app.lekto.testkit.testSample
import app.lekto.testkit.testSampleCatalog
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

        test("a blank word key or a meaning without a definition is malformed") {
            val blankWord = testSample(
                colourMaps = testSample().colourMaps + (CefrLevel.A1 to mapOf(" " to MasteryLevel.UNKNOWN)),
            )
            val noSense = """
                {"formatVersion":1,"samples":[{"language":"en","title":"T","paragraphs":["A boat."],
                "colourMaps":{"A1":{},"A2":{},"B1":{},"B2":{},"C1":{},"C2":{}},
                "glossaries":{"fr":{"entries":{"boat":[{"lemma":"boat","senses":[]}]}}}}]}
            """.trimIndent().encodeToByteArray()

            readSampleCatalog(testSampleCatalog(listOf(blankWord))) shouldBe SampleCatalogRead.Malformed
            readSampleCatalog(noSense) shouldBe SampleCatalogRead.Malformed
        }

        test("a form that points at a word the sample does not carry is malformed") {
            val dangling = testSample(forms = mapOf("boats" to "missing"))

            readSampleCatalog(testSampleCatalog(listOf(dangling))) shouldBe SampleCatalogRead.Malformed
        }

        test("two samples for one language are malformed, because the identity is fixed") {
            readSampleCatalog(testSampleCatalog(listOf(testSample(), testSample()))) shouldBe
                SampleCatalogRead.Malformed
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
