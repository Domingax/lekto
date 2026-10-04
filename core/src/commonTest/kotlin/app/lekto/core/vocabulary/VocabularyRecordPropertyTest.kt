package app.lekto.core.vocabulary

import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import app.lekto.core.vault.DeviceId
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlin.time.Instant

/**
 * The vocabulary record's serialisation round-trip and its deterministic id
 * (issue #22; docs/testing.md, "Test levels"): an entry survives encode → decode
 * whatever its text, and the same word key always yields the same,
 * filename-safe id so two devices' records for one word merge rather than
 * duplicate (ADR-0004, ADR-0006).
 */
@OptIn(ExperimentalKotest::class)
class VocabularyRecordPropertyTest :
    FunSpec({

        val levels = Arb.element(*MasteryLevel.entries.toTypedArray())

        test("a vocabulary entry survives a JSON record round-trip, for arbitrary text") {
            forAll(
                PropTestConfig(seed = 20261022, iterations = 300),
                Arb.string(),
                Arb.string(),
                Arb.string(),
                Arb.string(),
                levels,
                Arb.long(-10_000_000_000_000L..10_000_000_000_000L),
                Arb.string(),
                Arb.int(),
            ) { language, key, surface, translation, mastery, millis, device, schema ->
                val entry = VocabularyEntry(
                    key = WordKey(language, key),
                    surface = surface,
                    translation = translation,
                    contextSentence = translation,
                    mastery = mastery,
                )
                val record = VocabularyRecord.of(
                    entry,
                    Instant.fromEpochMilliseconds(millis),
                    DeviceId(device.ifBlank { "device" }),
                ).copy(schemaVersion = schema)

                VocabularyRecord.entryOf(record) == entry
            }
        }

        test("the record id is a deterministic, filename-safe function of the word key") {
            forAll(
                PropTestConfig(seed = 20261023, iterations = 300),
                Arb.string(),
                Arb.string(),
            ) { language, key ->
                val word = WordKey(language, key)
                val id = VocabularyRecord.idOf(word)

                id == VocabularyRecord.idOf(WordKey(language, key)) &&
                    id.isNotBlank() &&
                    id.none { character -> character == '/' || character == '\\' || character == '\u0000' }
            }
        }

        test("the record names its kind and schema version") {
            val record = VocabularyRecord.of(
                VocabularyEntry(WordKey("en", "lantern"), "lantern"),
                Instant.fromEpochMilliseconds(0),
                DeviceId("device-a"),
            )

            record.kind == VocabularyRecord.KIND && record.schemaVersion == VocabularyRecord.SCHEMA_VERSION
        }
    })
