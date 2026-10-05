package app.lekto.architecture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

/**
 * Every rule is exercised against a deliberately broken repository, so the
 * suite can never silently stop enforcing a boundary: if a rule is removed or
 * weakened, the violation it used to catch disappears and this test goes red
 * (ticket #9, "a deliberate violation fails the suite").
 *
 * The fixtures are values, not files, so a violation is expressed without
 * touching the working tree — and without leaving a real violation behind for
 * the next `check`.
 */
class ArchitectureViolationTest :
    FunSpec({

        test("a conforming repository has no violations") {
            val repository = repository(
                sources = listOf(
                    source(
                        "core/src/commonMain/kotlin/app/lekto/core/Lekto.kt",
                        "commonMain",
                        "app.lekto.core",
                    ),
                    source(
                        "core/src/commonTest/kotlin/app/lekto/core/LektoTest.kt",
                        "commonTest",
                        "app.lekto.core",
                        "app.lekto.testkit.TestClock",
                    ),
                    source(
                        "app/src/commonMain/kotlin/app/lekto/App.kt",
                        "commonMain",
                        "app.lekto",
                        "app.lekto.core.Lekto",
                    ),
                ),
            )

            LektoArchitecture.check(repository) shouldBe emptyList()
        }

        test("an undeclared module is a violation") {
            val repository = repository(extraModules = listOf(Module(":mystery", emptyList())))

            rules(repository) shouldContain Rules.MODULE_POLICY
        }

        test("a policy with no module is a violation") {
            val modules = LektoArchitecture.policies
                .filter { policy -> policy.path != Modules.CORE }
                .map { policy -> Module(policy.path, emptyList()) }

            rules(Repository(modules, emptyList())) shouldContain Rules.MODULE_POLICY
        }

        test("the domain may not depend on the application") {
            val repository = repository(dependencies = mapOf(":core" to listOf(dependency(":app"))))

            rules(repository) shouldContain Rules.MODULE_DEPENDENCY
        }

        test("the domain may not depend on an integration") {
            val repository = repository(dependencies = mapOf(":core" to listOf(dependency(":integrations:webdav"))))

            rules(repository) shouldContain Rules.MODULE_DEPENDENCY
        }

        test("nothing may depend on the application") {
            val repository = repository(dependencies = mapOf(":testkit" to listOf(dependency(":app"))))

            rules(repository) shouldContain Rules.MODULE_DEPENDENCY
        }

        test("the testkit is a test-scope dependency only") {
            val production = repository(dependencies = mapOf(":core" to listOf(dependency(":testkit"))))
            val test = repository(dependencies = mapOf(":core" to listOf(dependency(":testkit", testScoped = true))))

            rules(production) shouldContain Rules.MODULE_DEPENDENCY
            rules(test) shouldNotContain Rules.MODULE_DEPENDENCY
        }

        test("the domain may not import the application") {
            val repository = repository(sources = listOf(domainSource("app.lekto.App")))

            rules(repository) shouldContain Rules.IMPORT_PURITY
        }

        test("the domain may not import an integration") {
            val repository = repository(sources = listOf(domainSource("app.lekto.integrations.webdav.WebDav")))

            rules(repository) shouldContain Rules.IMPORT_PURITY
        }

        test("an integration may not import another integration") {
            val repository = repository(
                sources = listOf(
                    source(
                        "integrations/webdav/src/commonMain/kotlin/app/lekto/integrations/webdav/WebDav.kt",
                        "commonMain",
                        "app.lekto.integrations.webdav",
                        "app.lekto.integrations.dropbox.Dropbox",
                    ),
                ),
            )

            rules(repository) shouldContain Rules.IMPORT_PURITY
        }

        test("the domain may not import Android") {
            val repository = repository(sources = listOf(domainSource("androidx.compose.runtime.Composable")))

            rules(repository) shouldContain Rules.IMPORT_PURITY
        }

        test("the domain's shared JVM sources may not import Android") {
            val repository = repository(
                sources = listOf(
                    source(
                        "core/src/jvmSharedMain/kotlin/app/lekto/core/Shared.kt",
                        "jvmSharedMain",
                        "app.lekto.core",
                        "android.content.Context",
                    ),
                ),
            )

            rules(repository) shouldContain Rules.IMPORT_PURITY
        }

        test("the domain's Android source set may name Android") {
            val repository = repository(
                sources = listOf(
                    source(
                        "core/src/androidMain/kotlin/app/lekto/core/AndroidBacked.kt",
                        "androidMain",
                        "app.lekto.core",
                        "android.database.sqlite.SQLiteDatabase",
                    ),
                ),
            )

            rules(repository) shouldNotContain Rules.IMPORT_PURITY
        }

        test("a production source may not import the testkit") {
            val repository = repository(
                sources = listOf(
                    source(
                        "app/src/commonMain/kotlin/app/lekto/App.kt",
                        "commonMain",
                        "app.lekto",
                        "app.lekto.testkit.TestKit",
                    ),
                ),
            )

            rules(repository) shouldContain Rules.TESTKIT_LEAK
        }

        test("a package must match its directory") {
            val repository = repository(
                sources = listOf(
                    source(
                        "core/src/commonMain/kotlin/app/lekto/core/Lekto.kt",
                        "commonMain",
                        "app.lekto.wrong",
                    ),
                ),
            )

            rules(repository) shouldContain Rules.PACKAGE_PLACEMENT
        }

        test("a package must sit under its module's root") {
            val repository = repository(
                sources = listOf(
                    source("core/src/commonMain/kotlin/com/example/Mystery.kt", "commonMain", "com.example"),
                ),
            )

            rules(repository) shouldContain Rules.PACKAGE_OWNERSHIP
        }

        test("a source file must live under src/<sourceSet>/kotlin") {
            val repository = repository(
                sources = listOf(source("core/src/Broken.kt", "", "app.lekto.core")),
            )

            rules(repository) shouldContain Rules.SOURCE_PLACEMENT
        }

        test("a test class is named *Test and lives in a test source set") {
            val namedTestInProduction = repository(
                sources = listOf(
                    source(
                        "core/src/commonMain/kotlin/app/lekto/core/LektoTest.kt",
                        "commonMain",
                        "app.lekto.core",
                    ),
                ),
            )
            val supportInTestSource = repository(
                sources = listOf(
                    source(
                        "core/src/commonTest/kotlin/app/lekto/core/Helpers.kt",
                        "commonTest",
                        "app.lekto.core",
                    ),
                ),
            )

            rules(namedTestInProduction) shouldContain Rules.TEST_PLACEMENT
            rules(supportInTestSource) shouldContain Rules.TEST_PLACEMENT
        }

        test("a desktop semantics test must have an Android twin") {
            val repository = repository(
                sources = listOf(
                    source(
                        "app/src/desktopTest/kotlin/app/lekto/newfeature/NewScreenSemanticsTest.kt",
                        "desktopTest",
                        "app.lekto.newfeature",
                    ),
                ),
            )

            rules(repository) shouldContain Rules.UI_TEST_PARITY
        }

        test("the parity violation names the missing Android twin") {
            val repository = repository(
                sources = listOf(
                    source(
                        "app/src/desktopTest/kotlin/app/lekto/newfeature/NewScreenSemanticsTest.kt",
                        "desktopTest",
                        "app.lekto.newfeature",
                    ),
                ),
            )

            val violation = UiTestParityRules.check(repository, allowlist = emptySet()).single()

            violation.rule shouldBe Rules.UI_TEST_PARITY
            violation.detail shouldBe
                "has no Android twin at app/src/androidUnitTest/kotlin/app/lekto/newfeature/NewScreenSemanticsTest.kt"
        }

        test("a desktop semantics test with a same-named Android twin conforms") {
            val repository = repository(
                sources = listOf(
                    source(
                        "app/src/desktopTest/kotlin/app/lekto/newfeature/NewScreenSemanticsTest.kt",
                        "desktopTest",
                        "app.lekto.newfeature",
                    ),
                    source(
                        "app/src/androidUnitTest/kotlin/app/lekto/newfeature/NewScreenSemanticsTest.kt",
                        "androidUnitTest",
                        "app.lekto.newfeature",
                    ),
                ),
            )

            rules(repository) shouldNotContain Rules.UI_TEST_PARITY
        }

        test("an allowlisted desktop semantics test may miss its Android twin") {
            val path = "app/src/desktopTest/kotlin/app/lekto/newfeature/NewScreenSemanticsTest.kt"
            val repository = repository(sources = listOf(source(path, "desktopTest", "app.lekto.newfeature")))

            UiTestParityRules.check(repository, allowlist = setOf(path)) shouldBe emptyList()
        }

        test("only a *SemanticsTest needs an Android twin") {
            val repository = repository(
                sources = listOf(
                    source(
                        "app/src/desktopTest/kotlin/app/lekto/library/LibraryControllerTest.kt",
                        "desktopTest",
                        "app.lekto.library",
                    ),
                ),
            )

            rules(repository) shouldNotContain Rules.UI_TEST_PARITY
        }

        test("derives the Android twin path, rewriting only the source-set segment") {
            UiTestParityRules.twinPath(
                "app/src/desktopTest/kotlin/app/lekto/reader/ReaderScreenSemanticsTest.kt",
            ) shouldBe "app/src/androidUnitTest/kotlin/app/lekto/reader/ReaderScreenSemanticsTest.kt"
            UiTestParityRules.twinPath(
                "app/src/desktopTest/kotlin/app/lekto/desktopTest/NestedScreenSemanticsTest.kt",
            ) shouldBe "app/src/androidUnitTest/kotlin/app/lekto/desktopTest/NestedScreenSemanticsTest.kt"
        }
    })

private fun repository(
    dependencies: Map<String, List<DeclaredDependency>> = emptyMap(),
    sources: List<KotlinSource> = emptyList(),
    extraModules: List<Module> = emptyList(),
): Repository = Repository(
    modules = LektoArchitecture.policies.map { policy -> Module(policy.path, dependencies[policy.path].orEmpty()) } +
        extraModules,
    sources = sources,
)

private fun dependency(path: String, testScoped: Boolean = false): DeclaredDependency =
    DeclaredDependency(path, testScoped)

private fun domainSource(vararg imports: String): KotlinSource =
    source("core/src/commonMain/kotlin/app/lekto/core/Lekto.kt", "commonMain", "app.lekto.core", *imports)

private fun source(path: String, sourceSet: String, packageName: String, vararg imports: String): KotlinSource =
    KotlinSource(path, moduleOf(path), sourceSet, packageName, imports.toList())

/** The module a synthetic source path belongs to, for building a violation fixture. */
private fun moduleOf(path: String): String = when {
    path.startsWith("app/") -> ":app"
    path.startsWith("integrations/") -> ":integrations:webdav"
    path.startsWith("tools/") -> ":tools:dictionaries"
    else -> ":core"
}

private fun rules(repository: Repository): List<String> =
    LektoArchitecture.check(repository).map { violation -> violation.rule }
