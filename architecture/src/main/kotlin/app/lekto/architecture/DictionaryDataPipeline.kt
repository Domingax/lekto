package app.lekto.architecture

/**
 * The dictionary-data publishing contract (issue #306, ADR-0046, ADR-0047) as a
 * value: the complete build is a reusable workflow, the data repository holds
 * only the triggers, and the movable manifest is built from the run's own files
 * and published after every asset it names.
 *
 * The rules live here rather than only in the test so a synthetic workflow can be
 * proved to fail them (the same shape [AndroidPackaging] uses). A parser
 * regression or a reordered publication fails `./gradlew check`, not the monthly
 * run.
 */
object DictionaryDataPipeline {

    /** The application's reusable workflow, never triggered on its own. */
    const val REUSABLE: String = ".github/workflows/dictionary-data.yml"

    /** The caller installed in the data repository, versioned here for review. */
    const val CALLER: String = "tools/dictionaries/workflows/lekto-data.yml"

    /** The application's retired single-release lane. */
    const val RETIRED: String = ".github/workflows/dictionary-pack.yml"

    /** The four build layers ADR-0046 names. */
    val LAYERS: Set<String> = setOf("extract", "pair", "difficulty", "assemble")

    /** The preparation jobs the layers depend on: the matrices and the frequency lists. */
    val PREPARATION: Set<String> = setOf("prepare", "frequencies")

    /**
     * True when the assembly job publishes the manifest only after the bytes it
     * names: the release is created as a draft, the manifest is attached last, and
     * only then does `gh release edit` make it latest.
     */
    fun publishesManifestLast(workflow: String): Boolean {
        val body = WorkflowJobs.job(workflow, "assemble")
        val create = body?.indexOf("gh release create") ?: -1
        val upload = body?.indexOf("gh release upload") ?: -1
        val publish = body?.indexOf("gh release edit") ?: -1
        if (body == null || !inOrder(create, upload, publish)) return false
        return "--draft" in body.substring(create, upload) &&
            namesManifest(body.substring(upload, publish)) &&
            "--draft=false" in body.substring(publish)
    }

    private fun inOrder(create: Int, upload: Int, publish: Int): Boolean =
        create >= 0 && upload > create && publish > upload

    private fun namesManifest(betweenUploadAndPublish: String): Boolean =
        "manifest.json" in betweenUploadAndPublish || "RELEASE_MANIFEST" in betweenUploadAndPublish

    /**
     * True when the manifest is assembled from the artefacts the run produced and
     * not from the configured roster: a failed pair must not be advertised.
     */
    fun buildsFromProducedFiles(workflow: String): Boolean {
        val body = WorkflowJobs.job(workflow, "assemble") ?: return false
        return "--assemble-release" in body && "languages.json" !in body
    }

    /** True when the reusable workflow can only run because the data repository called it. */
    fun isReusableOnly(workflow: String): Boolean = TRIGGERS.none { trigger -> trigger in workflow }

    private val TRIGGERS = listOf("\n  push:", "\n  pull_request:", "\n  schedule:", "\n  workflow_dispatch:")
}
