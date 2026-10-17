package app.lekto.text

/**
 * One reader-facing message the app composes outside a composable — a controller's
 * inline result, an import failure, the last sync's outcome (ADR-0034). It names a
 * [TextKey] and, when the text carries values, the arguments to fill in; the
 * reader's **app language** resolves it at render, so a message stored before the
 * language moved still reads in the new one.
 *
 * A message is deliberately language-agnostic data: a controller produces it, the
 * screen renders it through [AppTexts.message], and nothing but the language table
 * holds the words.
 */
sealed interface AppMessage {

    /** A message named by a [TextKey], with the values its text interpolates. */
    data class Text(val key: TextKey, val args: List<String> = emptyList()) : AppMessage

    /**
     * A domain outcome whose text comes from a fixed constant or a free-text reason
     * (an **LLM provider** failure, an unavailable **Secret store**, a transport
     * error). [AppTexts.coreMessage] maps a known constant to its language table
     * entry and passes anything else through unchanged.
     */
    data class Core(val text: String) : AppMessage

    /** The last sync's outcome: what moved, keeping every count so it reads in either language. */
    data class SyncReportText(
        val uploaded: Int,
        val downloaded: Int,
        val deletions: Int,
        val attachments: Int,
        val unresolved: Int,
        val resynced: Boolean,
    ) : AppMessage {

        /** Whether the run moved anything, so the summary names it rather than saying nothing did. */
        val hasMovement: Boolean get() = downloaded > 0 || uploaded > 0 || deletions > 0 || attachments > 0
    }

    companion object {

        /** A [Text] message for [key], its arguments stringified. */
        fun of(key: TextKey, vararg args: Any?): Text = Text(key, args.map { value -> value?.toString().orEmpty() })

        /** A [Text] message naming a domain failure by its constant or free-text reason. */
        fun core(text: String): Core = Core(text)

        /**
         * The message for a failed action: the failure's own reason when it has one
         * — the domain's user-facing text, mapped when it is a known constant — and
         * the app's [fallback] otherwise, so a failure always reads as something the
         * reader can act on.
         */
        fun failure(failure: Throwable, fallback: TextKey): AppMessage =
            failure.message?.takeIf { message -> message.isNotBlank() }?.let(::core) ?: Text(fallback)

        /** The stable, storage-safe form of [message], so a kept outcome survives a restart (issue #121). */
        fun encode(message: AppMessage): String = when (message) {
            is Text -> (listOf("t", message.key.name) + message.args).joinToString(SEPARATOR)

            is Core -> "c$SEPARATOR${message.text}"

            is SyncReportText -> listOf(
                "r",
                message.uploaded.toString(),
                message.downloaded.toString(),
                message.deletions.toString(),
                message.attachments.toString(),
                message.unresolved.toString(),
                message.resynced.toString(),
            ).joinToString(SEPARATOR)
        }

        /**
         * [text] back as a message, or a [Core] fallback when it is not a form this
         * build wrote — an older stored outcome, say — so a restart never fails over
         * a stale document.
         */
        fun decode(text: String): AppMessage {
            val parts = text.split(SEPARATOR)
            return when (parts.firstOrNull()) {
                "t" -> keyOf(parts)?.let { key -> Text(key, parts.drop(2)) } ?: Core(text)
                "r" -> reportOf(parts) ?: Core(text)
                "c" -> Core(text.removePrefix("c$SEPARATOR"))
                else -> Core(text)
            }
        }

        private fun keyOf(parts: List<String>): TextKey? =
            parts.getOrNull(1)?.let { name -> TextKey.entries.firstOrNull { key -> key.name == name } }

        private fun reportOf(parts: List<String>): SyncReportText? {
            val numbers = parts.drop(1)
            if (numbers.size != REPORT_FIELDS) return null
            return runCatching {
                SyncReportText(
                    uploaded = numbers[0].toInt(),
                    downloaded = numbers[1].toInt(),
                    deletions = numbers[2].toInt(),
                    attachments = numbers[3].toInt(),
                    unresolved = numbers[4].toInt(),
                    resynced = numbers[5].toBooleanStrict(),
                )
            }.getOrNull()
        }

        private const val REPORT_FIELDS = 6

        /** A unit separator, so no stored argument — a title, a count — can collide with the framing. */
        private const val SEPARATOR = "\u001f"
    }
}
