package app.lekto

import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.LlmProviderConfig
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop wiring of the LLM provider (issue #88; ADR-0023): the non-secret
 * configuration round-trips to a real app-private directory, and the desktop
 * offers every preset, including the local Ollama it alone can reach (ADR-0022).
 * The Android wiring is excluded from coverage as platform glue, like the rest of
 * `androidMain`.
 */
class DesktopLlmTest {

    @Test
    fun theProviderConfigurationRoundTripsToDisk() {
        val root = Files.createTempDirectory("lekto-desktop-llm").toFile()
        try {
            val config = LlmProviderConfig(LlmProvider.ANTHROPIC, "claude-3")

            desktopLlm(root).settings.save(config)

            assertEquals(config, desktopLlm(root).settings.load())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun theDesktopOffersEveryPresetIncludingItsLocalOllama() {
        assertTrue(desktopLlm().providers.contains(LlmProvider.OLLAMA))
        assertEquals(LlmProvider.entries, desktopLlm().providers)
    }
}
