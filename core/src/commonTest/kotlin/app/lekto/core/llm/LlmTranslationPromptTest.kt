package app.lekto.core.llm

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * The phrase-translation prompt (issue #89; ADR-0022): the domain builds the
 * messages — the adapter only transports them — and the context that leaves the
 * device is exactly what the panel can disclose. It is a pure transform, so it
 * is proved without a socket.
 */
class LlmTranslationPromptTest :
    FunSpec({

        test("the messages carry the selection, its sentence and the target language") {
            val prompt = translationPrompt("the lantern", "On the quiet evening the lantern glows.", "fr")

            prompt.messages.size shouldBe 2
            prompt.messages[0].role shouldBe "system"
            prompt.messages[0].content shouldContain "fr"
            prompt.messages[1].role shouldBe "user"
            prompt.messages[1].content shouldContain "the lantern"
            prompt.messages[1].content shouldContain "On the quiet evening the lantern glows."
        }

        test("the disclosed context is exactly the user message sent") {
            val prompt = translationPrompt("the lantern", "On the quiet evening the lantern glows.", "fr")

            prompt.context shouldBe prompt.messages[1].content
            prompt.context shouldContain "Selected phrase: the lantern"
            prompt.context shouldContain "Containing sentence: On the quiet evening the lantern glows."
        }

        test("a blank or absent sentence falls back to the selection alone") {
            translationPrompt("the lantern", null, "fr").context shouldBe "Selected phrase: the lantern"
            translationPrompt("the lantern", "   ", "fr").context shouldBe "Selected phrase: the lantern"
        }

        test("the context is capped so a long paragraph cannot send the whole chapter") {
            val long = "before ".repeat(200)

            val prompt = translationPrompt("the lantern", long, "fr")

            prompt.context.length shouldBe MAX_TRANSLATION_CONTEXT
        }

        test("the cap never leaves a lone surrogate") {
            val emoji = "\uD83D\uDE00"
            val context = translationContext("selection", emoji.repeat(MAX_TRANSLATION_CONTEXT))

            (context.length <= MAX_TRANSLATION_CONTEXT) shouldBe true
            context.last().isHighSurrogate() shouldBe false
        }
    })
