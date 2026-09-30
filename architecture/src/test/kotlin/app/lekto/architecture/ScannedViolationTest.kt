package app.lekto.architecture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * The rules are proven against hand-built values in [ArchitectureViolationTest];
 * this proves the other half — that a violation written to disk flows through
 * [RepositoryScanner] into the rules. Without it, a scanner regression that hid
 * a real violation would still leave the suite green (ticket #9).
 */
class ScannedViolationTest :
    FunSpec({

        test("a deliberate violation on disk is caught through the scanner") {
            val root = createTempDirectory("lekto-architecture").toFile()
            try {
                root.resolve("settings.gradle.kts").writeText("include(\":core\")\n")
                File(root, "core").mkdirs()
                File(root, "core/build.gradle.kts").writeText(
                    "dependencies {\n    implementation(project(\":app\"))\n}\n",
                )
                File(root, "core/src/commonMain/kotlin/app/lekto/core/Lekto.kt").apply {
                    parentFile.mkdirs()
                    writeText("package app.lekto.core\n\nimport app.lekto.App\n")
                }

                val rules = LektoArchitecture.check(RepositoryScanner.scan(root)).map { violation -> violation.rule }

                rules shouldContain Rules.MODULE_DEPENDENCY
                rules shouldContain Rules.IMPORT_PURITY
            } finally {
                root.deleteRecursively()
            }
        }
    })
