package app.lekto.core.llm

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The model-list parse (issue #93; ADR-0022): the OpenAI-compatible envelope's
 * `data[].id` becomes the identifiers the picker offers, bounded in count and in
 * each identifier's length, so a hostile or broken provider cannot exhaust the
 * picker; a body that is not that envelope reads as "this provider does not
 * implement `/models`" so manual entry stays. It is a pure transform, so it is
 * pinned here without a socket.
 */
class LlmModelsResultTest :
    FunSpec({

        test("it reads the data[].id identifiers in the order the provider sent them") {
            val body = """{"object":"list","data":[{"id":"gpt-4o-mini"},{"id":"gpt-4o"}]}"""

            modelIds(body) shouldBe listOf("gpt-4o-mini", "gpt-4o")
        }

        test("it drops blank, duplicated and non-string identifiers") {
            val body = """{"data":[{"id":"  gpt-4o  "},{"id":""},{"id":42},{"id":"gpt-4o"},{"other":"x"}]}"""

            modelIds(body) shouldBe listOf("gpt-4o")
        }

        test("it bounds the count at MAX_MODELS") {
            val body = (1..MAX_MODELS + 10).joinToString(prefix = """{"data":[""", postfix = "]}") { i ->
                """{"id":"m$i"}"""
            }

            modelIds(body)?.size shouldBe MAX_MODELS
        }

        test("it drops an identifier longer than MAX_MODEL_ID_LENGTH as junk") {
            val tooLong = "m".repeat(MAX_MODEL_ID_LENGTH + 1)
            val body = """{"data":[{"id":"$tooLong"},{"id":"ok"}]}"""

            modelIds(body) shouldBe listOf("ok")
        }

        test("a body that is not the list envelope reads as unsupported") {
            modelIds("not json") shouldBe null
            modelIds("""{"object":"list"}""") shouldBe null
            modelIds("""{"data":"nope"}""") shouldBe null
        }

        test("the envelope with no models is an empty list, not an alien body") {
            modelIds("""{"object":"list","data":[]}""") shouldBe emptyList()
        }
    })
