package app.lekto.core.book

/** The phase of an import [ImportProgress] reports. */
enum class ImportStage {
    /** Reading and parsing the file's bytes. */
    PARSING,

    /** Writing the book into the vault and caching its text. */
    SAVING,
}

/**
 * How far an import has got: its [stage] and an overall [fraction] in `0..1`.
 *
 * Import is an occasional action that must not block the app, so it reports
 * progress as it runs (issue #15). The fraction is monotonic across the stages,
 * which is what lets the library show one determinate bar.
 */
data class ImportProgress(val stage: ImportStage, val fraction: Float) {
    init {
        require(fraction in 0f..1f) { "import fraction must be within 0..1, was $fraction" }
    }
}
