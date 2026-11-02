package app.lekto.core.book

import app.lekto.core.vault.DeviceId
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlin.time.Instant

/**
 * The book record's serialise/parse invariant (ADR-0003; docs/testing.md, "Test
 * levels": a serialise/parse domain invariant is a property over generated
 * data). Every book field, its **Author** included, survives encode → decode
 * whatever the title and author, so an import never loses what it read
 * (issue #286).
 */
@OptIn(ExperimentalKotest::class)
class BookRecordPropertyTest :
    FunSpec({

        test("a book's fields survive a vault-record round-trip, for arbitrary data") {
            forAll(
                PropTestConfig(seed = 20261102, iterations = 300),
                Arb.string(),
                Arb.string().orNull(),
            ) { title, author ->
                val book = Book(
                    id = "book",
                    title = title,
                    language = null,
                    format = BookFormat.EPUB,
                    fileName = "book.epub",
                    author = author,
                )

                val record = BookRecord.of(book, Instant.fromEpochMilliseconds(0), DeviceId("device"))

                BookRecord.bookOf(record) == book
            }
        }
    })
