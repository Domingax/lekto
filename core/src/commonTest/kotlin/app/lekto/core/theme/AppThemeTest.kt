package app.lekto.core.theme

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The **App theme**'s own rules (ADR-0036): three papers the reader may choose,
 * plus **System**, which resolves to Light or Dark from the platform and never to
 * Sepia, because no operating system has a sepia state. A chosen paper is itself
 * on every platform.
 */
class AppThemeTest :
    FunSpec({

        test("System on a dark platform resolves to Dark") {
            AppTheme.SYSTEM.resolve(systemInDark = true) shouldBe AppTheme.DARK
        }

        test("System on a light platform resolves to Light") {
            AppTheme.SYSTEM.resolve(systemInDark = false) shouldBe AppTheme.LIGHT
        }

        test("System never resolves to Sepia, whichever way the platform reports") {
            listOf(true, false).forEach { dark ->
                AppTheme.SYSTEM.resolve(systemInDark = dark) shouldNotBe AppTheme.SEPIA
            }
        }

        test("a chosen paper is what the reader gets, whatever the platform reports") {
            listOf(AppTheme.LIGHT, AppTheme.DARK, AppTheme.SEPIA).forEach { chosen ->
                chosen.resolve(systemInDark = true) shouldBe chosen
                chosen.resolve(systemInDark = false) shouldBe chosen
            }
        }

        test("a fresh install follows the platform") {
            AppTheme.DEFAULT shouldBe AppTheme.SYSTEM
        }
    })
