package app.lekto.architecture

/**
 * The desktop packaging contract (issue #29) as a value.
 *
 * jpackage builds an installer only on the operating system that owns it, so two
 * artefacts that nothing compiles against must agree: the formats the application
 * declares ([formats]) and the platform-to-installer pairs the release lane builds
 * them on ([packages]). A format dropped from either half is invisible until a
 * release finds one platform missing, which is why the guard reads both files.
 *
 * The reader is deliberately shallow: it matches the declarations it owns and
 * says nothing about the rest of a Gradle script or a workflow, so it cannot
 * become a second, inexact Gradle parser. Comments are skipped first, so a
 * commented-out format or module is not a declaration ([withoutComments]).
 */
object DesktopPackaging {

    /** Every installer a release promises, as the extension users download. */
    val REQUIRED_FORMATS: Set<String> = setOf("dmg", "msi", "exe")

    /**
     * What each platform's job produces. jpackage does not cross-compile, so a
     * `.dmg` only exists if a macOS runner produces it; both macOS architectures
     * are separate entries because the application image is architecture-specific.
     * Linux has no jpackage format and produces the portable AppImage instead.
     */
    val REQUIRED_PACKAGES: Set<PackageJob> =
        setOf(
            PackageJob(runner = "ubuntu-latest", formats = setOf("appimage")),
            PackageJob(runner = "windows-latest", formats = setOf("msi", "exe")),
            PackageJob(runner = "macos-15", formats = setOf("dmg")),
            PackageJob(runner = "macos-15-intel", formats = setOf("dmg")),
        )

    /** The JDK module that lets a Windows screen reader reach Compose (ADR-0007). */
    const val ACCESSIBILITY_MODULE: String = "jdk.accessibility"

    /**
     * The format names inside every `targetFormats(...)` call, lower-cased to the
     * extension they produce, e.g. `dmg`, `msi`, `deb`.
     */
    fun formats(buildScript: String): Set<String> = TARGET_FORMATS.findAll(withoutComments(buildScript))
        .flatMap { call -> call.groupValues[1].split(',') }
        .map { name -> name.trim().substringAfterLast('.').lowercase() }
        .filter { name -> name.isNotEmpty() }
        .toSet()

    /** The runner and the installer formats of each packaging job in [workflow]. */
    fun packages(workflow: String): Set<PackageJob> = PACKAGE_JOB.findAll(workflow)
        .map { job ->
            PackageJob(
                runner = job.groupValues[1],
                formats = job.groupValues[2].lowercase().split(' ', '\t').filter { it.isNotBlank() }.toSet(),
            )
        }
        .toSet()

    /** Whether the distribution declares [ACCESSIBILITY_MODULE] inside its `modules(...)` call. */
    fun shipsAccessibility(buildScript: String): Boolean = MODULES.findAll(withoutComments(buildScript))
        .any { call -> ACCESSIBILITY_MODULE in call.groupValues[1] }

    /** The application version declared as `lekto.version` in `gradle.properties`. */
    fun version(properties: String): String? = VERSION.find(properties)?.groupValues?.get(1)

    /**
     * The text with its Kotlin comments removed, so a commented-out call cannot
     * satisfy a rule. Strings are kept, because the values this guard reads
     * (`"jdk.accessibility"`, a runner label) are strings.
     */
    private fun withoutComments(text: String): String {
        val code = StringBuilder(text.length)
        var cursor = 0
        while (cursor < text.length) {
            when {
                text.startsWith("//", cursor) -> cursor = TextScan.skipLine(text, cursor)

                text.startsWith("/*", cursor) -> cursor = TextScan.skipBlockComment(text, cursor)

                text[cursor] == '"' -> {
                    val end = TextScan.skipString(text, cursor)
                    code.append(text, cursor, end)
                    cursor = end
                }

                else -> code.append(text[cursor++])
            }
        }
        return code.toString()
    }

    /** One platform's packaging job: the runner and the installer formats it produces. */
    data class PackageJob(val runner: String, val formats: Set<String>)

    private val TARGET_FORMATS = Regex("""targetFormats\s*\(([^)]*)\)""")
    private val PACKAGE_JOB = Regex("""runner:\s*(\S+)\s*\n\s*formats:\s*(.+)""")
    private val MODULES = Regex("""modules\s*\(([^)]*)\)""")
    private val VERSION = Regex("""(?m)^lekto\.version\s*=\s*(\d+\.\d+\.\d+)\s*$""")
}
