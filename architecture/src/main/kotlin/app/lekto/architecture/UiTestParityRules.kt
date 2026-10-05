package app.lekto.architecture

/**
 * The two UI-test lanes must stay inseparable (issue #73): a screen proved
 * through semantics in `app/src/desktopTest` must have a same-named twin in
 * `app/src/androidUnitTest`, so an Android-only constraint is not left to the
 * desktop lane. Issue #69's `LazyColumn` key crashed on Android while every JVM
 * test was green; nothing forced the Android lane to re-prove the screen.
 *
 * The convention is path-based: the Android twin of
 * `app/src/desktopTest/kotlin/<path>/<Name>SemanticsTest.kt` lives at
 * `app/src/androidUnitTest/kotlin/<path>/<Name>SemanticsTest.kt` — same name,
 * same package, different lane ([twinPath] derives it). Every desktop
 * `*SemanticsTest.kt` therefore needs an Android twin, unless its path is on
 * [LektoArchitecture.uiTestParityAllowlist], the burn-down list of screens whose
 * twin has not landed yet. A missing twin is a [Rules.UI_TEST_PARITY] violation
 * that names the file the desktop test should have.
 */
object UiTestParityRules {

    fun check(
        repository: Repository,
        allowlist: Set<String> = LektoArchitecture.uiTestParityAllowlist,
    ): List<Violation> {
        val androidTests = repository.sources
            .filter { source -> source.module == Modules.APP && source.sourceSet == ANDROID_LANE }
            .map { source -> source.path }
            .toSet()
        return repository.sources
            .filter { source ->
                source.module == Modules.APP &&
                    source.sourceSet == DESKTOP_LANE &&
                    source.fileName.endsWith(SEMANTICS_SUFFIX)
            }
            .filterNot { source -> source.path in allowlist }
            .mapNotNull { source -> missingTwin(source, androidTests) }
    }

    private fun missingTwin(desktop: KotlinSource, androidTests: Set<String>): Violation? {
        val twin = twinPath(desktop.path)
        if (twin in androidTests) return null
        return Violation(Rules.UI_TEST_PARITY, desktop.path, "has no Android twin at $twin")
    }

    /**
     * `app/src/desktopTest/kotlin/…/X.kt` → `app/src/androidUnitTest/kotlin/…/X.kt`.
     * Only the source-set segment is rewritten, so a package directory that
     * happens to be named `desktopTest` is left alone.
     */
    fun twinPath(desktopPath: String): String = desktopPath.replaceFirst("/$DESKTOP_LANE/", "/$ANDROID_LANE/")

    private const val DESKTOP_LANE = "desktopTest"
    private const val ANDROID_LANE = "androidUnitTest"
    private const val SEMANTICS_SUFFIX = "SemanticsTest.kt"
}
