package app.lekto.architecture

/**
 * Reads one GitHub Actions workflow's top-level jobs as text, without a YAML
 * dependency: a job is a two-space name under `jobs:`, and everything indented
 * further belongs to it. The lane-specific rules ([AndroidPackaging],
 * [DictionaryDataPipeline]) name the steps they need; this finds the body of one
 * job ([job]) and what that job waits for ([needs]), and says nothing else.
 */
object WorkflowJobs {

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
