package app.lekto.architecture

/**
 * The two things the rules read out of Gradle: the modules
 * `settings.gradle.kts` includes, and the `project(":x")` dependencies each
 * module's build file declares, tagged with the source set that declares them.
 *
 * Gradle's own model is not reachable from a JVM test, so the declaration is
 * read directly. Braces are tracked (ignoring strings and comments) to keep the
 * enclosing block's name; a `project(…)` is test-scoped when a block on the
 * stack — `commonTest.dependencies`, say — names a test source set.
 */
object GradleBuildFile {

    /** Parses the `include(":core")` lines from `settings.gradle.kts`. */
    fun includedModules(settings: String): List<String> =
        INCLUDE_PATTERN.findAll(settings).map { match -> match.groupValues[1] }.toList()

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
        scopes.addLast(TextScan.precedingName(text, index))
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
        val testScoped = scopes.any { scope -> scope.contains("test", ignoreCase = true) }
        dependencies += DeclaredDependency(text.substring(open + 1, close), testScoped)
        return close + 1
    }

    private const val PROJECT = "project("
    private val INCLUDE_PATTERN = Regex("""include\("(:[^"]+)"\)""")
}
