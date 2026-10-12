package app.lekto.core.difficulty

import app.lekto.testkit.InMemoryWordDifficulty
import app.lekto.testkit.WordDifficultyContract
import io.kotest.core.spec.style.FunSpec

/**
 * The [WordDifficulty] seam against the in-memory table (docs/testing.md, "Test
 * levels"): the real SQLite reader runs the same cases in `jvmTest` and its
 * Android twin in `androidHostTest`, so no implementation can differ.
 */
class WordDifficultyContractTest :
    FunSpec({
        WordDifficultyContract { InMemoryWordDifficulty() }
            .cases()
            .forEach { case -> test(case.name) { case.body() } }
    })
