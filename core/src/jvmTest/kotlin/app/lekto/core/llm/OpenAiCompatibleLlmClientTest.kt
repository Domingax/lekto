package app.lekto.core.llm

import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.IOException
import java.net.InetSocketAddress

/**
 * The OpenAI-compatible transport (issue #88; ADR-0022): a real HTTP request to a
 * local server proves the connection test posts to `{baseUrl}/chat/completions`
 * with a bearer key and the chosen model, maps each answer honestly, and treats a
 * dead transport as an inline failure rather than an exception. The server is
 * local and scripted, so no provider and no key is involved, as the pack
 * downloader's lane is.
 */
class OpenAiCompatibleLlmClientTest :
    FunSpec({

        /** What the local server saw, so a test can pin the request shape. */
        class Recorded {
            var path: String? = null
            var authorization: String? = null
            var body: String? = null
        }

        fun serve(status: Int, recorded: Recorded): HttpServer {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/v1/chat/completions") { exchange ->
                recorded.path = exchange.requestURI.path
                recorded.authorization = exchange.requestHeaders.getFirst("Authorization")
                recorded.body = exchange.requestBody.readBytes().decodeToString()
                exchange.sendResponseHeaders(status, -1)
                exchange.close()
            }
            server.start()
            return server
        }

        fun custom(base: String, model: String): LlmProviderConfig =
            LlmProviderConfig(LlmProvider.CUSTOM, model, customBaseUrl = base)

        fun base(server: HttpServer): String = "http://127.0.0.1:${server.address.port}/v1"

        test("a 2xx answer reports the provider connected") {
            val server = serve(200, Recorded())
            try {
                OpenAiCompatibleLlmClient().testConnection(custom(base(server), "gpt-4o-mini"), "sk-test") shouldBe
                    LlmConnectionResult.Connected
            } finally {
                server.stop(0)
            }
        }

        test("the request carries the bearer key, the model and the endpoint path") {
            val recorded = Recorded()
            val server = serve(200, recorded)
            try {
                OpenAiCompatibleLlmClient().testConnection(custom(base(server), "my-model"), "sk-secret")

                recorded.path shouldBe "/v1/chat/completions"
                recorded.authorization shouldBe "Bearer sk-secret"
                recorded.body.shouldNotBeNull() shouldContain "\"model\":\"my-model\""
            } finally {
                server.stop(0)
            }
        }

        test("a rejected key is reported as a rejected key") {
            val server = serve(401, Recorded())
            try {
                OpenAiCompatibleLlmClient().testConnection(custom(base(server), "m"), "sk-wrong") shouldBe
                    LlmConnectionResult.Failed(LlmConnectionResult.REJECTED_KEY)
            } finally {
                server.stop(0)
            }
        }

        test("a dead transport is an inline failure, never a thrown exception") {
            val client = OpenAiCompatibleLlmClient { throw IOException("connection refused") }

            client.testConnection(LlmProviderConfig(LlmProvider.OPENAI, "gpt-4o-mini"), "sk-test") shouldBe
                LlmConnectionResult.Failed(LlmConnectionResult.UNREACHABLE)
        }

        test("an incomplete configuration is refused before any request is sent") {
            var connected = false
            val client = OpenAiCompatibleLlmClient {
                connected = true
                throw IOException("must not connect")
            }

            client.testConnection(LlmProviderConfig(LlmProvider.OPENAI, ""), "sk-test") shouldBe
                LlmConnectionResult.Failed(LlmConnectionResult.INCOMPLETE)
            connected shouldBe false
        }

        test("no failure message ever quotes the key") {
            val server = serve(500, Recorded())
            try {
                val result = OpenAiCompatibleLlmClient().testConnection(custom(base(server), "m"), "sk-very-secret")

                (result as LlmConnectionResult.Failed).message shouldNotContain "sk-very-secret"
            } finally {
                server.stop(0)
            }
        }

        test("the chat-completions URL tolerates a trailing slash in the base URL") {
            chatCompletionsUrl(custom("https://example.test/v1/", "m")) shouldBe
                "https://example.test/v1/chat/completions"
        }
    })
