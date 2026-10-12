package app.lekto.core.llm

import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.flow.toList
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI

/**
 * The OpenAI-compatible transport (issue #88; ADR-0022): a real HTTP request to a
 * local server proves the connection test posts to `{baseUrl}/chat/completions`
 * with a bearer key and the chosen model, maps each answer honestly, and treats a
 * dead transport as an inline failure rather than an exception. The server is
 * local and scripted, so no provider and no key is involved, as the pack
 * downloader's lane is.
 *
 * It also proves the OpenCode Go client contract (a named `User-Agent` and a
 * stable `x-opencode-session`, without which Go answers `MissingSessionID`) and
 * that a provider's error body is only ever classified, never rendered — a body
 * that echoes the key cannot put it in a message.
 */
class OpenAiCompatibleLlmClientTest :
    FunSpec({

        /** What the local server saw, so a test can pin the request shape. */
        class Recorded {
            var path: String? = null
            var method: String? = null
            var authorization: String? = null
            var userAgent: String? = null
            var session: String? = null
            var body: String? = null
        }

        fun serve(
            status: Int,
            recorded: Recorded,
            responseBody: String = "",
            path: String = "/v1/chat/completions",
        ): HttpServer {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext(path) { exchange ->
                recorded.path = exchange.requestURI.path
                recorded.method = exchange.requestMethod
                recorded.authorization = exchange.requestHeaders.getFirst("Authorization")
                recorded.userAgent = exchange.requestHeaders.getFirst("User-Agent")
                recorded.session = exchange.requestHeaders.getFirst("x-opencode-session")
                recorded.body = exchange.requestBody.readBytes().decodeToString()
                val bytes = responseBody.encodeToByteArray()
                if (bytes.isEmpty()) {
                    exchange.sendResponseHeaders(status, -1)
                } else {
                    exchange.sendResponseHeaders(status, bytes.size.toLong())
                    exchange.responseBody.use { output -> output.write(bytes) }
                }
                exchange.close()
            }
            server.start()
            return server
        }

        /** Points any request at the local server's `chat/completions`, whatever base URL the config carries. */
        fun routedTo(server: HttpServer): (String) -> HttpURLConnection = {
            URI("http://127.0.0.1:${server.address.port}/v1/chat/completions").toURL().openConnection()
                as HttpURLConnection
        }

        /** Points any request at the local server's `models`, whatever base URL the config carries. */
        fun routedToModels(server: HttpServer): (String) -> HttpURLConnection = {
            URI("http://127.0.0.1:${server.address.port}/v1/models").toURL().openConnection()
                as HttpURLConnection
        }

        fun custom(base: String, model: String): LlmProviderConfig =
            LlmProviderConfig(LlmProvider.CUSTOM, model, customBaseUrl = base)

        fun base(server: HttpServer): String = "http://127.0.0.1:${server.address.port}/v1"

        /** The server-sent-event body a provider streams: the payloads, then `[DONE]`. */
        fun sse(vararg payloads: String): String =
            payloads.joinToString("") { payload -> "data: $payload\n\n" } + "data: [DONE]\n\n"

        /** A translation request with the prompt the domain would build. */
        fun translationRequest(config: LlmProviderConfig, key: String = "sk-test"): LlmTranslationRequest =
            LlmTranslationRequest(
                config = config,
                apiKey = key,
                messages = listOf(
                    LlmMessage("system", "Translate into fr"),
                    LlmMessage("user", "Selected phrase: the lantern"),
                ),
            )

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

        test("an OpenCode Go request carries the required session id and a named user agent") {
            val recorded = Recorded()
            val server = serve(200, recorded)
            try {
                val client = OpenAiCompatibleLlmClient(connect = routedTo(server))
                val config = LlmProviderConfig(LlmProvider.OPENCODE_GO, "deepseek-v4.1-flash")

                client.testConnection(config, "sk-test") shouldBe LlmConnectionResult.Connected

                recorded.session.shouldNotBeNull()
                recorded.userAgent shouldBe USER_AGENT
            } finally {
                server.stop(0)
            }
        }

        test("a provider that does not ask for a session never sees one") {
            val recorded = Recorded()
            val server = serve(200, recorded)
            try {
                val client = OpenAiCompatibleLlmClient(connect = routedTo(server))
                client.testConnection(LlmProviderConfig(LlmProvider.OPENAI, "gpt-4o-mini"), "sk-test")

                recorded.session shouldBe null
                recorded.userAgent shouldBe USER_AGENT
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

        test("an OpenCode MissingSessionID error body names the missing session") {
            val body = """{"type":"error","error":{"type":"MissingSessionID","message":"missing it"}}"""
            val server = serve(400, Recorded(), body)
            try {
                OpenAiCompatibleLlmClient().testConnection(custom(base(server), "m"), "sk-test") shouldBe
                    LlmConnectionResult.Failed(LlmConnectionResult.MISSING_SESSION)
            } finally {
                server.stop(0)
            }
        }

        test("an insufficient-quota error body names the billing") {
            val body = """{"error":{"type":"invalid_request_error","code":"insufficient_quota"}}"""
            val server = serve(429, Recorded(), body)
            try {
                OpenAiCompatibleLlmClient().testConnection(custom(base(server), "m"), "sk-test") shouldBe
                    LlmConnectionResult.Failed(LlmConnectionResult.NO_FUNDS)
            } finally {
                server.stop(0)
            }
        }

        test("a body that echoes the key is classified, never rendered") {
            val echoed = "sk-very-secret"
            val body = """{"error":{"type":"WeirdUnknownError","message":"your key $echoed is rejected"}}"""
            val server = serve(400, Recorded(), body)
            try {
                val result = OpenAiCompatibleLlmClient().testConnection(custom(base(server), "m"), echoed)

                (result as LlmConnectionResult.Failed).message shouldBe LlmConnectionResult.BAD_REQUEST
                result.message shouldNotContain echoed
            } finally {
                server.stop(0)
            }
        }

        test("a dead transport is an inline failure, never a thrown exception") {
            val client = OpenAiCompatibleLlmClient(connect = { throw IOException("connection refused") })

            client.testConnection(LlmProviderConfig(LlmProvider.OPENAI, "gpt-4o-mini"), "sk-test") shouldBe
                LlmConnectionResult.Failed(LlmConnectionResult.UNREACHABLE)
        }

        test("an incomplete configuration is refused before any request is sent") {
            var connected = false
            val client = OpenAiCompatibleLlmClient(
                connect = {
                    connected = true
                    throw IOException("must not connect")
                },
            )

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

        test("the header policy sends no Authorization for a keyless provider") {
            val headers = llmRequestHeaders(
                LlmProviderConfig(LlmProvider.OLLAMA, "llama3"),
                apiKey = "",
                sessionId = "s",
            ).toMap()

            headers.shouldNotContainKey("Authorization")
        }

        test("the header policy sends the OpenCode session only to the OpenCode gateways") {
            val go = llmRequestHeaders(LlmProviderConfig(LlmProvider.OPENCODE_GO, "m"), "k", "s").toMap()
            val zen = llmRequestHeaders(LlmProviderConfig(LlmProvider.OPENCODE_ZEN, "m"), "k", "s").toMap()
            val openai = llmRequestHeaders(LlmProviderConfig(LlmProvider.OPENAI, "m"), "k", "s").toMap()

            go.shouldContainKey("x-opencode-session")
            go["x-opencode-session"] shouldBe "s"
            zen.shouldContainKey("x-opencode-session")
            openai.shouldNotContainKey("x-opencode-session")
            openai["User-Agent"] shouldBe USER_AGENT
        }

        // --- Choosing the model from the provider's list (issue #93) ----------

        test("a model list is loaded from GET /models with the bearer key") {
            val recorded = Recorded()
            val body = """{"object":"list","data":[{"id":"gpt-4o-mini"},{"id":"gpt-4o"}]}"""
            val server = serve(200, recorded, body, path = "/v1/models")
            try {
                val result = OpenAiCompatibleLlmClient()
                    .listModels(custom(base(server), "m"), "sk-secret")

                result shouldBe LlmModelsResult.Loaded(listOf("gpt-4o-mini", "gpt-4o"))
                recorded.path shouldBe "/v1/models"
                recorded.method shouldBe "GET"
                recorded.authorization shouldBe "Bearer sk-secret"
            } finally {
                server.stop(0)
            }
        }

        test("the OpenAI list request names the client, like the connection test") {
            val recorded = Recorded()
            val server = serve(200, recorded, """{"data":[{"id":"m"}]}""", path = "/v1/models")
            try {
                OpenAiCompatibleLlmClient(connect = routedToModels(server))
                    .listModels(LlmProviderConfig(LlmProvider.OPENAI, "m"), "sk-test")

                recorded.userAgent shouldBe USER_AGENT
            } finally {
                server.stop(0)
            }
        }

        test("an OpenCode Go list carries the required session and needs no key") {
            val recorded = Recorded()
            val server = serve(200, recorded, """{"data":[{"id":"deepseek-v4.1-flash"}]}""", path = "/v1/models")
            try {
                val client = OpenAiCompatibleLlmClient(connect = routedToModels(server))
                val config = LlmProviderConfig(LlmProvider.OPENCODE_GO, "m")

                client.listModels(config, "") shouldBe LlmModelsResult.Loaded(listOf("deepseek-v4.1-flash"))

                recorded.session.shouldNotBeNull()
                recorded.authorization shouldBe null
            } finally {
                server.stop(0)
            }
        }

        test("a provider that needs a key reports the missing key rather than sending a keyless request") {
            var connected = false
            val client = OpenAiCompatibleLlmClient(
                connect = {
                    connected = true
                    throw IOException("must not connect")
                },
            )

            client.listModels(LlmProviderConfig(LlmProvider.OPENAI, "m"), "") shouldBe
                LlmModelsResult.Failed(LlmModelsResult.MISSING_KEY)
            connected shouldBe false
        }

        test("a 404 model list means the provider does not implement /models") {
            val server = serve(404, Recorded(), path = "/v1/models")
            try {
                OpenAiCompatibleLlmClient().listModels(custom(base(server), "m"), "sk-test") shouldBe
                    LlmModelsResult.Failed(LlmModelsResult.UNSUPPORTED)
            } finally {
                server.stop(0)
            }
        }

        test("an unparseable model body falls back to manual entry") {
            val server = serve(200, Recorded(), "<html>not json</html>", path = "/v1/models")
            try {
                OpenAiCompatibleLlmClient().listModels(custom(base(server), "m"), "sk-test") shouldBe
                    LlmModelsResult.Failed(LlmModelsResult.UNSUPPORTED)
            } finally {
                server.stop(0)
            }
        }

        test("a provider that lists no models says so rather than showing an empty list") {
            val server = serve(200, Recorded(), """{"object":"list","data":[]}""", path = "/v1/models")
            try {
                OpenAiCompatibleLlmClient().listModels(custom(base(server), "m"), "sk-test") shouldBe
                    LlmModelsResult.Failed(LlmModelsResult.EMPTY)
            } finally {
                server.stop(0)
            }
        }

        test("a rejected key on the model list is classified, and a body echoing it is never rendered") {
            val echoed = "sk-very-secret"
            val body = """{"error":{"type":"invalid_api_key","message":"your key $echoed is rejected"}}"""
            val server = serve(401, Recorded(), body, path = "/v1/models")
            try {
                val result = OpenAiCompatibleLlmClient().listModels(custom(base(server), "m"), echoed)

                result shouldBe LlmModelsResult.Failed(LlmConnectionResult.REJECTED_KEY)
                (result as LlmModelsResult.Failed).message shouldNotContain echoed
            } finally {
                server.stop(0)
            }
        }

        test("a dead transport on the model list is an inline failure, never a thrown exception") {
            val client = OpenAiCompatibleLlmClient(connect = { throw IOException("connection refused") })

            client.listModels(LlmProviderConfig(LlmProvider.OPENAI, "m"), "sk-test") shouldBe
                LlmModelsResult.Failed(LlmConnectionResult.UNREACHABLE)
        }

        test("a configuration with no base URL refuses to list before any request is sent") {
            var connected = false
            val client = OpenAiCompatibleLlmClient(
                connect = {
                    connected = true
                    throw IOException("must not connect")
                },
            )

            client.listModels(LlmProviderConfig(LlmProvider.CUSTOM, "m"), "sk-test") shouldBe
                LlmModelsResult.Failed(LlmModelsResult.INCOMPLETE)
            connected shouldBe false
        }

        test("the models URL tolerates a trailing slash in the base URL") {
            modelsUrl(custom("https://example.test/v1/", "m")) shouldBe "https://example.test/v1/models"
        }

        // --- Streaming phrase translation (issue #89) ------------------------

        test("a translation streams the deltas in order and stops at [DONE]") {
            val recorded = Recorded()
            val body = sse(
                """{"choices":[{"delta":{"content":"Bon"}}]}""",
                """{"choices":[{"delta":{"content":"jour"}}]}""",
            )
            val server = serve(200, recorded, body)
            try {
                val events = OpenAiCompatibleLlmClient()
                    .translate(translationRequest(custom(base(server), "m")))
                    .toList()

                events shouldBe listOf(LlmTranslationEvent.Delta("Bon"), LlmTranslationEvent.Delta("jour"))
                recorded.body shouldContain "\"stream\":true"
                recorded.body shouldContain "\"model\":\"m\""
                recorded.body shouldContain "Selected phrase: the lantern"
            } finally {
                server.stop(0)
            }
        }

        test("a 4xx translation is an inline failure event, not a thrown exception") {
            val server = serve(401, Recorded())
            try {
                val events = OpenAiCompatibleLlmClient()
                    .translate(translationRequest(custom(base(server), "m")))
                    .toList()

                events shouldBe listOf(LlmTranslationEvent.Failed(LlmConnectionResult.REJECTED_KEY))
            } finally {
                server.stop(0)
            }
        }

        test("a mid-stream error envelope is classified, never rendered with its text") {
            val echoed = "sk-very-secret"
            val body = sse("""{"error":{"code":"invalid_api_key","message":"key $echoed rejected"}}""")
            val server = serve(200, Recorded(), body)
            try {
                val events = OpenAiCompatibleLlmClient()
                    .translate(translationRequest(custom(base(server), "m"), echoed))
                    .toList()

                events shouldBe listOf(LlmTranslationEvent.Failed(LlmConnectionResult.REJECTED_KEY))
                (events.single() as LlmTranslationEvent.Failed).message shouldNotContain echoed
            } finally {
                server.stop(0)
            }
        }

        test("a dead translation transport is an inline failure event") {
            val client = OpenAiCompatibleLlmClient(connect = { throw IOException("connection refused") })

            client.translate(translationRequest(custom("http://127.0.0.1:1/v1", "m"))).toList() shouldBe
                listOf(LlmTranslationEvent.Failed(LlmConnectionResult.UNREACHABLE))
        }

        test("an incomplete translation configuration is refused before any request is sent") {
            var connected = false
            val client = OpenAiCompatibleLlmClient(
                connect = {
                    connected = true
                    throw IOException("must not connect")
                },
            )
            val request = LlmTranslationRequest(
                config = LlmProviderConfig(LlmProvider.OPENAI, ""),
                apiKey = "sk-test",
                messages = listOf(LlmMessage("user", "hi")),
            )

            client.translate(request).toList() shouldBe
                listOf(LlmTranslationEvent.Failed(LlmConnectionResult.INCOMPLETE))
            connected shouldBe false
        }

        test("a server-sent event with no text yields no event") {
            streamEvent("""{"choices":[{"delta":{"role":"assistant"}}]}""") shouldBe null
            streamEvent(": keep-alive") shouldBe null
            streamEvent("not json") shouldBe null
        }

        test("a server-sent event carrying text yields a delta") {
            streamEvent("""{"choices":[{"delta":{"content":"bonjour"}}]}""") shouldBe
                LlmTranslationEvent.Delta("bonjour")
        }
    })
