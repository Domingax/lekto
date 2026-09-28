package app.lekto.core

import app.lekto.testkit.SequentialIdGenerator
import app.lekto.testkit.TestClock
import app.lekto.testkit.deterministicSeams
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.forAll
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The injected seams and their test-side implementations.
 *
 * Time, identifiers and randomness are the only ways non-determinism enters the
 * domain (docs/research/testing-harness.md §7); each is a parameter with a
 * production wiring in `app.lekto.core` and a deterministic one in
 * `app.lekto.testkit`.
 */
class SeamsTest :
    FunSpec({

        context("TestClock") {
            test("reads the epoch until it is moved") {
                TestClock().now() shouldBe Instant.fromEpochMilliseconds(0L)
            }

            test("advances by the duration it is given, and only that") {
                val clock = TestClock()
                clock.advanceBy(5.seconds) shouldBe Instant.fromEpochMilliseconds(5_000L)
                clock.now() shouldBe Instant.fromEpochMilliseconds(5_000L)
            }

            test("can be fixed to an instant") {
                val clock = TestClock()
                clock.set(Instant.fromEpochMilliseconds(1_700_000_000_000L))
                clock.now() shouldBe Instant.fromEpochMilliseconds(1_700_000_000_000L)
            }
        }

        context("SequentialIdGenerator") {
            test("hands out consecutive ids from its start") {
                val ids = SequentialIdGenerator(prefix = "vocab-", start = 7)
                ids.newId() shouldBe "vocab-0007"
                ids.newId() shouldBe "vocab-0008"
            }

            test("never repeats an id, however many it mints") {
                forAll(Arb.int(1..1_000)) { count ->
                    val ids = SequentialIdGenerator()
                    val minted = List(count) { ids.newId() }
                    minted.toSet().size == count
                }
            }
        }

        context("UuidIdGenerator") {
            test("never repeats an id") {
                List(1_000) { UuidIdGenerator.newId() }.toSet() shouldHaveSize 1_000
            }
        }

        context("randomness") {
            test("the seam replays its stream for a given seed") {
                val first = deterministicSeams(random = Random(seed = 7)).random
                val second = deterministicSeams(random = Random(seed = 7)).random
                List(20) { first.nextInt() } shouldBe List(20) { second.nextInt() }
            }
        }

        context("deterministicSeams") {
            test("wires a controllable fake for every seam") {
                val seams = deterministicSeams()
                seams.clock.now() shouldBe Instant.fromEpochMilliseconds(0L)
                seams.ids.newId() shouldBe "0001"
                seams.random.nextInt() shouldBe Random(seed = 0).nextInt()
            }
        }
    })
