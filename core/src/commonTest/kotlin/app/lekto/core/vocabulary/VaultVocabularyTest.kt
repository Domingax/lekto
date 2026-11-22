package app.lekto.core.vocabulary

import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import app.lekto.core.text.wordKeyOf
import app.lekto.core.vault.DeviceId
import app.lekto.testkit.InMemoryLemmaLookup
import app.lekto.testkit.InMemoryTombstoneStore
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.TestClock
import app.lekto.testkit.deterministicSeams
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

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
            tombstones: InMemoryTombstoneStore = InMemoryTombstoneStore(),
            deviceId: DeviceId = DeviceId("device-a"),
        ): VaultVocabulary = VaultVocabulary(vault, tombstones, deterministicSeams(), deviceId)

        test("a saved word is read back by its key") {
            val vocabulary = newVocabulary()

            vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "lantern", mastery = MasteryLevel.RECOGNIZED))

            vocabulary.entryFor(WordKey("en", "lantern")) shouldBe
                VocabularyEntry(WordKey("en", "lantern"), "lantern", mastery = MasteryLevel.RECOGNIZED)
        }

        test("an unsaved word has no entry") {
            newVocabulary().entryFor(WordKey("en", "lantern")) shouldBe null
        }

        test("saving the same word again replaces its entry") {
            val vault = InMemoryVaultStore()
            val vocabulary = newVocabulary(vault)

            vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "lantern"))
            vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "Lantern", mastery = MasteryLevel.LEARNED))

            vocabulary.all() shouldContainExactly
                listOf(VocabularyEntry(WordKey("en", "lantern"), "Lantern", mastery = MasteryLevel.LEARNED))
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

        test("a deleted word has no entry") {
            val vault = InMemoryVaultStore()
            val vocabulary = newVocabulary(vault)
            vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "lantern"))

            vocabulary.delete(WordKey("en", "lantern"))

            vocabulary.entryFor(WordKey("en", "lantern")) shouldBe null
            vocabulary.all() shouldBe emptyList()
        }

        test("a deleted word is removed from the vault, so a restart does not bring it back") {
            val vault = InMemoryVaultStore()
            val entry = VocabularyEntry(WordKey("en", "lantern"), "lantern")
            newVocabulary(vault).save(entry)

            newVocabulary(vault).delete(entry.key)

            vault.all().none { record -> record.kind == VocabularyRecord.KIND } shouldBe true
            newVocabulary(vault).entryFor(entry.key) shouldBe null
        }

        test("a deleted word leaves a tombstone, so a sync does not resurrect it") {
            val vault = InMemoryVaultStore()
            val tombstones = InMemoryTombstoneStore()
            val vocabulary = newVocabulary(vault, tombstones)
            val entry = VocabularyEntry(WordKey("en", "lantern"), "lantern")
            vocabulary.save(entry)

            vocabulary.delete(entry.key)

            tombstones.get(VocabularyRecord.idOf(entry.key)) shouldNotBe null
        }

        test("deleting an unsaved word leaves no tombstone") {
            val tombstones = InMemoryTombstoneStore()

            newVocabulary(tombstones = tombstones).delete(WordKey("en", "lantern"))

            tombstones.all() shouldBe emptyList()
        }

        test("deleting an unsaved word is not an error") {
            newVocabulary().delete(WordKey("en", "lantern"))
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

        test("a save reports the update time its record was stamped with") {
            // The list's recency is the record's own `updatedAt`, not a field on
            // the entry (ADR-0050), so a save says when it landed.
            val clock = TestClock(Instant.fromEpochMilliseconds(1_000))
            val vocabulary = VaultVocabulary(
                InMemoryVaultStore(),
                InMemoryTombstoneStore(),
                deterministicSeams(clock = clock),
                DeviceId("device-a"),
            )

            val savedAt = vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "lantern"))

            savedAt shouldBe Instant.fromEpochMilliseconds(1_000)
        }

        test("every saved entry carries the update time of its record") {
            val clock = TestClock(Instant.fromEpochMilliseconds(1_000))
            val vocabulary = VaultVocabulary(
                InMemoryVaultStore(),
                InMemoryTombstoneStore(),
                deterministicSeams(clock = clock),
                DeviceId("device-a"),
            )
            val lantern = VocabularyEntry(WordKey("en", "lantern"), "lantern")
            vocabulary.save(lantern)
            clock.advanceBy(2.minutes)
            val harbour = VocabularyEntry(WordKey("en", "harbour"), "harbour")
            vocabulary.save(harbour)

            vocabulary.allWithTimes().associate { saved -> saved.entry.key to saved.updatedAt } shouldBe mapOf(
                lantern.key to Instant.fromEpochMilliseconds(1_000),
                harbour.key to Instant.fromEpochMilliseconds(121_000),
            )
        }

        test("an unreadable record is skipped from the timed list too") {
            val vault = InMemoryVaultStore()
            val vocabulary = newVocabulary(vault)
            vocabulary.save(VocabularyEntry(WordKey("en", "lantern"), "lantern"))
            vault.put(
                VocabularyRecord.of(
                    VocabularyEntry(WordKey("en", "broken"), "broken"),
                    deterministicSeams().clock.now(),
                    DeviceId("device-a"),
                ).copy(body = JsonObject(emptyMap())),
            )

            vocabulary.allWithTimes().map { saved -> saved.entry.surface } shouldContainExactly listOf("lantern")
        }
    })
