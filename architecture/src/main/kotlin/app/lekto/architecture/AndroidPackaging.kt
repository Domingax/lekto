package app.lekto.architecture

/**
 * The Android release packaging contract (issue #262) as a value.
 *
 * The APK is the one artefact a release publishes that no other lane compiles: a
 * signing configuration, a signature check and an asset name live only in the
 * workflow, so a dropped step is invisible until a tag ships a file nobody can
 * install. This reads the lane the way [DesktopPackaging] reads the installer
 * matrix, and says nothing about the rest of a workflow: it finds the body of one
 * job ([job]), what that job waits for ([needs]) and the names a release promises
 * it will carry.
 */
object AndroidPackaging {

    /** The one asset name a release publishes, so the guide and the release body can name it. */
    const val APK_ASSET: String = "Lekto-${'$'}{version}-android.apk"

    /** The artifact the Android job uploads and the release job downloads. */
    const val ARTIFACT: String = "android"

    /** The Gradle task that builds the APK a release ships. */
    const val RELEASE_TASK: String = ":app:assembleRelease"

    /** The body of the top-level job [name] in [workflow], or null when there is none. */
    fun job(workflow: String, name: String): String? {
        // Only what follows `jobs:` holds jobs: the `on:` triggers are also
        // two-space names, and a `push:` trigger is not a job.
        val jobs = JOBS.find(workflow)?.let { anchor -> workflow.substring(anchor.range.last + 1) } ?: return null
        val body = JOB.findAll(jobs).firstOrNull { job -> job.groupValues[1] == name }?.groupValues?.get(2)
        return body?.let { text -> withoutComments(text) }
    }

    /** The jobs the top-level job [name] waits for, empty when it declares none. */
    fun needs(workflow: String, name: String): Set<String> {
        val declared = job(workflow, name)?.let { body -> NEEDS.find(body)?.groupValues?.get(1) }
            ?: return emptySet()
        return declared.removeSurrounding("[", "]")
            .split(',')
            .map { dependency -> dependency.trim() }
            .filter { dependency -> dependency.isNotEmpty() }
            .toSet()
    }

    /**
     * The body with its full-line YAML comments removed: a comment that names a
     * tool (`apksigner`) must not satisfy a rule, the same reason
     * [DesktopPackaging] strips comments from a Gradle script.
     */
    private fun withoutComments(body: String): String = body.lineSequence()
        .filterNot { line -> line.trimStart().startsWith("#") }
        .joinToString("\n")

    /**
     * A job spans from its two-space name to the next two-space name, and a step is
     * indented further, so a nested key such as `needs:` cannot be mistaken for a
     * job. Comments at that indentation are not names.
     */
    private val JOBS = Regex("""(?m)^jobs:[ \t]*$""")
    private val JOB = Regex(
        pattern = """(?m)^ {2}([a-z][a-z0-9-]*):[ \t]*\n(.*?)(?=^ {2}[a-z][a-z0-9-]*:[ \t]*\n|\z)""",
        option = RegexOption.DOT_MATCHES_ALL,
    )
    private val NEEDS = Regex("""(?m)^ {4}needs:\s*(.+)$""")
}
