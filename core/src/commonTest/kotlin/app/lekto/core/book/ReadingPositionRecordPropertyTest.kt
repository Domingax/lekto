package app.lekto.core.book

import app.lekto.core.vault.DeviceId
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlin.time.Instant

/**
 * The reading-position record's serialise/parse invariant (issue #16;
 * docs/testing.md, "Test levels": a serialise/parse domain invariant is a
 * property over generated data). A position survives encode → decode whatever
 * the book id and offset, and a record's id stays derived from the book so a
 * reopened book looks its position up without scanning.
 */
@OptIn(ExperimentalKotest::class)
class ReadingPositionRecordPropertyTest :
    FunSpec({

        test("a reading position survives a vault-record round-trip, for arbitrary data") {
            forAll(
                PropTestConfig(seed = 20261016, iterations = 300),
                Arb.string(1..32),
                Arb.int(),
                Arb.long(-10_000_000_000_000L..10_000_000_000_000L),
                Arb.string(),
            ) { bookId, offset, millis, device ->
                val position = ReadingPosition(bookId, offset)
                val record = ReadingPositionRecord.of(
                    position,
                    Instant.fromEpochMilliseconds(millis),
                    DeviceId(device.ifBlank { "device" }),
                )

                ReadingPositionRecord.positionOf(record) == position
            }
        }

        test("a position record is keyed by its derived id and its own kind") {
            forAll(
                PropTestConfig(seed = 20261017, iterations = 200),
                Arb.string(1..32),
                Arb.int(),
            ) { bookId, offset ->
                val record = ReadingPositionRecord.of(
                    ReadingPosition(bookId, offset),
                    Instant.fromEpochMilliseconds(0),
                    DeviceId("device"),
                )

                record.id == ReadingPositionRecord.idOf(bookId) && record.kind == ReadingPositionRecord.KIND
            }
        }
    })
