package app.lekto.core.book

import app.lekto.core.vault.DeviceId
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlin.time.Instant

/**
 * The finished-declaration record's serialise/parse invariant (issue #200;
 * ADR-0038; docs/testing.md, "Test levels": a serialise/parse domain invariant is
 * a property over generated data). A declaration survives encode → decode
 * whatever the book and instant, and its identity stays derived from the book so
 * every device merges the same record instead of duplicating it.
 */
@OptIn(ExperimentalKotest::class)
class FinishedBookRecordPropertyTest :
    FunSpec({

        test("a finished declaration survives a vault-record round-trip, for arbitrary data") {
            forAll(
                PropTestConfig(seed = 20261029, iterations = 300),
                Arb.string(1..32),
                Arb.long(-10_000_000_000_000L..10_000_000_000_000L),
                Arb.string(),
            ) { bookId, millis, device ->
                val finished = FinishedBook(bookId, Instant.fromEpochMilliseconds(millis))
                val record = FinishedBookRecord.of(
                    finished,
                    Instant.fromEpochMilliseconds(millis),
                    DeviceId(device.ifBlank { "device" }),
                )

                FinishedBookRecord.finishedOf(record) == finished
            }
        }

        test("a finished record is keyed by its derived id and its own kind") {
            forAll(
                PropTestConfig(seed = 20261030, iterations = 200),
                Arb.string(1..32),
                Arb.long(),
            ) { bookId, millis ->
                val finished = FinishedBook(bookId, Instant.fromEpochMilliseconds(millis))
                val record = FinishedBookRecord.of(finished, finished.finishedAt, DeviceId("device"))

                record.id == FinishedBookRecord.idOf(bookId) &&
                    record.kind == FinishedBookRecord.KIND &&
                    record.id != bookId
            }
        }

        test("the declaration keeps its book and instant in the record body") {
            val finished = FinishedBook("b1", Instant.fromEpochMilliseconds(1_700_000_000_000))

            val record = FinishedBookRecord.of(finished, finished.finishedAt, DeviceId("device"))

            record.body["bookId"]?.toString() shouldBe "\"b1\""
            record.body["finishedAt"]?.toString() shouldBe "\"${finished.finishedAt}\""
        }
    })
