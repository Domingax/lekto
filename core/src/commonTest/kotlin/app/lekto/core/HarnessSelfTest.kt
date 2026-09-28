package app.lekto.core

import app.cash.turbine.test
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.PropTestConfig
import io.kotest.property.forAll
import kotlinx.coroutines.flow.flowOf
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The harness tests itself.
 *
 * A KMP build has a real trap: a source set can compile zero tests, or run them
 * under a runner that never discovers them, and still report success. These
 * tests fail loudly if the wiring regresses. Each exercises one capability the
 * harness promises, and the last two pin the two guarantees the ticket asks for:
 * a broken assertion fails, and a property failure reports a replayable seed.
 */
@OptIn(ExperimentalKotest::class)
class HarnessSelfTest : FunSpec({

    test("kotlin.test assertions are on the classpath") {
        assertEquals(4, 2 + 2)
    }

    test("Kotest matchers produce readable failures") {
        "lekto" shouldContain "lek"
    }

    test("Kotest property testing runs, seeded") {
        forAll<Int>(PropTestConfig(seed = 42)) { n ->
            n + 0 == n
        }
    }

    test("Turbine asserts a flow to completion") {
        flowOf(1, 2, 3).test {
            awaitItem() shouldBe 1
            awaitItem() shouldBe 2
            awaitItem() shouldBe 3
            awaitComplete()
        }
    }

    test("a deliberately broken assertion fails reproducibly") {
        suspend fun runOnce(): String =
            assertFailsWith<AssertionError> { (1 + 1) shouldBe 3 }.message.orEmpty()

        val first = runOnce()
        first shouldContain "3"
        first shouldContain "2"
        runOnce() shouldBe first
    }

    test("a failing property reports a replayable seed") {
        suspend fun runOnce(): String =
            assertFailsWith<AssertionError> {
                forAll<Int>(PropTestConfig(seed = 42)) { n ->
                    n * 0 == 1
                }
            }.message.orEmpty()

        val first = runOnce()
        first shouldContain "seed 42"
        runOnce() shouldBe first
    }
})
