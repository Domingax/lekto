package app.lekto.core.llm

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

/**
 * The outcome mapping of a connection test (issue #88): a success is
 * [LlmConnectionResult.Connected], an unrecognised failure falls back to the HTTP
 * status, and a recognised provider identifier names the cause. Every failure is
 * an honest, key-free message, so a provider cannot throw into the reading
 * session. It is pure, so the two clients map a provider's answer the same way.
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

        test("an OpenCode Go MissingSessionID envelope names the missing session") {
            val body = """{"type":"error","error":{"type":"MissingSessionID","message":"request is missing it"}}"""

            errorIdentifier(body) shouldBe "MissingSessionID"
            connectionResult(400, errorIdentifier(body)) shouldBe
                LlmConnectionResult.Failed(LlmConnectionResult.MISSING_SESSION)
        }

        test("a specific error code is preferred over the generic type") {
            val body = """{"error":{"message":"x","type":"invalid_request_error","code":"invalid_api_key"}}"""

            errorIdentifier(body) shouldBe "invalid_api_key"
            connectionResult(401, errorIdentifier(body)) shouldBe
                LlmConnectionResult.Failed(LlmConnectionResult.REJECTED_KEY)
        }

        test("a model error names the model") {
            connectionResult(404, "model_not_found") shouldBe
                LlmConnectionResult.Failed(LlmConnectionResult.UNKNOWN_MODEL)
            connectionResult(400, "ModelError") shouldBe
                LlmConnectionResult.Failed(LlmConnectionResult.UNKNOWN_MODEL)
        }

        test("a quota error names the billing") {
            connectionResult(400, "insufficient_quota") shouldBe
                LlmConnectionResult.Failed(LlmConnectionResult.NO_FUNDS)
            connectionResult(400, "insufficient_funds") shouldBe
                LlmConnectionResult.Failed(LlmConnectionResult.NO_FUNDS)
        }

        test("an unknown identifier falls back to the status mapping") {
            connectionResult(400, "SomeUnknownError") shouldBe
                LlmConnectionResult.Failed(LlmConnectionResult.BAD_REQUEST)
            connectionResult(500, "server_error") shouldBe
                LlmConnectionResult.Failed(LlmConnectionResult.SERVER_ERROR)
        }

        test("an identifier is only a lookup key and never reaches the message") {
            val echoed = "sk-secret-and-a-hostile-sentence"

            val result = connectionResult(400, echoed)

            (result as LlmConnectionResult.Failed).message shouldBe LlmConnectionResult.BAD_REQUEST
            result.message shouldNotContain echoed
        }

        test("a malformed, empty or unexpected body yields no identifier") {
            errorIdentifier("") shouldBe null
            errorIdentifier("not json at all") shouldBe null
            errorIdentifier("""{"unexpected":true}""") shouldBe null
            errorIdentifier("""{"error":"a plain string"}""") shouldBe null
        }
    })
