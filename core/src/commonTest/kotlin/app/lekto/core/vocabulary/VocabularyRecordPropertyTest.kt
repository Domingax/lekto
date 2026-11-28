package app.lekto.core.vocabulary

import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import app.lekto.core.vault.DeviceId
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

/**
 * The vocabulary record's serialisation round-trip and its deterministic id
 * (issue #22; docs/development.md, "Test levels"): an entry survives encode → decode
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
                Arb.list(Arb.string()),
            ) { language, key, surface, translation, mastery, millis, device, schema, books ->
                val entry = VocabularyEntry(
                    key = WordKey(language, key),
                    surface = surface,
                    translation = translation,
                    contextSentence = translation,
                    mastery = mastery,
                    bookIds = books,
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

        test("the schema version moves when the body gains the book history") {
            // Pinned as a number, not read back from the constant: the version is
            // the record format's promise, and a body with no `bookIds` must keep
            // reading across it (ADR-0050).
            val record = VocabularyRecord.of(
                VocabularyEntry(WordKey("en", "lantern"), "lantern"),
                Instant.fromEpochMilliseconds(0),
                DeviceId("device-a"),
            )

            record.schemaVersion shouldBe 2
        }

        test("a record written before the book history reads with no books") {
            // The body an earlier build wrote: the entry's fields without
            // `bookIds`. `VaultCodec`'s defaults apply it as an empty list, so an
            // older vault loads and the word simply names no book (ADR-0050).
            val body = buildJsonObject {
                put(
                    "key",
                    buildJsonObject {
                        put("language", "en")
                        put("key", "lantern")
                    },
                )
                put("surface", "lantern")
                put("mastery", "LEARNED")
            }
            val record = VocabularyRecord.of(
                VocabularyEntry(WordKey("en", "lantern"), "lantern"),
                Instant.fromEpochMilliseconds(0),
                DeviceId("device-a"),
            ).copy(body = body)

            val entry = VocabularyRecord.entryOf(record)

            entry.bookIds shouldBe emptyList()
            entry.surface shouldBe "lantern"
            entry.mastery shouldBe MasteryLevel.LEARNED
        }
    })
