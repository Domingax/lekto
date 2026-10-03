package app.lekto.core.book

/**
 * Why an import failed, in terms the library can show the user (issue #15).
 *
 * Import is an occasional action over files Lekto did not author, so failure is
 * expected: an unsupported format, an empty or corrupt file, a vault write that
 * did not land. The message is user-facing; the cause is kept for diagnosis.
 */
class ImportException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
