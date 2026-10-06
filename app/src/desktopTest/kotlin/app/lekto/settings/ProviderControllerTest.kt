package app.lekto.settings

import app.lekto.core.llm.LlmConnectionResult
import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.LlmProviderConfig
import app.lekto.core.secret.SecretResult
import app.lekto.core.secret.SecretStore
import app.lekto.testkit.FakeLlmClient
import app.lekto.testkit.InMemoryLlmSettingsStore
import app.lekto.testkit.InMemorySecretStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The LLM provider's state holder (issue #88; ADR-0022): loading the stored
 * configuration, choosing one provider, saving app-privately while the key goes
 * to the **Secret store**, running a connection test off the UI thread, and
 * reporting an honest inline result. The dispatcher is the test dispatcher, so
 * the asynchronous test is driven by virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One state holder, one test per behaviour; splitting the class hides it.
class ProviderControllerTest {

    private fun key(provider: LlmProvider): String = ProviderController.providerSecretKey(provider)

    @Test
    fun `it loads the stored configuration and reports it available`() = runTest {
        val settings = InMemoryLlmSettingsStore(LlmProviderConfig(LlmProvider.ANTHROPIC, "claude-3"))
        val controller = controller(settings = settings)

        assertTrue(controller.state.value.available)
        assertEquals(LlmProviderConfig(LlmProvider.ANTHROPIC, "claude-3"), controller.state.value.config)
    }

    @Test
    fun `an unconfigured store starts on the default preset with no model`() = runTest {
        val controller = controller()

        assertEquals(LlmProviderConfig.DEFAULT, controller.state.value.config)
        assertFalse(controller.state.value.hasStoredKey)
    }

    @Test
    fun `it offers only the presets the platform wires`() = runTest {
        val androidPresets = LlmProvider.entries - LlmProvider.OLLAMA
        val controller = controller(providers = androidPresets)

        assertEquals(androidPresets, controller.state.value.providers)
    }

    @Test
    fun `saving persists the configuration and stores the key in the secret store`() = runTest {
        val settings = InMemoryLlmSettingsStore()
        val secrets = InMemorySecretStore()
        val controller = controller(settings = settings, secrets = secrets)

        controller.selectProvider(LlmProvider.OPENAI)
        controller.setModel("gpt-4o-mini")
        controller.save("sk-live-123")

        assertEquals(LlmProviderConfig(LlmProvider.OPENAI, "gpt-4o-mini"), settings.load())
        assertEquals(SecretResult.Found("sk-live-123"), secrets.get(key(LlmProvider.OPENAI)))
        assertTrue(controller.state.value.hasStoredKey)
        assertEquals(ProviderResult.Success(ProviderController.SAVED), controller.state.value.result)
    }

    @Test
    fun `saving with no key typed leaves the stored key in place`() = runTest {
        val secrets = InMemorySecretStore()
        secrets.put(key(LlmProvider.OPENAI), "sk-existing")
        val controller = controller(secrets = secrets)

        controller.save("")

        assertEquals(SecretResult.Found("sk-existing"), secrets.get(key(LlmProvider.OPENAI)))
        assertTrue(controller.state.value.hasStoredKey)
    }

    @Test
    fun `saving reports an unavailable secret store inline`() = runTest {
        val controller = controller(secrets = InMemorySecretStore(available = false))

        controller.save("sk-live-123")

        assertEquals(ProviderResult.Failure(SecretStore.UNAVAILABLE), controller.state.value.result)
        assertFalse(controller.state.value.hasStoredKey)
    }

    @Test
    fun `a test uses the stored key when the field is empty`() = runTest {
        val secrets = InMemorySecretStore()
        secrets.put(key(LlmProvider.OPENAI), "sk-stored")
        val client = FakeLlmClient()
        val controller = controller(secrets = secrets, client = client)

        controller.test("")
        advanceUntilIdle()

        assertEquals("sk-stored", client.tested.single().second)
        assertEquals(ProviderResult.Success(ProviderController.CONNECTED), controller.state.value.result)
    }

    @Test
    fun `a test prefers a freshly typed key over the stored one`() = runTest {
        val secrets = InMemorySecretStore()
        secrets.put(key(LlmProvider.OPENAI), "sk-stored")
        val client = FakeLlmClient()
        val controller = controller(secrets = secrets, client = client)

        controller.test("sk-typed")
        advanceUntilIdle()

        assertEquals("sk-typed", client.tested.single().second)
    }

    @Test
    fun `a failed test is reported inline and clears the testing flag`() = runTest {
        val client = FakeLlmClient(LlmConnectionResult.Failed(LlmConnectionResult.REJECTED_KEY))
        val controller = controller(client = client)

        controller.test("sk-wrong")
        advanceUntilIdle()

        assertEquals(
            ProviderResult.Failure(LlmConnectionResult.REJECTED_KEY),
            controller.state.value.result,
        )
        assertFalse(controller.state.value.testing)
    }

    @Test
    fun `each provider has its own key slot, so switching reports the right presence`() = runTest {
        val secrets = InMemorySecretStore()
        secrets.put(key(LlmProvider.OPENAI), "sk-openai")
        val controller = controller(secrets = secrets)

        assertTrue(controller.state.value.hasStoredKey)

        controller.selectProvider(LlmProvider.ANTHROPIC)

        assertFalse(controller.state.value.hasStoredKey)
        assertEquals(LlmProvider.ANTHROPIC, controller.state.value.config.provider)
    }

    @Test
    fun `switching provider clears the previous provider's model`() = runTest {
        val controller = controller()
        controller.setModel("gpt-4o-mini")

        controller.selectProvider(LlmProvider.ANTHROPIC)

        assertEquals("", controller.state.value.config.model)
    }

    @Test
    fun `a custom base URL is kept for the next time Custom is chosen`() = runTest {
        val controller = controller()
        controller.selectProvider(LlmProvider.CUSTOM)
        controller.setBaseUrl("https://example.test/v1")

        controller.selectProvider(LlmProvider.OPENAI)
        controller.selectProvider(LlmProvider.CUSTOM)

        assertEquals("https://example.test/v1", controller.state.value.config.customBaseUrl)
    }

    @Test
    fun `removing the key deletes it from the secret store`() = runTest {
        val secrets = InMemorySecretStore()
        secrets.put(key(LlmProvider.OPENAI), "sk-openai")
        val controller = controller(secrets = secrets)

        controller.removeKey()

        assertEquals(SecretResult.Absent, secrets.get(key(LlmProvider.OPENAI)))
        assertFalse(controller.state.value.hasStoredKey)
    }

    @Test
    fun `removing a key reports an unavailable secret store rather than claiming success`() = runTest {
        val secrets = InMemorySecretStore(available = false)
        val controller = controller(secrets = secrets)

        controller.removeKey()

        assertEquals(ProviderResult.Failure(SecretStore.UNAVAILABLE), controller.state.value.result)
    }

    @Test
    fun `the controller state never carries the API key`() = runTest {
        val controller = controller()

        controller.save("sk-top-secret")

        assertFalse(controller.state.value.toString().contains("sk-top-secret"))
        assertNotNull(controller.state.value.result)
    }

    private fun TestScope.controller(
        settings: InMemoryLlmSettingsStore = InMemoryLlmSettingsStore(),
        secrets: InMemorySecretStore = InMemorySecretStore(),
        client: FakeLlmClient = FakeLlmClient(),
        providers: List<LlmProvider> = LlmProvider.entries,
    ): ProviderController = ProviderController(
        settings = settings,
        secrets = secrets,
        client = client,
        providers = providers,
        dispatcher = StandardTestDispatcher(testScheduler),
        scope = this,
    )
}
