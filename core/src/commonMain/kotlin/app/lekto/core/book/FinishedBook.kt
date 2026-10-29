package app.lekto.core.book

import app.lekto.core.vault.InstantIso8601Serializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The reader's declaration that a [Book] is **Finished** (CONTEXT.md,
 * "Finished"; ADR-0038): the book, and when the reader declared it.
 *
 * It is deliberately independent of the [ReadingPosition]: reaching the last
 * page does not declare a book finished, and turning a page or reopening the
 * book does not revoke the declaration. The declaration is user-authored data,
 * so it lives in the vault and travels with it; how one is stored is
 * [FinishedBookRecord]'s concern.
 */
@Serializable
data class FinishedBook(
    val bookId: String,
    @Serializable(with = InstantIso8601Serializer::class)
    val finishedAt: Instant,
)
