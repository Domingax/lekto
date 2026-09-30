package app.lekto.architecture

/**
 * The two things the rules read out of Gradle: the modules
 * `settings.gradle.kts` includes, and the `project(":x")` dependencies each
 * module's build file declares, tagged with the source set that declares them.
 *
 * Gradle's own model is not reachable from a JVM test, so the declaration is
 * read directly. Comments and strings are skipped so a `project(…)` in prose or
 * in a string is not a dependency; a reference is test-scoped when its
 * configuration names a test source set — either the enclosing block
 * (`commonTest.dependencies`, `getByName("desktopTest").dependencies`) or the
 * call itself (`testImplementation(…)`).
 */
object GradleBuildFile {

    /** Parses the `include(":core")` lines from `settings.gradle.kts`. */
    fun includedModules(settings: String): List<String> =
        INCLUDE_PATTERN.findAll(stripComments(settings)).map { match -> match.groupValues[1] }.toList()

    /** `:integrations:webdav` → `integrations/webdav`; the root `:` → empty. */
    fun moduleDirectory(path: String): String = path.removePrefix(":").replace(':', '/')

    fun declaredDependencies(buildFile: String): List<DeclaredDependency> {
        val dependencies = mutableListOf<DeclaredDependency>()
        val scopes = ArrayDeque<String>()
        var index = 0
        while (index < buildFile.length) {
            when {
                buildFile.startsWith("//", index) -> index = TextScan.skipLine(buildFile, index)
                buildFile.startsWith("/*", index) -> index = TextScan.skipBlockComment(buildFile, index)
                buildFile[index] == '"' -> index = TextScan.skipString(buildFile, index)
                buildFile[index] == '{' -> index = openScope(buildFile, index, scopes)
                buildFile[index] == '}' -> index = closeScope(index, scopes)
                buildFile.startsWith(PROJECT, index) -> index = readProject(buildFile, index, scopes, dependencies)
                else -> index++
            }
        }
        return dependencies
    }

    private fun openScope(text: String, index: Int, scopes: ArrayDeque<String>): Int {
        scopes.addLast(TextScan.blockHeader(text, index))
        return index + 1
    }

    private fun closeScope(index: Int, scopes: ArrayDeque<String>): Int {
        scopes.removeLastOrNull()
        return index + 1
    }

    private fun readProject(
        text: String,
        index: Int,
        scopes: ArrayDeque<String>,
        dependencies: MutableList<DeclaredDependency>,
    ): Int {
        val open = text.indexOf('"', index + PROJECT.length)
        val close = if (open < 0) -1 else text.indexOf('"', open + 1)
        if (open < 0 || close < 0) return index + PROJECT.length
        val inTestScope = scopes.any { scope -> scope.namesATestSourceSet() }
        val testScoped = inTestScope || TextScan.callName(text, index).namesATestSourceSet()
        dependencies += DeclaredDependency(text.substring(open + 1, close), testScoped)
        return close + 1
    }

    /** Copies [text] with comments removed and string literals kept. */
    private fun stripComments(text: String): String {
        val output = StringBuilder()
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("//", index) -> index = TextScan.skipLine(text, index)

                text.startsWith("/*", index) -> index = TextScan.skipBlockComment(text, index)

                text[index] == '"' -> {
                    val end = TextScan.skipString(text, index)
                    output.append(text, index, end)
                    index = end
                }

                else -> output.append(text[index++])
            }
        }
        return output.toString()
    }

    private fun String.namesATestSourceSet(): Boolean = contains("test", ignoreCase = true)

    private const val PROJECT = "project("
    private val INCLUDE_PATTERN = Regex("""include\("(:[^"]+)"\)""")
}
