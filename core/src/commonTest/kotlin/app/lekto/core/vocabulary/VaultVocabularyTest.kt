package app.lekto.core.vocabulary

import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import app.lekto.core.text.wordKeyOf
import app.lekto.core.vault.DeviceId
import app.lekto.testkit.InMemoryLemmaLookup
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.deterministicSeams
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject

/**
 * The vocabulary domain behaviour (issue #22): a save is keyed by the word's
 * identity, so saving again or saving an inflected form updates one entry rather
 * than adding another, and the entry survives a restart because it is a vault
 * record.
 */
class VaultVocabularyTest :
    FunSpec({

        fun newVocabulary(
            vault: InMemoryVaultStore = InMemoryVaultStore(),
            deviceId: DeviceId = DeviceId("device-a"),
        ): VaultVocabulary = VaultVocabulary(vault, deterministicSeams(), deviceId)

        test("a saved word is read back by its key") {
            val vocabulary = newVocabulary()

            vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "lantern", mastery = MasteryLevel.FAMILIAR))

            vocabulary.entryFor(WordKey("en", "lantern")) shouldBe
                VocabularyEntry(WordKey("en", "lantern"), "lantern", mastery = MasteryLevel.FAMILIAR)
        }

        test("an unsaved word has no entry") {
            newVocabulary().entryFor(WordKey("en", "lantern")) shouldBe null
        }

        test("saving the same word again replaces its entry") {
            val vault = InMemoryVaultStore()
            val vocabulary = newVocabulary(vault)

            vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "lantern"))
            vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "Lantern", mastery = MasteryLevel.MASTERED))

            vocabulary.all() shouldContainExactly
                listOf(VocabularyEntry(WordKey("en", "lantern"), "Lantern", mastery = MasteryLevel.MASTERED))
            vault.all().count { it.kind == VocabularyRecord.KIND } shouldBe 1
        }

        test("an inflected form does not create a second entry for the same lemma") {
            val lemmas = InMemoryLemmaLookup(mapOf("mangeais" to "manger", "mange" to "manger"))
            val vault = InMemoryVaultStore()
            val vocabulary = newVocabulary(vault)

            val first = VocabularyEntry(wordKeyOf("mangeais", "fr", lemmas), "mangeais")
            val second = VocabularyEntry(wordKeyOf("mange", "fr", lemmas), "mange")
            vocabulary.save(first)
            vocabulary.save(second)

            first.key shouldBe second.key
            vocabulary.all().size shouldBe 1
            vocabulary.entryFor(second.key)?.surface shouldBe "mange"
        }

        test("a saved entry survives a restart over the same vault") {
            val vault = InMemoryVaultStore()
            val entry = VocabularyEntry(WordKey("en", "lantern"), "lantern", contextSentence = "The lantern burned.")
            newVocabulary(vault).save(entry)

            newVocabulary(vault).entryFor(entry.key) shouldBe entry
        }

        test("all lists every saved entry") {
            val vocabulary = newVocabulary()
            val entries = listOf(
                VocabularyEntry(WordKey("en", "lantern"), "lantern"),
                VocabularyEntry(WordKey("fr", "manger"), "mangeais"),
            )

            entries.forEach(vocabulary::save)

            vocabulary.all() shouldContainExactly entries
        }

        test("an unreadable vocabulary record is skipped rather than failing the list") {
            val vault = InMemoryVaultStore()
            val vocabulary = newVocabulary(vault)
            vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "lantern"))
            val broken = VocabularyRecord.of(
                VocabularyEntry(WordKey("en", "broken"), "broken"),
                deterministicSeams().clock.now(),
                DeviceId("device-a"),
            ).copy(body = JsonObject(emptyMap()))
            vault.put(broken)

            vocabulary.all().map { it.surface } shouldContainExactly listOf("lantern")
        }
    })
