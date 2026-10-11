package app.lekto.core.sync

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The automatic-sync backoff policy (issue #121; ADR-0028): the wait between
 * runs starts at the base, doubles with each consecutive failure and never
 * exceeds the cap, so a target that keeps failing is polled less, not more. It is
 * a pure function of the failure count, so it is pinned here rather than through
 * the state holder.
 */
class SyncScheduleTest :
    FunSpec({

        test("the default wait is bounded between a quarter of an hour and an hour") {
            val schedule = SyncSchedule()

            schedule.baseInterval shouldBe 15.minutes
            schedule.maxInterval shouldBe 1.hours
        }

        test("a success or a first failure waits the base interval") {
            val schedule = SyncSchedule()

            schedule.intervalAfter(0) shouldBe 15.minutes
            schedule.intervalAfter(-3) shouldBe 15.minutes
        }

        test("each consecutive failure doubles the wait") {
            val schedule = SyncSchedule()

            schedule.intervalAfter(1) shouldBe 30.minutes
            schedule.intervalAfter(2) shouldBe 60.minutes
        }

        test("the wait never exceeds the cap, however many failures") {
            val schedule = SyncSchedule()

            schedule.intervalAfter(3) shouldBe 1.hours
            schedule.intervalAfter(50) shouldBe 1.hours
        }

        test("a smaller schedule doubles from its own base") {
            val schedule = SyncSchedule(baseInterval = 1.minutes, maxInterval = 4.minutes)

            schedule.intervalAfter(0) shouldBe 1.minutes
            schedule.intervalAfter(1) shouldBe 2.minutes
            schedule.intervalAfter(2) shouldBe 4.minutes
            schedule.intervalAfter(9) shouldBe 4.minutes
        }
    })
