package app.lekto.core

import app.lekto.core.text.WordKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll

/**
 * The default-mastery rule (ADR-0030): a saved entry's level always wins;
 * otherwise a word's level derives from its **Word difficulty** band against the
 * reader's **Reader level**, on the shared CEFR axis; otherwise it is unknown.
 *
 * The derivation is a domain invariant over two finite axes, so a property walks
 * every band combination and the examples pin the arithmetic.
 */
class DefaultMasteryTest :
    FunSpec({

        val bands = Arb.element<CefrLevel?>(null, *CefrLevel.entries.toTypedArray())

        test("every band combination derives the same level as the formula") {
            // mastery = max(0, 4 − max(0, difficulty_band − reader_band)), or unknown
            // when either band is absent — the rule only lights up when both are present.
            checkAll(bands, bands) { difficulty, reader ->
                val expected = if (difficulty == null || reader == null) {
                    MasteryLevel.UNKNOWN
                } else {
                    MasteryLevel.forLevel(4 - (difficulty.band - reader.band).coerceAtLeast(0))
                }
                derivedMastery(difficulty, reader) shouldBe expected
            }
        }

        test("a word at or below the reader's level reads as known") {
            derivedMastery(CefrLevel.A2, CefrLevel.B1) shouldBe MasteryLevel.KNOWN
            derivedMastery(CefrLevel.B1, CefrLevel.B1) shouldBe MasteryLevel.KNOWN
            derivedMastery(CefrLevel.A1, CefrLevel.C2) shouldBe MasteryLevel.KNOWN
        }

        test("each band above the reader's level drops one mastery level") {
            derivedMastery(CefrLevel.B2, CefrLevel.B1) shouldBe MasteryLevel.LEARNED
            derivedMastery(CefrLevel.C1, CefrLevel.B1) shouldBe MasteryLevel.FAMILIAR
            derivedMastery(CefrLevel.C2, CefrLevel.B1) shouldBe MasteryLevel.RECOGNIZED
        }

        test("a word more than four bands above the reader floors at unknown") {
            derivedMastery(CefrLevel.C2, CefrLevel.A1) shouldBe MasteryLevel.forLevel(0)
            derivedMastery(CefrLevel.C2, CefrLevel.A1) shouldBe MasteryLevel.UNKNOWN
        }

        test("no difficulty band or no reader level leaves the word unknown") {
            derivedMastery(null, CefrLevel.C2) shouldBe MasteryLevel.UNKNOWN
            derivedMastery(CefrLevel.A1, null) shouldBe MasteryLevel.UNKNOWN
            derivedMastery(null, null) shouldBe MasteryLevel.UNKNOWN
        }

        test("a saved entry's level wins over the derived default") {
            val saved = WordKey("en", "blorple")
            val unsaved = WordKey("en", "zzzz")
            val lookup = defaultMasteryLookup(
                saved = { key -> if (key == saved) MasteryLevel.RECOGNIZED else null },
                readerLevel = { language -> if (language == "en") CefrLevel.A1 else null },
                difficulty = { key -> if (key == saved) CefrLevel.C2 else null },
            )

            // The word would derive to unknown against A1, but the save wins.
            lookup.levelOf(saved) shouldBe MasteryLevel.RECOGNIZED
            // The unsaved word has a band (C2) above A1: it floors at unknown.
            lookup.levelOf(unsaved) shouldBe MasteryLevel.UNKNOWN
        }

        test("a saved unknown still wins, so a save is an explicit act") {
            val key = WordKey("en", "blorple")
            val lookup = defaultMasteryLookup(
                saved = { savedKey -> if (savedKey == key) MasteryLevel.UNKNOWN else null },
                readerLevel = { CefrLevel.C2 },
                difficulty = { CefrLevel.A1 },
            )

            lookup.levelOf(key) shouldBe MasteryLevel.UNKNOWN
        }

        test("the rule uses the key's own language for the reader level") {
            val lookup = defaultMasteryLookup(
                saved = { null },
                readerLevel = { language -> if (language == "fr") CefrLevel.B1 else null },
                difficulty = { CefrLevel.B1 },
            )

            lookup.levelOf(WordKey("fr", "manger")) shouldBe MasteryLevel.KNOWN
            lookup.levelOf(WordKey("en", "blorple")) shouldBe MasteryLevel.UNKNOWN
        }
    })
