package app.lekto.core.readinglevel

import app.lekto.core.CefrLevel
import app.lekto.core.text.baseLanguage
import app.lekto.core.vault.DeviceId
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll
import kotlin.time.Instant

/**
 * The **Reader level** record (ADR-0030): its serialisation round-trip and its
 * deterministic, base-tag id, so every device derives the same id and the records
 * merge rather than duplicate (ADR-0004).
 */
@OptIn(ExperimentalKotest::class)
class ReaderLevelRecordPropertyTest :
    FunSpec({

        val levels = Arb.element(*CefrLevel.entries.toTypedArray())
        // BCP-47-shaped tags: the shapes a real language tag takes, so the id stays
        // filename-safe exactly as it does for a real tag.
        val tags = Arb.element(
            "en", "en-US", "en-GB", "fr", "fr-FR", "de", "pt_BR", "zh-Hans", "es-419", "EN", "Fr-fr",
        )

        test("a reader level survives a JSON record round-trip, for arbitrary language and level") {
            checkAll(
                PropTestConfig(seed = 20261012, iterations = 300),
                tags,
                levels,
            ) { language, level ->
                val record = ReaderLevelRecord.of(
                    language,
                    level,
                    Instant.fromEpochMilliseconds(0),
                    DeviceId("device-a"),
                )
                val read = ReaderLevelRecord.read(record)
                read?.level shouldBe level
                read?.language shouldBe baseLanguage(language)
            }
        }

        test("the record id is a deterministic, filename-safe function of the base language") {
            checkAll(PropTestConfig(seed = 20261013, iterations = 300), tags) { language ->
                val id = ReaderLevelRecord.idOf(language)

                id shouldBe ReaderLevelRecord.idOf(baseLanguage(language))
                id.isNotBlank() shouldBe true
                id.none { character -> character == '/' || character == '\\' || character == '\u0000' } shouldBe true
            }
        }

        test("the record names its kind and schema version") {
            val record = ReaderLevelRecord.of("en", CefrLevel.B1, Instant.fromEpochMilliseconds(0), DeviceId("a"))

            record.kind shouldBe ReaderLevelRecord.KIND
            record.schemaVersion shouldBe ReaderLevelRecord.SCHEMA_VERSION
        }
    })
