package app.lekto.translation

import app.lekto.core.llm.LlmClient
import app.lekto.core.llm.LlmConnectionResult
import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.LlmProviderConfig
import app.lekto.core.llm.LlmTranslationEvent
import app.lekto.core.llm.LlmTranslationRequest
import app.lekto.settings.ProviderController
import app.lekto.testkit.FakeLlmClient
import app.lekto.testkit.InMemoryLlmSettingsStore
import app.lekto.testkit.InMemorySecretStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The phrase-translation state holder (issue #89; ADR-0022): reading the active
 * provider and its key from the **Secret store**, streaming the provider's deltas
 * into the panel, disclosing the context that leaves the device, reporting a
 * failure inline, and cancelling the stream when the panel closes. The dispatcher
 * is the test dispatcher, so the asynchronous test is driven by virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One state holder, one test per behaviour; splitting the class hides it.
class PhraseTranslationControllerTest {

    private val connected = LlmProviderConfig(LlmProvider.ANTHROPIC, "claude-3")

    @Test
    fun `it streams the connected provider's deltas into the panel`() = runTest {
        val client = FakeLlmClient(translation = listOf("La lanterne", " brille"))
        val controller = controller(secrets = keyedSecrets(), client = client)

        controller.translate("the lantern", "The lantern glows.", "fr")
        advanceUntilIdle()

        assertEquals(PhraseTranslationState.Done("La lanterne brille"), controller.state.value.state)
        assertEquals("Anthropic", controller.state.value.provider)
    }

    @Test
    fun `it sends the selection and its containing sentence to the provider`() = runTest {
        val client = FakeLlmClient()
        val controller = controller(secrets = keyedSecrets(), client = client)

        controller.translate("the lantern", "The lantern glows.", "fr")
        advanceUntilIdle()

        val request = client.translated.single()
        assertEquals(connected, request.config)
        assertEquals("sk-live-123", request.apiKey)
        val user = request.messages.single { message -> message.role == "user" }
        assertTrue(user.content.contains("the lantern"), "the selection is sent: ${user.content}")
        assertTrue(user.content.contains("The lantern glows."), "the containing sentence is sent: ${user.content}")
        assertEquals(user.content, controller.state.value.context)
    }

    @Test
    fun `without a stored key the panel reports no provider`() = runTest {
        val client = FakeLlmClient()
        val controller = controller(client = client)

        controller.translate("the lantern", null, "fr")
        advanceUntilIdle()

        assertEquals(PhraseTranslationState.NoProvider, controller.state.value.state)
        assertTrue(client.translated.isEmpty(), "no request must be sent without a key")
    }

    @Test
    fun `an incomplete configuration reports no provider`() = runTest {
        val client = FakeLlmClient()
        val controller = controller(settings = InMemoryLlmSettingsStore(LlmProviderConfig.DEFAULT), client = client)

        controller.translate("the lantern", null, "fr")
        advanceUntilIdle()

        assertEquals(PhraseTranslationState.NoProvider, controller.state.value.state)
        assertTrue(client.translated.isEmpty())
    }

    @Test
    fun `a keyless local provider needs no stored key`() = runTest {
        val client = FakeLlmClient(translation = listOf("bonjour"))
        val settings = InMemoryLlmSettingsStore(LlmProviderConfig(LlmProvider.OLLAMA, "llama3"))
        val controller = controller(settings = settings, client = client)

        controller.translate("the lantern", null, "fr")
        advanceUntilIdle()

        assertEquals(PhraseTranslationState.Done("bonjour"), controller.state.value.state)
        assertEquals("", client.translated.single().apiKey)
    }

    @Test
    fun `a provider failure is reported inline`() = runTest {
        val client = FakeLlmClient(translationFailure = LlmConnectionResult.REJECTED_KEY)
        val controller = controller(secrets = keyedSecrets(), client = client)

        controller.translate("the lantern", null, "fr")
        advanceUntilIdle()

        assertEquals(PhraseTranslationState.Failed(LlmConnectionResult.REJECTED_KEY), controller.state.value.state)
    }

    @Test
    fun `closing the panel cancels the stream`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var cancelled = false
        val client = object : LlmClient {
            override fun testConnection(config: LlmProviderConfig, apiKey: String): LlmConnectionResult =
                LlmConnectionResult.Connected

            override fun translate(request: LlmTranslationRequest): Flow<LlmTranslationEvent> = flow {
                try {
                    emit(LlmTranslationEvent.Delta("La lanterne"))
                    gate.await()
                    emit(LlmTranslationEvent.Delta(" brille"))
                } finally {
                    cancelled = true
                }
            }
        }
        val controller = controller(secrets = keyedSecrets(), client = client)

        controller.translate("the lantern", null, "fr")
        advanceUntilIdle()
        assertEquals(PhraseTranslationState.Streaming("La lanterne"), controller.state.value.state)

        controller.dismiss()
        advanceUntilIdle()

        assertTrue(cancelled, "closing the panel must cancel the collecting coroutine")
        assertEquals(PhraseTranslationState.NoProvider, controller.state.value.state)
    }

    @Test
    fun `a fresh selection replaces the previous stream`() = runTest {
        val client = FakeLlmClient(translation = listOf("first"))
        val controller = controller(secrets = keyedSecrets(), client = client)

        controller.translate("one", null, "fr")
        client.translation = listOf("second")
        controller.translate("two", null, "fr")
        advanceUntilIdle()

        assertEquals(PhraseTranslationState.Done("second"), controller.state.value.state)
    }

    private fun keyedSecrets(): InMemorySecretStore = InMemorySecretStore().apply {
        put(ProviderController.providerSecretKey(LlmProvider.ANTHROPIC), "sk-live-123")
    }

    private fun TestScope.controller(
        settings: InMemoryLlmSettingsStore = InMemoryLlmSettingsStore(connected),
        secrets: InMemorySecretStore = InMemorySecretStore(),
        client: LlmClient = FakeLlmClient(),
    ): PhraseTranslationController = PhraseTranslationController(
        settings = settings,
        secrets = secrets,
        client = client,
        dispatcher = StandardTestDispatcher(testScheduler),
        scope = this,
    )
}
