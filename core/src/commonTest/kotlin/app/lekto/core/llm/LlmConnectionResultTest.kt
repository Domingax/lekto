package app.lekto.core.llm

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The status-to-outcome mapping of a connection test (issue #88): a success is
 * [LlmConnectionResult.Connected] and every failure is an honest, key-free
 * message, so a provider cannot throw into the reading session. It is pure, so
 * the two clients map a provider's answer the same way.
 */
class LlmConnectionResultTest :
    FunSpec({

        test("a 2xx answer is a successful connection") {
            (200..299).forEach { code -> connectionResultFor(code) shouldBe LlmConnectionResult.Connected }
        }

        test("a rejected key is named as such") {
            connectionResultFor(401) shouldBe LlmConnectionResult.Failed(LlmConnectionResult.REJECTED_KEY)
            connectionResultFor(403) shouldBe LlmConnectionResult.Failed(LlmConnectionResult.REJECTED_KEY)
        }

        test("a missing endpoint points at the base URL") {
            connectionResultFor(404) shouldBe LlmConnectionResult.Failed(LlmConnectionResult.NOT_FOUND)
        }

        test("rate limiting is reported as retryable") {
            connectionResultFor(429) shouldBe LlmConnectionResult.Failed(LlmConnectionResult.RATE_LIMITED)
        }

        test("another 4xx points at the request, usually the model") {
            connectionResultFor(400) shouldBe LlmConnectionResult.Failed(LlmConnectionResult.BAD_REQUEST)
            connectionResultFor(422) shouldBe LlmConnectionResult.Failed(LlmConnectionResult.BAD_REQUEST)
        }

        test("a server error is reported as an error") {
            connectionResultFor(500) shouldBe LlmConnectionResult.Failed(LlmConnectionResult.SERVER_ERROR)
            connectionResultFor(503) shouldBe LlmConnectionResult.Failed(LlmConnectionResult.SERVER_ERROR)
        }
    })
